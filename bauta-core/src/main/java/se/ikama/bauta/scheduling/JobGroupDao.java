package se.ikama.bauta.scheduling;

import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SortedSet;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Component
public class JobGroupDao implements RowMapper<JobGroup> {

    Logger log = LoggerFactory.getLogger(this.getClass().getName());

    @Autowired
    DataSource dataSource;

    @Autowired
    JobTriggerDao triggerDao;

    @Setter
    @Getter
    SortedSet<String> jobNames;


    public JobGroup createJobGroup(String name, String regex) {
        JobGroup jobGroup = new JobGroup();
        jobGroup.setName(name);
        jobGroup.setRegex(regex);

        PatternSyntaxException exc = null;
        try {
            Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            exc = e;
        }
        if (exc != null) {
            log.warn("INVALID REGEXP");
            return null;
        } else {
            List<String> matchedJobs = new ArrayList<>();
            for (String job : jobNames){
                boolean match = job.matches(regex);
                if (match){
                    matchedJobs.add(job);
                }
            }
            jobGroup.setJobNames(matchedJobs);
            return jobGroup;
        }
    }

    @Transactional
    public void save(JobGroup group) {
        JdbcTemplate template = new JdbcTemplate(dataSource);
        var dbGroups = getAllJobGroups();
        ArrayList<String> nameList = new ArrayList<>();
        dbGroups.forEach(g -> {
            nameList.add(g.getName());
        });
        if (group.getId() == null && !nameList.contains(group.getName())) {
            log.info("saving group {}",group.toString());
            template.update("insert into JOB_GROUP (GROUP_NAME, REGEX, JOB_NAMES) values(?,?,?)",
                    group.getName(), group.getRegex(), group.getJobNames().toString());
        }
    }

    @Transactional
    public void updateGroup(JobGroup group){
        JdbcTemplate template = new JdbcTemplate(dataSource);
        log.info("updating group {}",group.toString());
        template.update("update JOB_GROUP set GROUP_NAME=?,REGEX=?,JOB_NAMES=? where ID=?",
                    group.getName(), group.getRegex(), group.getJobNames().toString(), group.getId());
    }

    public List<JobGroup> getAllJobGroups(){
        JdbcTemplate template = new JdbcTemplate(dataSource);
        List<JobGroup> out = template.query("select * from JOB_GROUP", this);
        return out;
    }

    public List<JobGroup> getJobGroup(String groupName){
        JdbcTemplate template = new JdbcTemplate(dataSource);
        List<JobGroup> out = template.query("select * from JOB_GROUP where GROUP_NAME='"+groupName+"'", this);
        return out;
    }

    public void delete(JobGroup group) {
        JdbcTemplate template = new JdbcTemplate(dataSource);
        template.update("delete from JOB_GROUP where ID=?", group.getId());
    }
    public void deleteAll() {
        JdbcTemplate template = new JdbcTemplate(dataSource);
        template.update("delete from JOB_GROUP");
    }

    @Override
    public JobGroup mapRow(ResultSet resultSet, int i) throws SQLException {
        JobGroup jg = new JobGroup();
        jg.setId(resultSet.getLong("ID"));
        jg.setName(resultSet.getString("GROUP_NAME"));
        jg.setRegex(resultSet.getString("REGEX"));

        String dbStrings =  resultSet.getString("JOB_NAMES");
        dbStrings = dbStrings.replace("[", "").replace("]", "");
        String[] toList = dbStrings.split(",");
        List<String> jobList = new ArrayList<>(Arrays.asList(toList));
        jg.setJobNames(jobList);
        return jg;
    }
}
