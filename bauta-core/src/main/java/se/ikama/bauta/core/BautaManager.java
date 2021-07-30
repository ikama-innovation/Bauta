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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import se.ikama.bauta.batch.JobParametersProvider;
import se.ikama.bauta.core.metadata.JobMetadata;
import se.ikama.bauta.core.metadata.JobMetadataReader;
import se.ikama.bauta.core.metadata.StepMetadata;
import se.ikama.bauta.scheduling.JobGroupDao;
import se.ikama.bauta.scheduling.JobTrigger;
import se.ikama.bauta.scheduling.JobTriggerDao;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;

public class BautaManager implements StepExecutionListener, JobExecutionListener {

    Logger log = LoggerFactory.getLogger(this.getClass().getName());
    private Date startTime = new Date();
    private BautaConfig bautaConfig;

    JobRepository jobRepository;

    JobOperator jobOperator;

    JobExplorer jobExplorer;

    JobRegistry jobRegistry;

    private SortedSet<String> jobNames = Collections.synchronizedSortedSet(new TreeSet<>());

    private Map<String, JobInstanceInfo> jobInstanceInfoCache = Collections.synchronizedMap(new HashMap<>());
    /**
     * Listeners for job updates
     */
    private HashSet<JobEventListener> jobEventListeners = new HashSet<>();

    /**
     * Lock to prevent concurrent modification of the job event list
     */
    private ReentrantLock jobEventListenerLock = new ReentrantLock();


    private BlockingQueue<JobUpdateSignal> updatedJobExecutions = new LinkedBlockingQueue<>();

    // Single thread that updates jobExecutionListeners
    private ThreadPoolTaskScheduler jobExecutionListenerUpdateThread;

    @Value("${bauta.updateThreadSleepBeforeUpdate}")
    private int updateThreadSleepBeforeUpdate = 300;

    // Task scheduler for scheduled jobs
    private ThreadPoolTaskScheduler threadPoolTaskScheduler;


    @Value("${bauta.rebuildServerCommand:}")
    private String rebuildServerCommand;

    @Value("${bauta.refreshServerCommand:}")
    private String refreshServerCommand;

    @Autowired
    Environment env;


    @Autowired
    JobTriggerDao jobTriggerDao;

    @Autowired
    JobGroupDao groupDao;

    @Autowired
    JobMetadataReader jobMetadataReader;

    // Set to true after rebuild has been issued. To avoid multiple calls to the rebuild command
    private boolean rebuilding = false;



    public BautaManager(BautaConfig bautaConfig, JobOperator jobOperator, JobRepository jobRepository, JobExplorer jobExplorer, JobRegistry jobRegistry) {
        this.bautaConfig = bautaConfig;
        this.jobRepository = jobRepository;
        this.jobOperator = jobOperator;
        this.jobExplorer = jobExplorer;
        this.jobRegistry = jobRegistry;

    }


    @PostConstruct()
    public void init() {
        threadPoolTaskScheduler
                = new ThreadPoolTaskScheduler();
        threadPoolTaskScheduler.setPoolSize(5);
        threadPoolTaskScheduler.setThreadNamePrefix(
                "JobTriggerScheduler");
        threadPoolTaskScheduler.setAwaitTerminationSeconds(5);
        log.info("Home is {}", bautaConfig.getProperty(BautaConfigParams.HOME_DIR));
        log.info("properties: {}", getServerInfo().toArray());
        initializeScheduling(false);
        TimerTask cleanupTimerTask = new TimerTask() {
            @Override
            public void run() {
                if (hasRunningExecutions()) {
                    log.warn("Detected running job(s) at startup. Probably means that server was stopped while steps were running. ");
                    log.warn("Attempting cleanup..");
                    cleanUp();
                }
            }
        };
        Timer timer = new Timer("Cleanup", true);
        timer.schedule(cleanupTimerTask, 10000);


        initListenerPushThread();
        // Make sure job names are initialized
        listJobNames();
        groupDao.setJobNames(listJobNames());

    }
    @PreDestroy
    private void shutdown() {
        log.info("Shutting down..");

        threadPoolTaskScheduler.setWaitForTasksToCompleteOnShutdown(false);
        jobExecutionListenerUpdateThread.setWaitForTasksToCompleteOnShutdown(false);
        threadPoolTaskScheduler.shutdown();
        jobExecutionListenerUpdateThread.shutdown();
        log.info("Done!");
    }

