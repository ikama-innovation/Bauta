package se.ikama.bauta.batch.tasklet;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.dbcp2.BasicDataSource;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.context.Context;

import javax.sql.DataSource;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Validates the content of the staging database using sql queries and validation rules
 */
@Slf4j()
@ToString
public class SqlValidationTasklet extends ThymeleafReportTasklet implements ReportGenerator, InitializingBean {

    @Autowired
    @Qualifier("stagingDataSource")
    DataSource dataSource;

    /**
     * The query timeout in seconds. Defaults to -1 which means that the default timeout of the datasource will be used.
     */
    @Setter
    private int queryTimeout = -1;

    @Setter @Getter
    private String reportName;

    private List<SqlValidation> validations;


    /**
     * JSON array with validation objects. Will be mapped to a list of {@link SqlValidation}.
     * Example: <pre>
     * [
     * {
     *   "sqlQuery":"select count(*) from person1",
     *   "title":"Count should be > 1",
     *   "result_gt":1,
     * },{
     *   "sqlQuery":"select count(*) from person2",
     *   "title":"Count should be > 100",
     *   "result_gt":100,
     * },{
     *   "sqlQuery":"select countx(*) from person2",
     *   "title":"This will fail because of invalid sql",
     *   "result_gt":1,
     * },{
     *   "sqlQuery":"select count(*) from person2",
     *   "title":"Execution time should be  <  1s",
     *   "maxExecutionTime":1000
     * }]
     * </pre>
     *
     * @See SqlValidation
     */
    @Setter
    private String configuration;


    public SqlValidationTasklet() {
        addReportGenerator(this);
    }

    @Override
    public void afterPropertiesSet() throws Exception {

        if (configuration != null) {
            ObjectMapper mapper = new ObjectMapper();
            SqlValidation vs[] = mapper.readValue(configuration, SqlValidation[].class);
            this.validations = new ArrayList<>(vs.length);
            Collections.addAll(this.validations, vs);
        }
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "stagingTransactionManager")
    public ReportGenerationResult generateReport(File reportFile, StepContribution sc, ChunkContext cc) throws Exception {
        Context context = new Context();
        context.setVariable("stepName", cc.getStepContext().getStepName());
        context.setVariable("jobName", cc.getStepContext().getJobName());
        context.setVariable("jobExecutionId", cc.getStepContext().getStepExecution().getJobExecutionId());
        context.setVariable("jobInstanceId", cc.getStepContext().getStepExecution().getJobExecution().getJobInstance().getInstanceId());

        context.setVariable("name", getReportName());
        int failedValidations = validate();
        context.setVariable("validations", validations);
        log.debug("Writing report ..");
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(reportFile), "utf-8")) {
            templateEngine.process("sql_validation_report", context, writer);
        }
        log.debug("Writing report, done!");

        if (failedValidations > 0) {
            return new ReportGenerationResult(ReportGenerationResult.ReportGenerationResultStatus.Failed, failedValidations + " of " + validations.size() + " validations failed.");
        }
        return new ReportGenerationResult(ReportGenerationResult.ReportGenerationResultStatus.OK, "All validations passed");
    }

    @Override
    public String getReportFilename() {
        return getReportName() + ".html";
    }

    /**
     * Run all validations
     * @return
     */
    private int validate() {
        int failed = 0;
        JdbcTemplate jdbct = new JdbcTemplate(dataSource);
        jdbct.setQueryTimeout(queryTimeout);
        for (SqlValidation validation : validations) {
            String sql = validation.getSqlQuery();
            int active  = ((BasicDataSource)dataSource).getNumActive();
            int idle  = ((BasicDataSource)dataSource).getNumIdle();
            try {
                long t = System.nanoTime();
                jdbct.query(sql, validation);
                // Calculate query time in ms
                t = Math.round((System.nanoTime() - t) / 1000000.0);
                validation.setExecutionTime(t);
                validation.validate();
                if (validation.getStatus() == SqlValidation.ValidationResultStatus.ValidationFailed) {
                    failed++;
                }
             } catch (DataAccessException dae) {
                log.warn("Validation query failed: " + dae.getMostSpecificCause());
                validation.setSqlError(dae.getMessage());
                validation.setStatus(SqlValidation.ValidationResultStatus.SqlFailed);
            }

        }
        return failed;
    }
}
