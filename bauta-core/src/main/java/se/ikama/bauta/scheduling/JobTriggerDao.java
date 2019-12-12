package se.ikama.bauta.scheduling;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;

@Component
public class JobTriggerDao implements RowMapper<JobTrigger> {
    @Autowired
    DataSource dataSource;

    @Transactional
    public void saveOrUpdate(JobTrigger trigger) {
        JdbcTemplate template = new JdbcTemplate(dataSource);
        if (trigger.getId() == null) {
            template.update("insert into SCHEDULING_TRIGGER (JOB_NAME,TRIGGER_TYPE,TRIGGERING_JOB_NAME,CRON,JOB_PARAMETERS) values(?,?,?,?,?)", trigger.getJobName(), trigger.getTriggerType().toString(), trigger.getTriggeringJobName(), trigger.getCron(), trigger.getJobParameters());
        } else {
            template.update("update SCHEDULING_TRIGGER set JOB_NAME=?,TRIGGER_TYPE=?,TRIGGERING_JOB_NAME=?,CRON=?,JOB_PARAMETERS=? where ID=?", trigger.getJobName(), trigger.getTriggerType().toString(), trigger.getTriggeringJobName(), trigger.getCron(), trigger.getJobParameters(), trigger.getId());
        }
    }

    public void delete(JobTrigger jobTrigger) {
        JdbcTemplate template = new JdbcTemplate(dataSource);
        template.update("delete from SCHEDULING_TRIGGER where ID=?", jobTrigger.getId());
    }

    public List<JobTrigger> loadTriggers() {
        JdbcTemplate template = new JdbcTemplate(dataSource);
        List<JobTrigger> out = template.query("select ID,JOB_NAME, TRIGGER_TYPE,TRIGGERING_JOB_NAME, CRON, JOB_PARAMETERS from SCHEDULING_TRIGGER", this);
        return out;
    }

    @Override
    public JobTrigger mapRow(ResultSet rs, int rowNum) throws SQLException {
        JobTrigger jt = new JobTrigger();
        jt.setId(rs.getLong("ID"));
        jt.setJobName(rs.getString("JOB_NAME"));
        jt.setCron(rs.getString("CRON"));
        jt.setTriggeringJobName(rs.getString("TRIGGERING_JOB_NAME"));
        jt.setTriggerType(JobTrigger.TriggerType.valueOf(rs.getString("TRIGGER_TYPE")));
        jt.setJobParameters(rs.getString("JOB_PARAMETERS"));
        return jt;
    }

    public List<JobTrigger> getJobCompletionTriggersFor(String completedJobName) {
        String args[] = {completedJobName};
        JdbcTemplate template = new JdbcTemplate(dataSource);
        List<JobTrigger> out = template.query("select ID,JOB_NAME, TRIGGER_TYPE,TRIGGERING_JOB_NAME, CRON, JOB_PARAMETERS from SCHEDULING_TRIGGER  where TRIGGERING_JOB_NAME=?", args, this);
        return out;
    }

    public void logSuccess(JobTrigger jt) {
        log(jt, "SUCCESS", null);
    }

    public void logFailure(JobTrigger jt, String errorMsg) {
        log(jt, "FAILED", errorMsg);
    }

    public void log(JobTrigger jt, String status, String errorMsg) {
        JdbcTemplate template = new JdbcTemplate(dataSource);
        Date timestamp = new Date();
        template.update("insert into SCHEDULING_LOG (TSTAMP, STATUS, JOB_NAME, TRIGGER_TYPE, TRIGGERING_JOB_NAME, CRON, ERROR_MSG) values(?,?,?,?,?,?,?)",
                timestamp, status, jt.getJobName(), jt.getTriggerType().toString(), jt.getTriggeringJobName(), jt.getCron(), errorMsg);
    }

    public List<JobTriggerLog> loadLog(int maxResults) {
        JdbcTemplate template = new JdbcTemplate(dataSource);
        RowMapper<JobTriggerLog> rowMapper = (rs, rowNum) -> {
            JobTriggerLog l = new JobTriggerLog();
            l.setTstamp(rs.getTimestamp("TSTAMP"));
            l.setCron(rs.getString("CRON"));
            l.setErrorMsg(rs.getString("ERROR_MSG"));
            l.setJobName(rs.getString("JOB_NAME"));
            l.setStatus(rs.getString("STATUS"));
            l.setTriggeringJobName(rs.getString("TRIGGERING_JOB_NAME"));
            l.setTriggerType(JobTrigger.TriggerType.valueOf(rs.getString("TRIGGER_TYPE")));
            return l;
        };
        List<JobTriggerLog> out = template.query("select TSTAMP, STATUS, JOB_NAME, TRIGGER_TYPE, TRIGGERING_JOB_NAME, CRON, ERROR_MSG from SCHEDULING_LOG  order by TSTAMP desc limit " + maxResults, rowMapper);
        return out;
    }

}
