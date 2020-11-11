package se.ikama.bauta.batch.tasklet.mongodb;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.bson.Document;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.mongodb.core.DocumentCallbackHandler;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Meta;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.util.Assert;
import org.thymeleaf.context.Context;

import com.mongodb.MongoException;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import se.ikama.bauta.batch.tasklet.ReportGenerationResult;
import se.ikama.bauta.batch.tasklet.ReportGenerator;
import se.ikama.bauta.batch.tasklet.ThymeleafReportTasklet;

@Slf4j
@Setter
public class MongoQueryReportTasklet extends ThymeleafReportTasklet implements ReportGenerator, InitializingBean {
	@Autowired
	MongoTemplate mongoTemplate;

	/**
	 * The query timeout in seconds. Defaults to -1 which means that the default
	 * timeout of the datasource will be used.
	 */
	private int queryTimeout = -1;

	/**
	 * A human-friendly name of the generated report.
	 */
	@Getter
	String reportName;
	/**
	 * The MongoDB collection name
	 */
	private String collectionName;
	/**
	 * JSON-formatted queries
	 */
	private List<String> jsonQueries;
	/**
	 * Titles to be presented above each query result. One matching title per jsonQuery
	 */
	private List<String> titles;

	private int cursorBatchSize = 0;

	public MongoQueryReportTasklet() {
		addReportGenerator(this);
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		Assert.notEmpty(jsonQueries, "jsonQueries must not be empty");
		Assert.notEmpty(titles, "titles must not be empty");
		Assert.isTrue(jsonQueries.size() == titles.size(), "titles and jsonQueries must have same size");

	}

	@Override
	public ReportGenerationResult generateReport(File reportFile, StepContribution sc, ChunkContext cc)
			throws Exception {
		Context context = new Context();
		context.setVariable("stepName", cc.getStepContext().getStepName());
		context.setVariable("jobName", cc.getStepContext().getJobName());
		context.setVariable("jobExecutionId", cc.getStepContext().getStepExecution().getJobExecutionId());
		context.setVariable("jobInstanceId",
				cc.getStepContext().getStepExecution().getJobExecution().getJobInstance().getInstanceId());

		context.setVariable("name", getReportName());
		List<QueryResult> result = fetchData();
		log.debug("Result length is " + result.size());
		context.setVariable("queryResults", result);
		try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(reportFile), "utf-8")) {
			templateEngine.process("dynamic_sql_report", context, writer);
		}
		return new ReportGenerationResult(ReportGenerationResult.ReportGenerationResultStatus.OK);
	}

	@Override
	public String getReportFilename() {
		return getReportName() + ".html";
	}

	private List<QueryResult> fetchData() {
		ArrayList<QueryResult> out = new ArrayList<>();
		for (int i = 0; i < jsonQueries.size(); i++) {
			String json = jsonQueries.get(i);
			String title = titles.get(i);
			log.debug("Querying '{}'", json);
			QueryResult qr = new QueryResult();
			qr.setQuery(json);
			qr.setTitle(title);
			try {
				Query q = new BasicQuery(json);
				Meta meta = new Meta();
				meta.setMaxTime(Duration.ofMillis(queryTimeout));
				meta.setCursorBatchSize(cursorBatchSize);
				q.setMeta(meta);
				mongoTemplate.executeQuery(q, collectionName, qr);

			} catch (DataAccessException dae) {
				log.warn("Report query failed: " + dae.getMostSpecificCause());
				qr.setError(dae.getMessage());
			}
			out.add(qr);
		}
		return out;
	}

	public class QueryResult implements DocumentCallbackHandler {

		List<String> columnNames = new ArrayList<>();
		List<Object> columnTypes = new ArrayList<>();
		List<List<Object>> rows = new ArrayList<>();
		String title;
		String query;
		String error;

		public List<String> getColumnNames() {
			return columnNames;
		}

		public List<Object> getColumnTypes() {
			return columnTypes;
		}

		public List<List<Object>> getRows() {
			return rows;
		}

		public String getTitle() {
			return title;
		}

		public void setTitle(String title) {
			this.title = title;
		}

		public String getQuery() {
			return query;
		}

		public void setQuery(String query) {
			this.query = query;
		}

		public String getError() {
			return error;
		}

		public void setError(String error) {
			this.error = error;
		}

		@Override
		public void processDocument(Document document) throws MongoException, DataAccessException {
			log.debug("Processing document {}", document);
			ArrayList<Object> row = new ArrayList<>();

			for (String key : document.keySet()) {
				log.debug("key: {}", key);
				if (!columnNames.contains(key)) {
					columnNames.add(key);
					columnTypes.add("String");
					log.debug("Adding column '{}'", key);
				}
				row.add(document.get(key));
			}
			this.rows.add(row);
		}
	}
}
