package ikama.batchc3.core;


import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DateUtils;
import org.apache.commons.lang.time.DurationFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.*;
import org.springframework.batch.core.configuration.xml.SimpleFlowFactoryBean;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.job.flow.*;
import org.springframework.batch.core.job.flow.support.state.StepState;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.NoSuchJobException;
import org.springframework.batch.core.launch.NoSuchJobExecutionException;
import org.springframework.batch.core.launch.NoSuchJobInstanceException;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.tasklet.TaskletStep;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class C3Manager implements StepExecutionListener, JobExecutionListener , ApplicationContextAware {

    Logger log = LoggerFactory.getLogger(this.getClass().getName());
    private C3Config c3Config;

    JobRepository jobRepository;

    JobOperator jobOperator;

    JobExplorer jobExplorer;
    private HashSet<JobEventListener> jobEventListeners = new HashSet<>();
    private ApplicationContext applicationContext;

    public C3Manager(C3Config c3Config, JobOperator jobOperator, JobRepository jobRepository, JobExplorer jobExplorer) {
        this.c3Config = c3Config;
        this.jobRepository = jobRepository;
        this.jobOperator = jobOperator;
        this.jobExplorer = jobExplorer;
    }



    @PostConstruct
    public void init() {
        log.info("Home is {}", c3Config.getProperty(C3ConfigParams.HOME_DIR));
    }



    public Long startJob(String jobName)  {
        if (StringUtils.isNumeric(jobName)) {
            int i = Integer.parseInt(jobName);
            jobName = listJobNames().get(i);
        }
        DateTimeFormatter dtf = DateTimeFormatter.ISO_DATE_TIME;
        String params = "start="+dtf.format(LocalDateTime.now());
        try {
            return jobOperator.start(jobName, params);
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to start job", e);
        }
    }

    public void stopJob(String jobName)  {
        if (StringUtils.isNumeric(jobName)) {
            int i = Integer.parseInt(jobName);
            jobName = listJobNames().get(i);
        }
        try {
            Set<Long> ids = jobOperator.getRunningExecutions(jobName);
            for (Long id : ids) {
                jobOperator.stop(id);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to stop job " + jobName, e);
        }
    }

    public void restartJob(long executionId) throws Exception {
        jobOperator.restart(executionId);
    }
    public void abandonJob(long executionId) throws Exception {
        jobOperator.abandon(executionId);
    }
    public Collection<String> listJobSummaries() throws NoSuchJobException, NoSuchJobInstanceException, NoSuchJobExecutionException {
        ArrayList<String> out = new ArrayList<>();
        Set<String> jobNames = jobOperator.getJobNames();
        for (String jobName:jobNames) {
            StringBuilder sb = new StringBuilder(jobName);
            sb.append(System.lineSeparator());
            List<Long> jobInstances = jobOperator.getJobInstances(jobName, 0, 1);
            if (jobInstances.size() > 0) {
                long latestInstance = jobInstances.get(0);
                sb.append("  ").append("i: ").append(latestInstance).append(System.lineSeparator());
                List<Long> executions = jobOperator.getExecutions(latestInstance);
                for (Long exId:executions) {
                    String s = jobOperator.getSummary(exId);
                    sb.append("    ").append(s);
                }
            }
            out.add(sb.toString());
        }
        return out;
    }
    public List<String> listJobNames() {
        ArrayList<String> out = new ArrayList<>();
        out.addAll(jobOperator.getJobNames());
        out.sort(String::compareTo);
        return out;
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info("beforeJob: {}", jobExecution);
        fireJobEvent(jobExecution);
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        log.info("afterJob: {}", jobExecution);
        fireJobEvent(jobExecution);
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        log.info("beforeJob: {}", stepExecution);
        fireJobEvent(stepExecution.getJobExecution());
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        log.info("afterStep: {}", stepExecution);
        log.info("Job: {}", stepExecution.getJobExecution());

        fireJobEvent(stepExecution.getJobExecution());
        return stepExecution.getExitStatus();
    }

    private void fireJobEvent(JobExecution je) {
        JobInfo jobInfo = extractJobInfo(je);
        for (JobEventListener jel : jobEventListeners) {
            jel.onJobChange(jobInfo);
        }
    }

    private JobInfo extractJobInfo(JobExecution je) {
        JobInfo jobInfo = new JobInfo(je.getJobInstance().getJobName());
        jobInfo.setExecutionStatus(je.getStatus().name());
        jobInfo.setExitStatus(je.getExitStatus().getExitCode());
        jobInfo.setInstanceId(je.getJobInstance().getInstanceId());
        jobInfo.setExecutionId(je.getId());
        jobInfo.setStartTime(je.getStartTime());
        jobInfo.setEndTime(je.getEndTime());
        if (je.getEndTime() != null) {
            long duration = je.getEndTime().getTime() - je.getStartTime().getTime();
            jobInfo.setDuration(duration);
        }

        for (StepExecution se : je.getStepExecutions()) {
            StepInfo si = new StepInfo(se.getStepName());
            si.setExecutionStatus(se.getStatus().name());
            try {
                si.setReportUrls((List<String>) se.getExecutionContext().get("reportUrls"));
            } catch (ClassCastException e) {
                log.warn("reportUrls: {}", se.getExecutionContext().get("reportUrls"));
                log.warn("Class cast error:  'reportUrls'. Job: {}, Step: {}", jobInfo.getName(), se.getStepName());
            }
            jobInfo.add(si);
        }
        return jobInfo;
    }


    public List<JobInfo> jobDetails() throws Exception {
        ArrayList<JobInfo> out = new ArrayList<>();
        Set<String> jobNames = jobOperator.getJobNames();
        for (String jobName:jobNames) {
            JobInfo jobInfo = fetchJobInfo(jobName);
            out.add(jobInfo);
        }
        return out;
    }
    private JobInfo fetchJobInfo(String jobName) throws Exception  {
        JobInfo out = new JobInfo(jobName);
        List<Long> jobInstances = jobOperator.getJobInstances(jobName, 0, 1);
        if (jobInstances.size() > 0) {
            long latestInstance = jobInstances.get(0);
            out.setInstanceId(latestInstance);
            List<Long> executions = jobOperator.getExecutions(latestInstance);
            if (executions.size() > 0) {
                long latestExecutionId = executions.get(0);
                JobExecution jobExecution = jobExplorer.getJobExecution(latestExecutionId);
                out = extractJobInfo(jobExecution);
            }
        }
        return out;
    }

    public void registerJobChangeListener(JobEventListener jobEventListener) {
        this.jobEventListeners.add(jobEventListener);
    }
    public void unregisterJobChangeListener(JobEventListener jobEventListener) {
        this.jobEventListeners.remove(jobEventListener);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
