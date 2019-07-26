package ikama.batchc3.batch.tasklet.oracle;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecutionException;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.StoppableTasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import javax.sql.DataSource;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Offers a way to run PLSQL code asynchronously. Typically by calling a stored procedure.
 * <p>
 * Runs a PLSQL block asynchronously once as a scheduled job using the Oracle DBMS_SCHEDULER.
 * See https://docs.oracle.com/en/database/oracle/oracle-database/19/arpls/DBMS_SCHEDULER.html#GUID-A24DEB5D-2EAF-4C0B-8715-30DC947B3F87
 * <p>
 * The tasklet starts the block asynchronously and then continuously checks the status of the job in the DB.
 * <p>
 * Will essentially run the equivalent of:
 * <code>
 * begin
 * SYS.DBMS_SCHEDULER.CREATE_JOB(
 * job_name=>'MY_JOB123',
 * job_type=>'PLSQL_BLOCK',
 * job_action=>'begin MY_PROCEDURE(''some_argument'',123);end;',
 * enabled=>TRUE);
 * end;
 * /
 * </code>
 */
public class ScheduledJobTasklet implements StoppableTasklet {

    private final Logger log = LoggerFactory.getLogger(ScheduledJobTasklet.class);
    private String action;
    Map<String, Object> outParams = new HashMap<>();

    /* Interval (in ms) between status checks.  */
    private Long statusCheckInterval = 30000L;

    /* Sleep time (in ms) within the execute method */
    private Long sleepTimeMs = 250L;

    /* Time of last status check in the DB */
    private LocalDateTime lastCheck = null;
    private boolean stopping = false;
    private boolean stoppable = true;
    private boolean stopCommandSent = false;
    private String dbmsJobName = null;

    @Autowired
    @Qualifier("stagingDataSource")
    DataSource dataSource;

    /**
     * The PLSQL block to run. Typically calls a stored procedure, e.g.
     * <code>
     * "begin my_procedure(''some_argument'', 123);end;"
     * </code>
     *
     * @param action
     */
    public void setAction(String action) {
        this.action = action;
    }

    /**
     * How often to query Oracle and check the status of the scheduled job.
     * In ms. Valid values are 10000 - 300000
     *
     * @param statusCheckInterval
     */
    public void setStatusCheckInterval(Long statusCheckInterval) {
        if (statusCheckInterval < 10000 || statusCheckInterval > 300000) {
            throw new IllegalArgumentException("Illegal vaue for statusCheckInterval. Valid values are 10000 - 300000");
        }
        this.statusCheckInterval = statusCheckInterval;
    }

    @Override
    public void stop() {
        log.debug("Stopping..");
        if (this.dbmsJobName != null) {
            stop(this.dbmsJobName);
        }

        stopping = true;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        dbmsJobName = (String) chunkContext.getStepContext().getStepExecution().getExecutionContext().get("DBMS_JOBNAME");

        if (dbmsJobName == null) {
            // Create a unique JOB_NAME. 
            // TODO: This could _potentially_ create non-unique names if another step is started very close in time.
            String stepName = chunkContext.getStepContext().getStepName();
            SimpleDateFormat format = new SimpleDateFormat("yyMMddHHmmsss");
            // Max length of job name is 30. Oracle also converts it to uppercase. Must not begin with '_', therefor prepending 'J'
            dbmsJobName = "J" + StringUtils.right(stepName.toUpperCase().replace("-", "_") + format.format(new Date()), 29);
            log.debug("Creating a new scheduled DBMS job. Job name will be '{}'", dbmsJobName);
            chunkContext.getStepContext().getStepExecution().getExecutionContext().put("DBMS_JOBNAME", dbmsJobName);
            schedule(dbmsJobName, contribution);
            outParams.put("JOB_NAME", dbmsJobName);
            chunkContext.getStepContext().getStepExecution().getExecutionContext().put("outParams", outParams);
        } else if (stopCommandSent || lastCheck == null || LocalDateTime.now().minus(statusCheckInterval, ChronoUnit.MILLIS).isAfter(lastCheck)) {
            log.debug("Time to check status");
            String status = checkStatus(dbmsJobName, contribution);
            lastCheck = LocalDateTime.now();
            if ("SUCCEEDED".equals(status)) {
                return RepeatStatus.FINISHED;
            } else if ("STOPPED".equals(status)) {
                log.debug("Job is stopped");
                contribution.setExitStatus(ExitStatus.STOPPED);
                return RepeatStatus.FINISHED;
            } else if (!StringUtils.isEmpty(status)) {
                // Not any of the expected values
                throw new JobExecutionException("Scheduled job " + dbmsJobName + " finished with (unexpected) status '" + status + "'");
            } else {
                // We could just assume that the job is running, but this is a more robust way to make sure the job is running
                checkRunning(dbmsJobName, contribution);
            }
        }
        sleep();
        return RepeatStatus.CONTINUABLE;
    }

    private void schedule(String dbmsJobName, StepContribution contribution) throws SQLException {
        String sql = buildStatement(dbmsJobName);
        log.debug("Executing statement {}", sql);


        // exeute statement
        try (Connection connection = dataSource.getConnection(); Statement stmt = connection.createStatement()) {
            boolean hasResult = stmt.execute(sql);
        }
        contribution.incrementWriteCount(1);
        lastCheck = LocalDateTime.now();
        log.debug("Statement executed successfully");
    }

