package se.ikama.bauta.scheduling;

import lombok.Getter;
import lombok.Setter;
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

@Component
public class JobGroupDao implements RowMapper<JobGroup> {

    @Autowired
    DataSource dataSource;

    @Autowired
    JobTriggerDao triggerDao;

    @Setter
    @Getter
    SortedSet<String> jobNames;

    public void testCreationAndSaving(String name, String regex){
        JobGroup group = new JobGroup();
        group.setName(name);
        group.setRegex(regex);

        List<String> matchedJobs = new ArrayList<>();

        for (String job : jobNames){
            boolean match = job.matches(regex);
            if (match){
                matchedJobs.add(job);
            }
        }
        group.setJobNames(matchedJobs);
        //System.out.println("matches: "+matchedJobs.toString());
        //System.out.println("group: "+group.toString());
        saveOrUpdate(group);
        var g = getJobGroups();
        System.out.println("returned group: "+g.toString());
    }

    @Transactional
    public void saveOrUpdate(JobGroup group) {
        System.out.println("group : "+group);
        JdbcTemplate template = new JdbcTemplate(dataSource);
        var dbGroups = getJobGroups();

        if (group.getId() == null) {
            System.out.println("saving group "+group.toString());
            template.update("insert into JOB_GROUP (GROUP_NAME, REGEX, JOB_NAMES) values(?,?,?)",
                    group.getName(), group.getRegex(), group.getJobNames().toString());
        } else {
            System.out.println("updating group "+group.toString());
            template.update("update JOB_GROUP set GROUP_NAME=?,REGEX=?,JOB_NAMES=? where ID=?",
                    group.getName(), group.getRegex(), group.getJobNames().toString(), group.getId());
        }
    }

    public List<JobGroup> getJobGroups(){
        JdbcTemplate template = new JdbcTemplate(dataSource);
        List<JobGroup> out = template.query("select * from JOB_GROUP", this);
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
        dbStrings = dbStrings.replace("[", "");
        dbStrings = dbStrings.replace("]", "");
        String[] toList = dbStrings.split(",");
        List<String> jobList = new ArrayList<>(Arrays.asList(toList));
        jg.setJobNames(jobList);
        return jg;
    }
}
