package se.ikama.bauta.batch.tasklet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.util.Assert;
import org.thymeleaf.context.Context;

import javax.sql.DataSource;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class SqlQueryReportTasklet extends ThymeleafReportTasklet implements ReportGenerator, InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(SqlQueryReportTasklet.class);

    @Autowired
    @Qualifier("stagingDataSource")
    DataSource dataSource;

    /**
     * The query timeout in seconds. Defaults to -1 which means that the default timeout of the datasource will be used.
     */
    private int queryTimeout = -1;


    private List<String> sqlQueries;
    private List<String> titles;

    public SqlQueryReportTasklet() {
        addReportGenerator(this);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Assert.notEmpty(sqlQueries, "sqlQueries must not be empty");
        Assert.notEmpty(titles, "titles must not be empty");
        Assert.isTrue(sqlQueries.size() == titles.size(), "titles and sqlQueries must have same size");

    }

    public void setSqlQueries(List<String> sqlQueries) {
        this.sqlQueries = sqlQueries;
    }

    public void setTitles(List<String> titles) {
        this.titles = titles;
    }

    public int getQueryTimeout() {
        return queryTimeout;
    }

    public void setQueryTimeout(int queryTimeout) {
        this.queryTimeout = queryTimeout;
    }

    @Override
    public void generateReport(File reportFile, StepContribution sc, ChunkContext cc) throws Exception {
        Context context = new Context();
        context.setVariable("stepName", cc.getStepContext().getStepName());
        context.setVariable("jobName", cc.getStepContext().getJobName());
        context.setVariable("jobExecutionId", cc.getStepContext().getStepExecution().getJobExecutionId());
        context.setVariable("jobInstanceId", cc.getStepContext().getStepExecution().getJobExecution().getJobInstance().getInstanceId());

        context.setVariable("name", name);
        List<QueryResult> result = fetchData();
        log.debug("Result length is " + result.size());
        context.setVariable("queryResults", result);
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(reportFile), "ISO-8859-15")) {
            templateEngine.process("dynamic_sql_report", context, writer);
        }
    }

    @Override
    public String getReportFilename() {
        return name + ".html";
    }

    private List<QueryResult> fetchData() {
        ArrayList<QueryResult> out = new ArrayList<>();
        for (int i = 0; i < sqlQueries.size(); i++) {
            String sql = sqlQueries.get(i);
            String title = titles.get(i);
            JdbcTemplate jdbct = new JdbcTemplate(dataSource);
            jdbct.setQueryTimeout(queryTimeout);
            log.debug("Querying '{}'", sql);
            QueryResult qr = new QueryResult();
            qr.setQuery(sql);
            qr.setTitle(title);
            try {
                jdbct.query(sql, qr);
            } catch (DataAccessException dae) {
                log.warn("Report query failed: " + dae.getMostSpecificCause());
                qr.setError(dae.getMessage());
            }
            out.add(qr);
        }
        return out;
    }

    public class QueryResult implements ResultSetExtractor {

        List<String> columnNames;
        List<Object> columnTypes;
        List<List<Object>> rows;
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
        public Integer extractData(ResultSet rs) throws SQLException, DataAccessException {
            log.debug("Extracting data");
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            this.columnNames = new ArrayList<>();
            this.columnTypes = new ArrayList<>();
            log.debug("Column names are {}", columnNames);
            log.debug("Column types are {}", columnTypes);
            for (int i = 0; i < columnCount; i++) {
                columnNames.add(metaData.getColumnName(i + 1));
                columnTypes.add(metaData.getColumnTypeName(i + 1));
            }
            this.rows = new ArrayList<List<Object>>();
            while (rs.next()) {
                log.debug("Processing row");
                ArrayList<Object> row = new ArrayList<>();
                for (int i = 0; i < columnCount; i++) {
                    row.add(rs.getObject(i + 1));
                }
                this.rows.add(row);
            }
            return columnCount;
        }

    }
}