    /**
     * Make an attempt to stop the scheduled job cleanly by sending a stop
     * request to the DBMS_SCHEDULER.
     *
     * @param dbmsJobName
     */
    private void stop(String dbmsJobName) {
        // Just make one attempt  send stop command
        stopCommandSent = true;
        String sql = buildStopStatement(dbmsJobName);
        log.debug("Executing stop statement {}", sql);
        // exeute statement
        try (Connection connection = dataSource.getConnection(); Statement stmt = connection.createStatement()) {
            boolean hasResult = stmt.execute(sql);
            log.debug("Stop statement executed successfully");
        } catch (Exception e) {
            log.warn("Failed to stop job", e);
        }

    }

    /**
     * Checks if the given job is running.
     *
     * @return
     * @throws SQLException
     */
    private boolean checkRunning(String dbmsJobName, StepContribution contribution) throws SQLException {
        String checkIfRunningSql = "select JOB_NAME, SESSION_ID, SLAVE_PROCESS_ID from USER_SCHEDULER_RUNNING_JOBS where job_name=?";
        try (Connection connection = dataSource.getConnection(); PreparedStatement stmt = connection.prepareStatement(checkIfRunningSql)) {
            log.debug("Check if running with query '{}'", checkIfRunningSql);
            stmt.setString(1, dbmsJobName);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String jobName = rs.getString(1);
                long sessionId = rs.getLong(2);
                long slaveProcessId = rs.getLong(3);
                log.debug("query result: {},{},{}", jobName, sessionId, slaveProcessId);
                outParams.put("sessionId", sessionId);
                outParams.put("slaveProcessId", slaveProcessId);
                contribution.incrementReadCount();
                return true;
            }
        } catch (Exception e) {
            log.warn("Failed to checkIfRunning", e);
        }
        return false;
    }

    private void sleep() {
        try {
            Thread.sleep(sleepTimeMs);
        } catch (InterruptedException ex) {
            log.warn("Unexpected interruptedException", ex);
        }
    }

    /**
     * When a scheduled job has finished (successfully or not), a row is written
     * to the table user_scheduler_job_run_details. This method returns the value in
     * column STATUS if it has any of the values "SUCCEEDED", "STOPPED".
     * Returns null if the job has not finished.
     * Throws JobExecutionException if the status value is "FAILED"
     */
    private String checkStatus(String dbmsJobName, StepContribution contribution) throws JobExecutionException {
        String status = null;
        long oraError = 0;
        String additionalInfo = null;
        // Check if the job has ended
        final String checkSuccessSql = "select JOB_NAME,STATUS,ERROR#,ADDITIONAL_INFO from user_scheduler_job_run_details where job_name=?";

        try (Connection connection = dataSource.getConnection(); PreparedStatement stmt = connection.prepareStatement(checkSuccessSql)) {
            log.debug("Checking status: '{}'", checkSuccessSql);
            stmt.setString(1, dbmsJobName);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String jobName = rs.getString(1);
                status = rs.getString(2);
                oraError = rs.getLong(3);
                additionalInfo = rs.getString(4);
                log.debug("query result: {},{},{},{}", jobName, status, oraError, additionalInfo);
                if ("FAILED".equals(status)) {
                    throw new JobExecutionException("Job " + jobName + " failed: " + additionalInfo);
                }
                // Just assert that we only received one row
                if (rs.next()) {
                    // table should only contain one row, since we assume that we are using a unique job name
                    log.warn("Additional row(s) found in user_scheduler_job_run_details. Expected only one row");
                }
            } else {
                log.debug("No result. Job is not finished.");
            }
        } catch (Exception e) {
            // TODO: What is the best thing to do here? Stop step execution?
            log.warn("Failed to check status", e);
        }
        return status;
    }


    private String buildStatement(String jobName) {
        /* Build a statement like this
        begin
            SYS.DBMS_SCHEDULER.CREATE_JOB(
            job_name=>'DUMMY_JOB123',
            job_type=>'PLSQL_BLOCK',
            job_action=>'begin DUMMY_PROCEDURE(''hello'',30);end;',
            enabled=>TRUE);
        end;
         */

        String lf = System.lineSeparator();
        StringBuilder plsql = new StringBuilder();
        plsql.append("begin ").append(lf);
        plsql.append("SYS.DBMS_SCHEDULER.CREATE_JOB(").append(lf);
        plsql.append("job_name=>'").append(jobName).append("',").append(lf);
        plsql.append("job_type=>'PLSQL_BLOCK',").append(lf);
        plsql.append("job_action=>'").append(action).append("',").append(lf);
        plsql.append("enabled=>TRUE);").append(lf);
        plsql.append("end;").append(lf);
        //plsql.append("/").append(lf);
        return plsql.toString();
    }

    private String buildStopStatement(String jobName) {
        /* Build a statement like this
        BEGIN
            DBMS_SCHEDULER.STOP_JOB ( job_name=>'MY_JOB_123' , force=> true );
        END;
         */
        String lf = System.lineSeparator();
        StringBuilder plsql = new StringBuilder();
        plsql.append("begin ").append(lf);
        plsql.append("SYS.DBMS_SCHEDULER.STOP_JOB(").append(lf);
        plsql.append("job_name=>'").append(jobName).append("',").append(lf);
        plsql.append("force=>TRUE);").append(lf);
        plsql.append("end;").append(lf);
        return plsql.toString();
    }


}
