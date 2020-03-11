package se.ikama.bauta.core;


import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.*;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.job.flow.FlowJob;
import org.springframework.batch.core.launch.*;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import se.ikama.bauta.batch.JobParametersProvider;
import se.ikama.bauta.core.metadata.JobMetadata;
import se.ikama.bauta.core.metadata.JobMetadataReader;
import se.ikama.bauta.core.metadata.StepMetadata;
import se.ikama.bauta.scheduling.JobTrigger;
import se.ikama.bauta.scheduling.JobTriggerDao;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BautaManager implements StepExecutionListener, JobExecutionListener, ApplicationContextAware {

    Logger log = LoggerFactory.getLogger(this.getClass().getName());
    private BautaConfig bautaConfig;

    JobRepository jobRepository;

    JobOperator jobOperator;

    JobExplorer jobExplorer;

    JobRegistry jobRegistry;

    private HashSet<JobEventListener> jobEventListeners = new HashSet<>();
    private ApplicationContext applicationContext;

    private HashSet<Long> scheduledUpdateJobExecutionIds = new HashSet<Long>();

    /**
     * Holds last known information about the steps of a job
     */
    private TreeMap<String, JobInstanceInfo> cachedJobInstanceInfos = new TreeMap<>();

    ScheduledExecutorService jobUpdateScheduler;

    @Value("${bauta.rebuildServerCommand}")
    private String rebuildServerCommand;

    @Autowired
    Environment env;

    // Task scheduler for scheduled jobs
    private ThreadPoolTaskScheduler threadPoolTaskScheduler;

    @Autowired
    JobTriggerDao jobTriggerDao;

    @Autowired
    JobMetadataReader jobMetadataReader;

    public BautaManager(BautaConfig bautaConfig, JobOperator jobOperator, JobRepository jobRepository, JobExplorer jobExplorer, JobRegistry jobRegistry) {
        this.bautaConfig = bautaConfig;
        this.jobRepository = jobRepository;
        this.jobOperator = jobOperator;
        this.jobExplorer = jobExplorer;
        this.jobRegistry = jobRegistry;
        jobUpdateScheduler = Executors.newScheduledThreadPool(1);
    }


    @PostConstruct
    public void init() {
        threadPoolTaskScheduler
                = new ThreadPoolTaskScheduler();
        threadPoolTaskScheduler.setPoolSize(5);
        threadPoolTaskScheduler.setThreadNamePrefix(
                "JobTriggerScheduler");
        log.info("Home is {}", bautaConfig.getProperty(BautaConfigParams.HOME_DIR));
        log.info("properties: {}", getServerInfo().toArray());
        initializeScheduling(false);

    }
    public  void initializeScheduling(boolean refresh) {
        log.debug("Initializing scheduling");
        if (refresh) {
            log.debug("Shutting down taskScheduler");
            threadPoolTaskScheduler.shutdown();
            log.debug("Done");
        }
        threadPoolTaskScheduler.initialize();
        List<JobTrigger> jobTriggers = jobTriggerDao.loadTriggers();
        log.debug("Found {} triggers", jobTriggers.size());
        for (JobTrigger jt : jobTriggers) {
            if (jt.getTriggerType() == JobTrigger.TriggerType.CRON) {
                Runnable jobStarter = new Runnable() {
                    @Override
                    public void run() {
                        try {
                            log.info("Starting job {} based on cron '{}'", jt.getJobName(), jt.getCron());
                            startJob(jt.getJobName(), jt.getJobParameters());
                            jobTriggerDao.logSuccess(jt);
                            log.debug("Done!");

                        } catch (Exception e) {
                            //jobTriggerDao.logTriggering("Failed to start CRON-triggered job " +jt.getJobName(), e);
                            jobTriggerDao.logFailure(jt, e.getMessage());
                            log.error("Failed to start job via CRON trigger", e);
                        }
                    }
                };
                CronTrigger trigger = new CronTrigger(jt.getCron());
                log.debug("Scheduling {} at '{}'", jt.getJobName(), jt.getCron());
                threadPoolTaskScheduler.schedule(jobStarter, trigger);
            }
        }
    }

    public Long startJob(String jobName, String jobParams) throws JobParametersInvalidException, JobInstanceAlreadyExistsException, NoSuchJobException {
        if (StringUtils.isNumeric(jobName)) {
            int i = Integer.parseInt(jobName);
            jobName = listJobNames().get(i);
        }
        Set<Long> runningExecutions = jobOperator.getRunningExecutions(jobName);
        if (runningExecutions != null && runningExecutions.size() > 0) {
            throw new JobInstanceAlreadyExistsException("Job " + jobName+" is already running");
        }
        DateTimeFormatter dtf = DateTimeFormatter.ISO_DATE_TIME;
        StringBuilder paramsStr = new StringBuilder();
        paramsStr.append("start=").append(dtf.format(LocalDateTime.now()));
        if (env.containsProperty("bauta.application.git.commit.id.abbrev")) {
            paramsStr.append(",revision=");
            paramsStr.append(env.getProperty("bauta.application.git.commit.id.abbrev","?"));
            if (env.containsProperty("bauta.application.git.total.commit.count")) {
                paramsStr.append("(").append(env.getProperty("bauta.application.git.total.commit.count")).append(")");
            }
        }
        if (jobParams != null) {
            paramsStr.append(","+jobParams);
        }
        log.debug("Starting job {} with jobParams: {}", jobName, paramsStr);
        return jobOperator.start(jobName, paramsStr.toString());
    }
    public Long startJob(String jobName, Map<String, String> params) throws JobParametersInvalidException, JobInstanceAlreadyExistsException, NoSuchJobException {
        StringBuilder paramsStr = new StringBuilder();
        boolean first = true;
        if (params != null && params.size() > 0) {
            for(Map.Entry param : params.entrySet()) {
                if (!first) {
                    paramsStr.append(",");
                }
                else {
                    first = false;
                }
                paramsStr.append(param.getKey()).append("=").append(param.getValue());
            }
        }
        return startJob(jobName, paramsStr.toString());
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
        JobExecution je = jobOperator.abandon(executionId);
        fireJobEvent(je);
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
        log.debug("beforeJob: {}", jobExecution);
        fireJobEvent(jobExecution);
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        log.debug("afterJob: {}", jobExecution);
        if (jobExecution.getStatus().equals(BatchStatus.COMPLETED)) {
            handleJobCompletionTriggers(jobExecution.getJobInstance().getJobName());
        }
        fireJobEvent(jobExecution);
    }

    private void handleJobCompletionTriggers(String completedJobName) {
        log.debug("Handling jobCompletionTriggers for {}", completedJobName);
        List<JobTrigger> jobTriggers = jobTriggerDao.getJobCompletionTriggersFor(completedJobName);
        log.debug("Found {} job triggers for {} ", jobTriggers.size(), completedJobName);
        for (JobTrigger jt : jobTriggers) {
            Runnable jobStarter = new Runnable() {
                @Override
                public void run() {
                    try {
                        String jobToStart = jt.getJobName();
                        log.info("Starting job {} triggered by completion of job '{}'", jobToStart, completedJobName);
                        startJob(jobToStart, jt.getJobParameters());
                        jobTriggerDao.logSuccess(jt);
                        log.debug("Done!");

                    } catch (Exception e) {
                        jobTriggerDao.logFailure(jt, e.getMessage());
                        log.error("Failed to start job via CRON trigger", e);
                    }
                }
            };
            threadPoolTaskScheduler.schedule(jobStarter, Instant.now());
        }
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {

        log.debug("beforeStep: {}", stepExecution);
        fireJobEvent(stepExecution.getJobExecution());
        // Sometimes, the step status may still be in STARTING phase when we get here.
        // To ensure that we will get the STARTED status, schedule an update in a second or so
        scheduleDelayedJobEvent(stepExecution.getJobExecutionId());
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        log.debug("afterStep: {}", stepExecution);

        fireJobEvent(stepExecution.getJobExecution());
        return stepExecution.getExitStatus();
    }

    /**
     * Will fire a jobEvent in a second from now.
     * @param jobExecutionId
     */
    private void scheduleDelayedJobEvent(long jobExecutionId) {
        Runnable update = new Runnable() {
            @Override
            public void run() {
                JobExecution je = jobExplorer.getJobExecution(jobExecutionId);
                if (je != null) {
                    log.debug("Firing delayed update for {}", jobExecutionId);
                    fireJobEvent(je);
                }
            }
        };
        jobUpdateScheduler.schedule(update, 2000, TimeUnit.MILLISECONDS);
    }

    private void fireJobEvent(JobExecution je) {
        JobInstanceInfo jobInstanceInfo = null;
        try {
            jobInstanceInfo = extractJobInstanceInfo(je, true);
        } catch (Exception e) {
            log.warn("Failed to extract job info", e);
        }
        for (JobEventListener jel : jobEventListeners) {
            try {
                jel.onJobChange(jobInstanceInfo);
            }
            catch (Exception e)  {
                log.error("Failed to call onJobChange in one of the listeners", e);
            }
        }
    }

    private JobInstanceInfo extractBasicJobInfo(String jobName) throws Exception {
        JobInstanceInfo jobInstanceInfo = new JobInstanceInfo(jobName);
        FlowJob job = (FlowJob) jobRegistry.getJob(jobName);
        JobMetadata jobMetadata = jobMetadataReader.getMetadata(jobName);
        if (jobMetadata != null) {
            for (StepMetadata stepMetadata : jobMetadata.getAllSteps()) {
                StepInfo stepInfo = new StepInfo(stepMetadata.getId());
                stepInfo.setExecutionStatus("UNKNOWN");
                stepInfo.setType(stepMetadata.getStepType().toString());
                stepInfo.setFirstInSplit(stepMetadata.isFirstInSplit());
                stepInfo.setLastInSplit(stepMetadata.isLastInSplit());
                stepInfo.setSplitId(stepMetadata.getSplit() != null ? stepMetadata.getSplit().getId() : null);
                stepInfo.setNextId(stepMetadata.getNextId());
                jobInstanceInfo.appendStep(stepInfo);
            }
        }

        JobParametersValidator jobParametersValidator = job.getJobParametersValidator();
        if (jobParametersValidator != null && jobParametersValidator instanceof JobParametersProvider) {
            JobParametersProvider validator = (JobParametersProvider)jobParametersValidator;
            jobInstanceInfo.setRequiredJobParamKeys(validator.getRequiredKeys());
            jobInstanceInfo.setOptionalJobParamKeys(validator.getOptionalKeys());
        }
        return jobInstanceInfo;

    }

    private JobInstanceInfo extractJobInstanceInfo(JobExecution je, boolean mergeOlderExecutions) throws Exception {
        String jobName = je.getJobInstance().getJobName();
        log.debug("extractJobInstanceInfo: {}, mergeOlder: {}", je, mergeOlderExecutions);
        JobInstanceInfo jobInstanceInfo = new JobInstanceInfo(je.getJobInstance().getJobName());
        FlowJob job = (FlowJob) jobRegistry.getJob(je.getJobInstance().getJobName());
        HashMap<String, StepInfo> idToStepInfo = new HashMap<>();
        if (mergeOlderExecutions) {
            JobMetadata jobMetadata = jobMetadataReader.getMetadata(jobName);
            for (StepMetadata stepMetadata : jobMetadata.getAllSteps()) {
                StepInfo stepInfo = new StepInfo(stepMetadata.getId());
                stepInfo.setExecutionStatus("UNKNOWN");
                stepInfo.setType(stepMetadata.getStepType().toString());
                stepInfo.setSplitId(stepMetadata.getSplit() != null ? stepMetadata.getSplit().getId() : null);
                stepInfo.setNextId(stepMetadata.getNextId());
                stepInfo.setFirstInSplit(stepMetadata.isFirstInSplit());
                stepInfo.setLastInSplit(stepMetadata.isLastInSplit());
                jobInstanceInfo.appendStep(stepInfo);
                idToStepInfo.put(stepMetadata.getId(), stepInfo);
            }
        }

        JobParametersValidator jobParametersValidator = job.getJobParametersValidator();
        if (jobParametersValidator != null && jobParametersValidator instanceof JobParametersProvider) {
            JobParametersProvider validator = (JobParametersProvider)jobParametersValidator;
            jobInstanceInfo.setRequiredJobParamKeys(validator.getRequiredKeys());
            jobInstanceInfo.setOptionalJobParamKeys(validator.getOptionalKeys());
        }
        jobInstanceInfo.setExecutionStatus(je.getStatus().name());
        jobInstanceInfo.setExitStatus(je.getExitStatus().getExitCode());
        jobInstanceInfo.setInstanceId(je.getJobInstance().getInstanceId());
        jobInstanceInfo.setLatestExecutionId(je.getId());
        jobInstanceInfo.setStartTime(je.getStartTime());
        jobInstanceInfo.setEndTime(je.getEndTime());
        Properties jp = je.getJobParameters().toProperties();
        jp.remove("start");
        jobInstanceInfo.setJobParameters(jp);
        long totalDuration = 0;
        int executionCount = 1;
        if (je.getEndTime() != null) {
            totalDuration += je.getEndTime().getTime() - je.getStartTime().getTime();
            jobInstanceInfo.setLatestDuration(totalDuration);
        }
        for (StepExecution se : je.getStepExecutions()) {
            StepInfo si = idToStepInfo.get(se.getStepName());
            if (si == null) {
                si = new StepInfo(se.getStepName());
                jobInstanceInfo.appendStep(si);
            }
            extractStepInfo(si, se);
        }

        if (mergeOlderExecutions) {
            log.debug("merging with older executions");
            List<Long> jobExecutions = jobOperator.getExecutions(je.getJobInstance().getInstanceId());
            for (Long executionId : jobExecutions) {
                log.debug("executionId: {}", executionId);
                if (executionId >= je.getId()) {
                    continue;
                }
                executionCount++;
                JobExecution jeh = jobExplorer.getJobExecution(executionId);
                if (jeh.getEndTime() != null) {
                    totalDuration += jeh.getEndTime().getTime() - jeh.getStartTime().getTime();
                }
                for (StepExecution seh : jeh.getStepExecutions()) {
                    StepInfo si = idToStepInfo.get(seh.getStepName());
                    if (si != null && si.getJobExecutionId() == null) {
                        extractStepInfo(si, seh);
                        log.debug("Found old step, {}.", seh.getStepName());
                    }
                }
            }
        }

        if (totalDuration > 0 ) {
            jobInstanceInfo.setDuration(totalDuration);
        }
        jobInstanceInfo.setExecutionCount(executionCount);
        log.debug("Done");
        return jobInstanceInfo;
    }

    private void extractStepInfo(StepInfo si, StepExecution se) {
        si.setExecutionStatus(se.getStatus().name());
        si.setJobExecutionId(se.getJobExecutionId());
        si.setJobInstanceId(se.getJobExecution().getJobId());
        if (se.getEndTime() != null) {
            long duration = se.getEndTime().getTime() - se.getStartTime().getTime();
            si.setDuration(duration);
        }
        else {
            long duration = new Date().getTime() - se.getStartTime().getTime();
            si.setDuration(duration);
        }
        try {
            si.setReportUrls((List<String>) se.getExecutionContext().get("reportUrls"));
        } catch (ClassCastException e) {
            log.warn("reportUrls: {}", se.getExecutionContext().get("reportUrls"));
            log.warn("Class cast error:  'reportUrls', Step: {}", se.getStepName());
        }
        si.setExitDescription(se.getExitStatus().getExitDescription());
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
                out.add(extractJobInstanceInfo(je, false));
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
                out = extractJobInstanceInfo(jobExecution, true);
            }
        }
        else {
            out = extractBasicJobInfo(jobName);
        }
        return out;
    }



    public void registerJobChangeListener(JobEventListener jobEventListener) {
        log.debug("registering JobChangeListener {}", jobEventListener.hashCode());
        this.jobEventListeners.add(jobEventListener);
        log.debug("Number of listeners is {}", jobEventListeners.size());

    }

    public void unregisterJobChangeListener(JobEventListener jobEventListener) {
        log.debug("unregistering JobChangeListener {}", jobEventListener.hashCode());
        this.jobEventListeners.remove(jobEventListener);
        log.debug("Number of listeners is {}", jobEventListeners.size());
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;

    }

    /**
     * Executes an external command that typically
     * <ul>
     *    <li>Stops the application
     *    <li>fetches the lastest version of the application from VCS
     *    <li>Rebuilds the application</li>
     *    <li>Starts the application again</li>
     * </ul>
     * @return The exit code of the rebuild command
     * @throws Exception
     */
    public int rebuildServer() throws Exception {
        if (hasRunningExecutions()) {
            throw new RuntimeException("There are running executions! Will not rebuild");
        }
        if (rebuildServerCommand == null) {
            throw new RuntimeException("rebuildServerCommand is not set");
        }
        // Split command to support parameters, e.g. "rebuild_server param1 param2"
        String rebuildServerCommands[] =  StringUtils.split(rebuildServerCommand, " ");
        log.info("Executing rebuildServerCommand: '{}'", (Object)rebuildServerCommands);
        ProcessBuilder pb = new ProcessBuilder(rebuildServerCommands);
        Process process = pb.start();
        int result =  process.waitFor();
        log.info("Done!");
        return result;

    }
    public List<String> getServerInfo() {
        ArrayList<String> info = new ArrayList<>();
        info.add("Profiles: " + env.getProperty("spring.profiles.active","---"));
        info.add("Bauta version: " + env.getProperty("bauta.version","---"));
        info.add("Bauta build: " + env.getProperty("bauta.build","---"));
        info.add("Bauta build time: " + env.getProperty("bauta.buildTime","---"));
        info.add("Application name: " + env.getProperty("bauta.application.name","---"));
        info.add("Application description: " + env.getProperty("bauta.application.description","---"));
        info.add("Application version: " + env.getProperty("bauta.application.version","---"));
        info.add("Application build: " + env.getProperty("bauta.application.build","---"));
        info.add("Application build time: " + env.getProperty("bauta.application.buildTime","---"));
        info.add("GIT branch: " + env.getProperty("bauta.application.git.branch","---"));
        info.add("Closest tag: " + env.getProperty("bauta.application.git.closest.tag.name","---"));
        info.add("GIT commit count: " + env.getProperty("bauta.application.git.total.commit.count","---"));
        info.add("GIT id: " + env.getProperty("bauta.application.git.commit.id.abbrev","---"));
        info.add("GIT commit message: " + env.getProperty("bauta.application.git.commit.message.short","---"));
        info.add("Home dir: " + env.getProperty("bauta.homeDir","---"));
        info.add("Log dir: " + env.getProperty("bauta.logDir","---"));
        info.add("Job dir: " + env.getProperty("bauta.jobBeansDir","---"));
        info.add("Script dir: " + env.getProperty("bauta.scriptDir","---"));
        info.add("Staging DB: " + env.getProperty("bauta.stagingDB.url","---"));
        info.add("Staging DB user: " + env.getProperty("bauta.stagingDB.username","---"));

        return info;
    }
    public String getShortServerInfo() {
        StringBuilder sb = new StringBuilder();
        // Get the spring profile
        sb.append(env.getProperty("spring.profiles.active","").replace("productionMode", "").replace("production", "").replace(","," "));
        sb.append(" ").append(env.getProperty("bauta.application.name","-"));
        sb.append(" ").append(env.getProperty("bauta.application.buildTime",""));
        if (env.containsProperty("bauta.application.git.commit.id.abbrev")) {
            sb.append(" ").append(env.getProperty("bauta.application.git.branch"));
            sb.append(" ").append(env.getProperty("bauta.application.git.commit.id.abbrev"));
            if (env.containsProperty("bauta.application.git.total.commit.count")) sb.append(" (").append(env.getProperty("bauta.application.git.total.commit.count")).append(")");
        }
        else {
            sb.append(" ").append(env.getProperty("bauta.application.version"));
        }
        return  sb.toString();
    }
}
