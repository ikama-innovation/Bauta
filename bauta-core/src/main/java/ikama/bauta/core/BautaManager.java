package ikama.bauta.core;


import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.*;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.NoSuchJobException;
import org.springframework.batch.core.launch.NoSuchJobExecutionException;
import org.springframework.batch.core.launch.NoSuchJobInstanceException;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.env.Environment;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class BautaManager implements StepExecutionListener, JobExecutionListener, ApplicationContextAware {

    Logger log = LoggerFactory.getLogger(this.getClass().getName());
    private BautaConfig bautaConfig;

    JobRepository jobRepository;

    JobOperator jobOperator;

    JobExplorer jobExplorer;
    private HashSet<JobEventListener> jobEventListeners = new HashSet<>();
    private ApplicationContext applicationContext;

    /**
     * Holds last known information about the steps of a job
     */
    private TreeMap<String, JobInstanceInfo> cachedJobInstanceInfos = new TreeMap<>();

    @Value("${bauta.rebuildServerCommand}")
    private String rebuildServerCommand;

    @Autowired
    Environment env;

    public BautaManager(BautaConfig bautaConfig, JobOperator jobOperator, JobRepository jobRepository, JobExplorer jobExplorer) {
        this.bautaConfig = bautaConfig;
        this.jobRepository = jobRepository;
        this.jobOperator = jobOperator;
        this.jobExplorer = jobExplorer;
    }


    @PostConstruct
    public void init() {
        log.info("Home is {}", bautaConfig.getProperty(BautaConfigParams.HOME_DIR));
    }


    public Long startJob(String jobName) {
        if (StringUtils.isNumeric(jobName)) {
            int i = Integer.parseInt(jobName);
            jobName = listJobNames().get(i);
        }
        DateTimeFormatter dtf = DateTimeFormatter.ISO_DATE_TIME;
        String params = "start=" + dtf.format(LocalDateTime.now());
        try {
            return jobOperator.start(jobName, params);
        } catch (Exception e) {
            throw new RuntimeException("Failed to start job", e);
        }
    }

    public void stopJob(String jobName) {
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
        for (String jobName : jobNames) {
            StringBuilder sb = new StringBuilder(jobName);
            sb.append(System.lineSeparator());
            List<Long> jobInstances = jobOperator.getJobInstances(jobName, 0, 1);
            if (jobInstances.size() > 0) {
                long latestInstance = jobInstances.get(0);
                sb.append("  ").append("i: ").append(latestInstance).append(System.lineSeparator());
                List<Long> executions = jobOperator.getExecutions(latestInstance);
                for (Long exId : executions) {
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
    private boolean hasRunningExecutions() {
        log.debug("Checking if we have running executions");
        for (String jobName : listJobNames()) {
            try {
                Set<Long> runningExecutions = jobOperator.getRunningExecutions(jobName);
                log.debug("Running executions for job {}: {}", jobName, runningExecutions);
                if (runningExecutions != null && runningExecutions.size() > 0) {
                    return true;
                }
            } catch (NoSuchJobException ex) {
                log.warn("Unexpected error when determining running executions", ex);
            }
        }
        return false;
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info("beforeJob: {}", jobExecution);
        fireJobEvent(jobExecution);
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        log.info("afterJob: {}", jobExecution);
        if (!jobExecution.getStatus().equals(BatchStatus.COMPLETED)) {
            // Create a cached version of the job

        }
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
        JobInstanceInfo jobInstanceInfo = null;
        try {
            jobInstanceInfo = extractJobInstanceInfo(je);
        } catch (Exception e) {
            log.warn("Failed to extract job info", e);
        }
        for (JobEventListener jel : jobEventListeners) {
            jel.onJobChange(jobInstanceInfo);
        }
    }

    private JobInstanceInfo extractJobInstanceInfo(JobExecution je) throws Exception {
        JobInstanceInfo jobInstanceInfo = new JobInstanceInfo(je.getJobInstance().getJobName());

        jobInstanceInfo.setExecutionStatus(je.getStatus().name());
        jobInstanceInfo.setExitStatus(je.getExitStatus().getExitCode());
        jobInstanceInfo.setInstanceId(je.getJobInstance().getInstanceId());
        jobInstanceInfo.setExecutionId(je.getId());
        jobInstanceInfo.setStartTime(je.getStartTime());
        jobInstanceInfo.setEndTime(je.getEndTime());
        if (je.getEndTime() != null) {
            long duration = je.getEndTime().getTime() - je.getStartTime().getTime();
            jobInstanceInfo.setDuration(duration);
        }
        HashSet<String> stepNames = new HashSet<>();
        for (StepExecution se : je.getStepExecutions()) {
            StepInfo si = extractStepInfo(se);
            jobInstanceInfo.appendStep(si);
            stepNames.add(si.getName());
        }
        List<Long> jobExecutions = jobOperator.getExecutions(je.getJobInstance().getInstanceId());
        for (Long executionId : jobExecutions) {
            if (executionId == je.getId()) {
                continue;
            }
            JobExecution jeh = jobExplorer.getJobExecution(executionId);
            int i = 0;
            for (StepExecution seh : jeh.getStepExecutions()) {
                if (!stepNames.contains(seh.getStepName())) {
                    jobInstanceInfo.insertStepAt(extractStepInfo(seh), i);
                    i++;
                } else {
                    break;
                }
            }
        }

        return jobInstanceInfo;
    }

    private StepInfo extractStepInfo(StepExecution se) {
        StepInfo si = new StepInfo(se.getStepName());
        si.setExecutionStatus(se.getStatus().name());
        if (se.getEndTime() != null) {
            long duration = se.getEndTime().getTime() - se.getStartTime().getTime();
            si.setDuration(duration);
        }

        try {
            si.setReportUrls((List<String>) se.getExecutionContext().get("reportUrls"));
        } catch (ClassCastException e) {
            log.warn("reportUrls: {}", se.getExecutionContext().get("reportUrls"));
            log.warn("Class cast error:  'reportUrls', Step: {}", se.getStepName());
        }
        si.setExitDescription(se.getExitStatus().getExitDescription());
        return si;
    }

    public List<JobInstanceInfo> jobDetails() throws Exception {
        ArrayList<JobInstanceInfo> out = new ArrayList<>();
        Set<String> jobNames = jobOperator.getJobNames();
        for (String jobName : jobNames) {
            JobInstanceInfo jobInstanceInfo = fetchJobInfo(jobName);
            out.add(jobInstanceInfo);
        }
        return out;
    }

    public List<JobInstanceInfo> jobHistory(String jobName) throws Exception {
        ArrayList<JobInstanceInfo> out = new ArrayList<>();
        List<JobInstance> jobInstances = jobExplorer.getJobInstances(jobName, 0, 3);
        for (JobInstance ji : jobInstances) {
            List<JobExecution> jobExecutions = jobExplorer.getJobExecutions(ji);
            for(JobExecution je: jobExecutions) {
                out.add(extractJobInstanceInfo(je));
            }
        }
        return out;

    }

    private JobInstanceInfo fetchJobInfo(String jobName) throws Exception {
        JobInstanceInfo out = new JobInstanceInfo(jobName);
        List<Long> jobInstances = jobOperator.getJobInstances(jobName, 0, 1);
        if (jobInstances.size() > 0) {
            long latestInstance = jobInstances.get(0);
            out.setInstanceId(latestInstance);
            List<Long> executions = jobOperator.getExecutions(latestInstance);
            if (executions.size() > 0) {
                long latestExecutionId = executions.get(0);
                JobExecution jobExecution = jobExplorer.getJobExecution(latestExecutionId);
                out = extractJobInstanceInfo(jobExecution);
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

    /**
     * Executes an external command that typically
     * @return
     * @throws Exception
     */
    public int rebuildServer() throws Exception {
        if (hasRunningExecutions()) {
            throw new RuntimeException("There are running executions! Will not rebuild");
        }
        if (rebuildServerCommand == null) {
            throw new RuntimeException("rebuildServerCommand is not set");
        }
        log.info("Executing rebuildServerCommand: '{}'", rebuildServerCommand);
        ProcessBuilder pb = new ProcessBuilder(rebuildServerCommand);
        Process process = pb.start();
        return process.waitFor();

    }
    public List<String> getServerInfo() {
        ArrayList<String> info = new ArrayList<>();
        info.add("Profiles: " + env.getProperty("spring.profiles.active","---"));
        info.add("Bauta version: " + env.getProperty("bauta.version","---"));
        info.add("Bauta build: " + env.getProperty("bauta.build","---"));
        info.add("Bauta build time: " + env.getProperty("bauta.buildTime","---"));
        info.add("Instance name: " + env.getProperty("bauta.instance.name","---"));
        info.add("Instance version: " + env.getProperty("bauta.instance.version","---"));
        info.add("Instance build: " + env.getProperty("bauta.instance.build","---"));
        info.add("Instance build time: " + env.getProperty("bauta.instance.buildTime","---"));
        info.add("Home dir: " + env.getProperty("bauta.homeDir","---"));
        info.add("Job dir: " + env.getProperty("bauta.jobBeansDir","---"));
        info.add("Staging DB: " + env.getProperty("bauta.stagingDB.url","---"));
        info.add("Staging DB user: " + env.getProperty("bauta.stagingDB.username","---"));

        return info;
    }
    public String getShortServerInfo() {
        return env.getProperty("spring.profiles.active","").replace("productionMode", "").replace(","," ") + " " + env.getProperty("bauta.instance.name","---") + " " + env.getProperty("bauta.instance.version")+ " " + env.getProperty("bauta.instance.buildTime","");
    }
}