    private void cleanUp() {
        log.info("Cleaning up job executions");
        for (String jobName:jobOperator.getJobNames()) {
            try {
                for (Long executionId : jobOperator.getRunningExecutions(jobName)) {
                    JobExecution je = jobExplorer.getJobExecution(executionId);
                    if (startTime.after(je.getStartTime())) {
                        log.warn("Job {}, executionId {} is stored as running, but startTime is before (re)start. Changing to FAILED", jobName, executionId);
                        je.setEndTime(new Date());
                        je.setStatus(BatchStatus.FAILED);
                        je.setExitStatus(ExitStatus.FAILED);
                        for(StepExecution se : je.getStepExecutions()) {
                            if (se.getExitStatus().isRunning()) {
                                log.warn("Step {} is stored as running. Changing to FAILED", se.getStepName());
                                se.setStatus(BatchStatus.FAILED);
                                se.setEndTime(new Date());
                                se.setExitStatus(ExitStatus.FAILED);
                                jobRepository.update(se);
                            }
                        }
                        jobRepository.update(je);
                        fireJobEvent(je, 0);
                    }
                }
            }
            catch(Exception e) {
                log.error("Failure during cleanup", e);
            }
        }
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
            jobName = listJobNames().toArray(new String[0])[i];

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
            jobName = listJobNames().toArray(new String[0])[i];

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
        fireJobEvent(je, 0);
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

    public SortedSet<String> listJobNames() {

        if (jobNames.size() == 0) {
            log.info("Initializing jobNames");
            TreeSet<String> metaJobNames = new TreeSet<>();
            metaJobNames.addAll(jobMetadataReader.getJobMetadata().keySet());
            jobNames.addAll(metaJobNames);
            log.info("jobNames: {}", jobNames);
        }
        return jobNames;
    }

    private boolean hasRunningExecutions() {
        log.debug("Checking if we have running executions");
        for (String jobName : jobOperator.getJobNames()) {
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
        fireJobEvent(jobExecution, 0);
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        log.debug("afterJob: {}", jobExecution);
        if (jobExecution.getStatus().equals(BatchStatus.COMPLETED) || jobExecution.getStatus().equals(BatchStatus.FAILED)) {
            handleJobCompletionTriggers(jobExecution.getJobInstance().getJobName(), jobExecution.getStatus());
        }

        fireJobEvent(jobExecution, 0);
    }

    private void handleJobCompletionTriggers(String completedJobName, BatchStatus batchStatus) {
        log.debug("Handling jobCompletionTriggers for {}, {}", completedJobName, batchStatus);
        List<JobTrigger> jobTriggers = jobTriggerDao.getJobCompletionTriggersFor(completedJobName);
        log.debug("Found {} job triggers for {} ", jobTriggers.size(), completedJobName);
        for (JobTrigger jt : jobTriggers) {
            log.debug("Handling job trigger {}", jt);
            if (isValidTrigger(jt, batchStatus)) {
                Runnable jobStarter = new Runnable() {
                    @Override
                    public void run() {
                        try {
                            String jobToStart = jt.getJobName();
                            log.info("Starting job {} triggered by completion of job '{}' with status {}", jobToStart, completedJobName, batchStatus);
                            startJob(jobToStart, jt.getJobParameters());
                            jobTriggerDao.logSuccess(jt);
                            log.debug("Done!");

                        } catch (Exception e) {
                            jobTriggerDao.logFailure(jt, e.getMessage());
                            log.error("Failed to start job via trigger", e);
                        }
                    }
                };
                threadPoolTaskScheduler.schedule(jobStarter, Instant.now());
            }
        }
    }

    private boolean isValidTrigger(JobTrigger jt, BatchStatus batchStatus) {
        return (batchStatus == BatchStatus.COMPLETED && (jt.getTriggerType() == JobTrigger.TriggerType.JOB_COMPLETION || jt.getTriggerType() == JobTrigger.TriggerType.JOB_COMPLETION_OR_FAILURE))
                || (batchStatus == BatchStatus.FAILED && jt.getTriggerType() == JobTrigger.TriggerType.JOB_COMPLETION_OR_FAILURE);
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {

        log.debug("beforeStep: {}", stepExecution);
        fireJobEvent(stepExecution, 1000);
        // Sometimes, the step status may still be in STARTING phase when we get here.
        // To ensure that we will get the STARTED status, schedule an update in a second or so
        //fireJobEvent(stepExecution.getJobExecution().getId(), 2000);
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        log.debug("afterStep: {}", stepExecution);
        fireJobEvent(stepExecution, 0);
        return stepExecution.getExitStatus();
    }

    private void fireJobEvent(StepExecution se, int delay) {
        String jobName = se.getJobExecution().getJobInstance().getJobName();
        JobInstanceInfo jii = jobInstanceInfoCache.get(jobName);
        if (jii != null) {
            StepInfo si = jii.getStep(se.getStepName());
            extractStepInfo(si, se);
            jii.updateCount();
            JobUpdateSignal signal = new JobUpdateSignal(JobUpdateSignal.UpdateType.StepUpdate, se.getJobExecutionId(), se.getStepName());
            if (!updatedJobExecutions.contains(signal)) {
                updatedJobExecutions.add(signal);
            }
        }
        else {
            fireJobEvent(se.getJobExecution(), 0);
        }
    }

    private void fireJobEvent(JobExecution je, int delayMs) {
        // Invalidate cache
        // TODO: Improve. Could potentially ad a job execution multiple times
        // TODO: Handle delayed
        JobUpdateSignal signal = new JobUpdateSignal(JobUpdateSignal.UpdateType.JobUpdate, je.getId(), null);
        if (!updatedJobExecutions.contains(signal)) {
            updatedJobExecutions.add(signal);
        }
    }

    private void initListenerPushThread() {

        Runnable jobEventUpdate = () -> {
            while(true) {
                try {
                    log.debug("Waiting for updated jobs..");
                    JobUpdateSignal signal = updatedJobExecutions.take();
                    log.debug("Updated job signal: {}, updatedJobExecutions: {}", signal, updatedJobExecutions);
                    // Sleep some time to let the state be properly persisted by spring batch
                    JobInstanceInfo jobInstanceInfo = null;
                    StepInfo stepInfo = null;
                    Thread.sleep(updateThreadSleepBeforeUpdate);
                    JobExecution je = jobExplorer.getJobExecution(signal.getJobExecutionId());
                    updatedJobExecutions.remove(signal);
                    jobInstanceInfo = extractJobInstanceInfo(je, true);
                    jobInstanceInfoCache.put(jobInstanceInfo.getName(), jobInstanceInfo);

                    if (signal.getUpdateType() == JobUpdateSignal.UpdateType.StepUpdate) {
                        stepInfo = jobInstanceInfo.getStep(signal.getStepName());

                    }
                    // Lock access to the listener set to prevent concurrent modification issues
                    jobEventListenerLock.lock();
                    try {
                        log.debug("Updating {} listeners ..", jobEventListeners.size());
                        for (JobEventListener jel : jobEventListeners) {
                            try {
                                long t = System.nanoTime();
                                if (signal.getUpdateType() == JobUpdateSignal.UpdateType.StepUpdate) {
                                    jel.onStepChange(jobInstanceInfo, stepInfo);
                                }
                                else {
                                    jel.onJobChange(jobInstanceInfo);
                                }
                                t = System.nanoTime() - t;
                                double tMs = Math.round(t / 1000000.0);
                                if (tMs > 1000) {
                                    log.warn("Call to onJobChange in {} took {} ms. Listeners must execute fast", jel, tMs);
                                }
                            } catch (Exception e) {
                                log.error("Failed to call onJobChange in one of the listeners", e);
                            }
                        }
                        log.debug("Done updating listeners!");

                    } finally {
                        jobEventListenerLock.unlock();
                    }

                } catch (InterruptedException e) {
                    log.info("Ending update push thread");
                    break;
                }
                catch (Exception e) {
                    log.error("Failed to extract job info and update listeners", e);
                }
            }
        };

        //jobExecutionListenerUpdateThread = new Thread(jobEventUpdate, "JobExListenerUpdater" );
        //jobExecutionListenerUpdateThread.setDaemon(true);
        //jobExecutionListenerUpdateThread.start();
        jobExecutionListenerUpdateThread = new ThreadPoolTaskScheduler();
        jobExecutionListenerUpdateThread.setThreadNamePrefix("JobExecutionListenerPush");
        jobExecutionListenerUpdateThread.setAwaitTerminationSeconds(5);
        jobExecutionListenerUpdateThread.setWaitForTasksToCompleteOnShutdown(false);
        jobExecutionListenerUpdateThread.initialize();
        jobExecutionListenerUpdateThread.schedule(jobEventUpdate, Instant.now());

    }

    private JobInstanceInfo extractBasicJobInfo(String jobName) throws Exception {
        JobInstanceInfo jobInstanceInfo = new JobInstanceInfo(jobName);
        FlowJob job = (FlowJob) jobRegistry.getJob(jobName);
        JobMetadata jobMetadata = jobMetadataReader.getMetadata(jobName);
        if (jobMetadata != null) {
        	jobInstanceInfo.setDescription(jobMetadata.getDescription());
            for (StepMetadata stepMetadata : jobMetadata.getAllSteps()) {
                StepInfo stepInfo = new StepInfo(stepMetadata.getId());
                stepInfo.setExecutionStatus("UNKNOWN");
                stepInfo.setType(stepMetadata.getStepType().toString());
                stepInfo.setFirstInSplit(stepMetadata.isFirstInSplit());
                stepInfo.setLastInSplit(stepMetadata.isLastInSplit());
                stepInfo.setSplitId(stepMetadata.getSplit() != null ? stepMetadata.getSplit().getId() : null);
                stepInfo.setFlowId(stepMetadata.getFlow() != null ? stepMetadata.getFlow().getId() : null);
                stepInfo.setNextId(stepMetadata.getNextId());
                jobInstanceInfo.appendStep(stepInfo);
            }
            jobInstanceInfo.updateCount();
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
            jobInstanceInfo.setDescription(jobMetadata.getDescription());
            for (StepMetadata stepMetadata : jobMetadata.getAllSteps()) {
                StepInfo stepInfo = new StepInfo(stepMetadata.getId());
                stepInfo.setExecutionStatus("UNKNOWN");
                stepInfo.setType(stepMetadata.getStepType().toString());
                stepInfo.setSplitId(stepMetadata.getSplit() != null ? stepMetadata.getSplit().getId() : null);
                stepInfo.setFlowId(stepMetadata.getFlow() != null ? stepMetadata.getFlow().getId() : null);
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
            if (!mergeOlderExecutions) {
                // In this case, we have not added metadata to steps. Get step type from metadata
                StepMetadata.StepType stepType = jobMetadataReader.getMetadata(jobName).getStepMetadata(si.getName()).getStepType();
                if (stepType != null) {
                    si.setType(stepType.name());
                }
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
        jobInstanceInfo.updateCount();
        log.debug("extractJobInstanceInfo Done");
        return jobInstanceInfo;
    }

    private void extractStepInfo(StepInfo si, StepExecution se) {
        si.setExecutionStatus(se.getStatus().name());
        si.setJobExecutionId(se.getJobExecutionId());
        si.setJobInstanceId(se.getJobExecution().getJobId());
        try {
            si.setReportUrls((List<String>) se.getExecutionContext().get("reportUrls"));
        } catch (ClassCastException e) {
            log.warn("reportUrls: {}", se.getExecutionContext().get("reportUrls"));
            log.warn("Class cast error:  'reportUrls', Step: {}", se.getStepName());
        }
        si.setExitDescription(se.getExitStatus().getExitDescription());
        si.setStartTime(se.getStartTime());
        si.setEndTime(se.getEndTime());
        if ("RW".equals(si.getType())) {
            ReadWriteInfo rwi = new ReadWriteInfo();
            rwi.setCommitCount(se.getCommitCount());
            rwi.setFilterCount(se.getFilterCount());
            rwi.setProcessSkipCount(se.getProcessSkipCount());
            rwi.setReadCount(se.getReadCount());
            rwi.setReadSkipCount(se.getReadSkipCount());
            rwi.setRollbackCount(se.getRollbackCount());
            rwi.setWriteCount(se.getWriteCount());
            rwi.setWriteSkipCount(se.getWriteSkipCount());
            si.setReadWriteInfo(rwi);
        }

    }

    public List<JobInstanceInfo> jobDetails() throws Exception {
        long t = System.nanoTime();
        ArrayList<JobInstanceInfo> out = new ArrayList<>();
        for (String jobName : listJobNames()) {
            JobInstanceInfo jobInstanceInfo = fetchJobInfo(jobName);
            out.add(jobInstanceInfo);
        }
        if (log.isDebugEnabled()) {
            t = System.nanoTime() - t;
            double ms = Math.round(t/1000000.0);
            log.debug("jobDetails returned {} jobs, took {} ms", out.size(), ms);
        }
        return out;
    }
    public JobInstanceInfo jobDetails(String jobName) throws Exception {
        return fetchJobInfo(jobName);
    }

    public List<JobInstanceInfo> jobHistory(String jobName) throws Exception {
        ArrayList<JobInstanceInfo> out = new ArrayList<>();
        List<JobInstance> jobInstances = jobExplorer.getJobInstances(jobName, 0, 3);
        if (jobInstances != null) {
            for (JobInstance ji : jobInstances) {
                List<JobExecution> jobExecutions = jobExplorer.getJobExecutions(ji);
                if (jobExecutions != null) {
                    for (JobExecution je : jobExecutions) {
                        out.add(extractJobInstanceInfo(je, false));
                    }
                }
            }
        }
        return out;

    }

    private JobInstanceInfo fetchJobInfo(String jobName) throws Exception {
        JobInstanceInfo out = jobInstanceInfoCache.get(jobName);
        if (out != null) {
            log.debug("Found job info in cache: {}", jobName);
            return out;
        }

        out = new JobInstanceInfo(jobName);

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
            jobInstanceInfoCache.put(jobName, out);
        }
        else {
            out = extractBasicJobInfo(jobName);
        }
        return out;
    }



    public void registerJobChangeListener(JobEventListener jobEventListener) {
        log.debug("registering JobChangeListener {}", jobEventListener.hashCode());
        this.jobEventListenerLock.lock();
        try {
            this.jobEventListeners.add(jobEventListener);
            log.debug("Number of listeners is {}", jobEventListeners.size());
        }
        finally {
            this.jobEventListenerLock.unlock();
        }

    }

    public void unregisterJobChangeListener(JobEventListener jobEventListener) {
        log.debug("unregistering JobChangeListener {}", jobEventListener.hashCode());
        this.jobEventListenerLock.lock();
        try {
            this.jobEventListeners.remove(jobEventListener);
            log.debug("Number of listeners is {}", jobEventListeners.size());
        } finally {
            this.jobEventListenerLock.unlock();
        }

    }

    public boolean isRebuildSupported() {
        return StringUtils.isNotEmpty(this.rebuildServerCommand);
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
        if (!isRebuildSupported()) throw new RuntimeException("Refresh is not supported. Set bauta.refreshServerCommand");
        if (rebuilding) {
            // Already rebuilding
            return -1;
        }
        rebuilding = true;
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
    public boolean isRefreshSupported() {
        return StringUtils.isNotEmpty(this.refreshServerCommand);
    }

    public int refreshServer() throws Exception  {
        if (!isRefreshSupported()) throw new RuntimeException("Refresh is not supported. Set bauta.refreshServerCommand");
        String refreshServerCommands[] =  StringUtils.split(refreshServerCommand, " ");
        log.info("Executing refreshServerCommand: '{}'", (Object)refreshServerCommands);
        ProcessBuilder pb = new ProcessBuilder(refreshServerCommands);
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

        // JVM info
        int MB = 1024*1024;
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / MB;
        long freeMemory = runtime.freeMemory() / MB;
        long totalMemory = runtime.totalMemory() / MB;
        long maxMemory = runtime.maxMemory() / MB;
        info.add("JVM used mem: " + usedMemory);
        info.add("JVM free mem: " + freeMemory);
        info.add("JVM total mem: " + totalMemory);
        info.add("JVM max mem: " + maxMemory);

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
