package se.ikama.bauta.batch.tasklet.python;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecutionException;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.listener.StepExecutionListenerSupport;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.StoppableTasklet;
import org.springframework.batch.core.step.tasklet.SystemProcessExitCodeMapper;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.util.Assert;
import se.ikama.bauta.batch.tasklet.ReportUtils;

import java.io.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;


public class PythonTasklet extends StepExecutionListenerSupport implements StoppableTasklet, InitializingBean {


    private static final Logger log = LoggerFactory.getLogger(PythonTasklet.class);

    private List<String> scriptFiles = null;

    private String executable = "python3";

    private static final String SCRIPT_PARAMETER_PREFIX_JOBPARAM = "jobparam.";
    private static final String SCRIPT_PARAMETER_PREFIX_ENV = "env.";


    private Map<String, String> environmentParams = null;

    private List<String> scriptParameters = null;

    private File scriptDir = null;

    private long timeout = 0;

    private long checkInterval = 300;

    /**
     * In order to properly stop the running python process, we need to kill the process on the OS level using kill/pkill.
     * If you for some reason need to disable this feature, set this to false.
     */
    private boolean killProcessesOnStop = true;

    /**
     * Kill signal to use when the python process is killed.
     * Defaults to 15 (SIGTERM)
     */
    private String killSignal = "15";

    /**
     *
     */
    private boolean setExplicitCodepage = false;

    private JobExplorer jobExplorer;

    private volatile boolean stopping = true;

    private long currentExecutionId = -1;

    /**
     *
     */
    private String logSuffix = "log";

    /**
     * The name of the generated report/log file. Without file suffix.
     */
    private String reportName = null;

    /**
     * A unique id for the group of processes that are started for each script. The uid is added to the command line
     * to make it possible to find and kill all processes with a command line containing this uid.
     */
    private String processUid;

    private boolean addProperties = false;

    private String propertyRegex = "";

    @Autowired
    Environment env;

    @Value("${bauta.reportDir}")
    protected String reportDir;

    /**
     * Execute system executable (python ..) and map its exit code to {@link ExitStatus}
     * using {@link SystemProcessExitCodeMapper}.
     */
    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        StringBuilder reportFileName = new StringBuilder();
        if (reportName != null) {
            reportFileName.append(reportName);
        }
        else {
            reportFileName.append(contribution.getStepExecution().getStepName());
        }
        reportFileName.append(".").append(logSuffix);

        StepExecution stepExecution = chunkContext.getStepContext().getStepExecution();
        File logFile = ReportUtils.generateReportFile(reportDir, stepExecution, reportFileName.toString());
        if (stepExecution.getJobExecutionId() != currentExecutionId) {
            currentExecutionId = stepExecution.getJobExecutionId();
            log.debug("Setting up log urls");
            FileUtils.forceMkdirParent(logFile);
            // Delete file if it exists. Could happen if this is a re-run.
            FileUtils.deleteQuietly(logFile);
            List<String> urls = new ArrayList<>();
            urls.add(ReportUtils.generateReportUrl(stepExecution, reportFileName.toString()));
            chunkContext.getStepContext().getStepExecution().getExecutionContext().put("reportUrls", urls);
            log.debug("Setting log urls in execution context {}", urls);
            return RepeatStatus.CONTINUABLE;
        }

        stopping = false;

        log.debug("scriptParameters: {}", scriptParameters);
        ArrayList<String> scriptParameterValues = new ArrayList<>();
        if (scriptParameters != null && scriptParameters.size() > 0) {
            for (String sp : scriptParameters) {
                log.debug("scriptParameter: {}", sp);
                if (sp.startsWith(SCRIPT_PARAMETER_PREFIX_JOBPARAM)) {
                    String jobParamKey = sp.substring(SCRIPT_PARAMETER_PREFIX_JOBPARAM.length());
                    Map<String, Object> jobParameters = chunkContext.getStepContext().getJobParameters();
                    Object value = jobParameters.get(jobParamKey);
                    if (value == null) {
                        throw new IllegalArgumentException("No job-parameter named '" + jobParamKey + " found.");
                    }
                    scriptParameterValues.add(value.toString());
                } else if (sp.startsWith(SCRIPT_PARAMETER_PREFIX_ENV)) {
                    String jobParamKey = sp.substring(SCRIPT_PARAMETER_PREFIX_ENV.length());

                    String value = env.getProperty(jobParamKey);
                    if (value == null) {
                        throw new IllegalArgumentException("No Spring property '" + jobParamKey + " found.");
                    }
                    scriptParameterValues.add(value);
                } else {
                    scriptParameterValues.add(sp);
                }
            }
        }
        chunkContext.getStepContext().getJobParameters();

        for (String scriptFile : scriptFiles) {
            log.debug("Handling {}", scriptFile);
            if (StringUtils.equals(logSuffix, "log")) {
                try (PrintWriter pw = new PrintWriter(new FileWriter(logFile, true))) {
                    String line = StringUtils.repeat("-", scriptFile.length());
                    pw.println(line);
                    pw.println(scriptFile + ":");
                    pw.println(line);
                    pw.flush();
                }
            }
            FutureTask<Integer> systemCommandTask = new FutureTask<Integer>(new Callable<Integer>() {

                @Override
                public Integer call() throws Exception {
                    ArrayList<String> commands = new ArrayList<>();
                    String scriptParams = StringUtils.join(scriptParameterValues, " ");
                    String cmd = "exit|"+executable+" "+scriptFile+" "+scriptParams;
                    if (runsOnWindows()) {
                        log.debug("Running on windows.");
                        commands.add("cmd.exe");
                        commands.add("/c");
                        if (setExplicitCodepage) {
                            cmd = "chcp 65001|" + cmd;
                        }
                        commands.add(cmd);
                    }
                    else {
                        commands.add("/bin/sh");
                        commands.add("-c");
                        // Add the processUid as the last script parameter
                        if (killProcessesOnStop) {
                            if (processUid == null) {
                                processUid = UUID.randomUUID().toString();
                            }
                            cmd = cmd + " " + processUid;
                        }
                        commands.add(cmd);
                    }

                    Properties props = new Properties();
                    MutablePropertySources propSrcs = ((AbstractEnvironment) env).getPropertySources();
                    StreamSupport.stream(propSrcs.spliterator(), false)
                            .filter(ps -> ps instanceof EnumerablePropertySource)
                            .map(ps -> ((EnumerablePropertySource) ps).getPropertyNames())
                            .flatMap(Arrays::<String>stream)
                            .forEach(propName -> props.setProperty(propName, env.getProperty(propName)));

                    if (environmentParams.size() > 0) {
                        environmentParams.forEach((key, val) -> {
                            if (key.equals("addProperties") && val.equals("true")) {
                                addProperties = true;
                            } else if (key.equals("propertyRegex")) {
                                propertyRegex = val;
                            }
                        });
                    }
                    if (addProperties && propertyRegex.length() > 0){
                        props.forEach((key, val) -> {
                            if (key.toString().matches(propertyRegex)){
                                key = key.toString().toUpperCase();
                                key = key.toString().replaceAll("\\.", "_");
                                environmentParams.put(key.toString(), val.toString());
                            }
                        });
                    }

                    log.debug("Command is: " + StringUtils.join(commands, ","));
                    ProcessBuilder pb = new ProcessBuilder(commands);

                    String jobInstanceId = Long.toString(contribution.getStepExecution().getJobExecution().getJobInstance().getInstanceId());
                    String jobExecutionId = Long.toString(contribution.getStepExecution().getJobExecution().getId());
                    String jobName = contribution.getStepExecution().getJobExecution().getJobInstance().getJobName();
                    String stepName = contribution.getStepExecution().getStepName();
                    Map<String, String> env = pb.environment();
                    env.put("BAUTA_JOB_INSTANCE_ID", jobInstanceId);
                    env.put("BAUTA_JOB_EXECUTION_ID", jobExecutionId);
                    env.put("BAUTA_STEP_NAME", stepName);
                    env.put("BAUTA_JOB_NAME", jobName);
                    if (environmentParams != null){
                        env.putAll(environmentParams);
                        log.debug("environmentParams: {}", environmentParams);
                    }
                    log.debug("Environment: {}", pb.environment());

                    pb.directory(scriptDir);
                    pb.redirectErrorStream(true);
                    pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile));
                    pb.redirectError(ProcessBuilder.Redirect.appendTo(logFile));

                    Process process = pb.start();
                    log.debug("Starting process for {}. {}", scriptFile, Thread.currentThread().getId());
                    try {
                        log.warn("Process exit code: {}", process.waitFor());
                        return process.waitFor();
                    }
                    catch(InterruptedException ie) {
                        log.debug("Interrupted. Trying to close python process..");
                        process.destroyForcibly();
                        log.debug("After destroy.");
                        return -1;
                    }
                    finally {
                        try {
                            process.getOutputStream().flush();
                        } catch(Exception e) {
                            log.warn("Failed to flush process output stream");
                        }
                    }
                }
            });

            long t0 = System.currentTimeMillis();
            TaskExecutor taskExecutor = new SimpleAsyncTaskExecutor(stepExecution.getStepName());
            taskExecutor.execute(systemCommandTask);

            while (true) {
                Thread.sleep(checkInterval);

                if (systemCommandTask.isDone()) {

                    int exitCode = systemCommandTask.get();

                    log.debug("{} done. ExitCode: {}", scriptFile, exitCode);

                    checkForErrorsInLog(logFile);

                    if (exitCode != 0) {
                        throw new JobExecutionException("python exited with code " + exitCode);
                    }
                    break;
                } else if (System.currentTimeMillis() - t0 > timeout) {
                    kill (systemCommandTask, "timeout");
                } else if (chunkContext.getStepContext().getStepExecution().isTerminateOnly()) {
                    kill (systemCommandTask, "terminateOnly");
                } else if (stopping) {
                    // We are in the middle of executing a python script.
                    // Only thing we can do is to terminate the processes that have been started.
                    stopping = false;
                    kill(systemCommandTask, "stop");
                }
            }
        }
        return RepeatStatus.FINISHED;
    }

    private boolean runsOnWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    /**
     * Checks the log file for errors that should result in a step failure.
     *
     * @param logFile
     * @throws JobExecutionException If errors are found in the log file.
     */
    private void checkForErrorsInLog(File logFile) throws JobExecutionException {
        try (LineNumberReader reader = new LineNumberReader(new InputStreamReader(new FileInputStream(logFile)))) {
            String line = null;
            while ((line = reader.readLine()) != null) {
                //if (line.startsWith("Error")) {
                //    throw new JobExecutionException("There were python errors: " + line);
                //}

            }
        } catch (IOException ioe) {
            throw new RuntimeException("Failed to check log file for errors", ioe);
        }
    }

    /**
     * @param executable executable to be executed in a separate system process
     */
    public void setExecutable(String executable) {
        this.executable = executable;
    }

    /**
     * @param envp environment parameter values, inherited from parent process when not set (or set to null).
     */
    public void setEnvironmentParams(Map<String, String> envp) {
        this.environmentParams = envp;
    }

    /**
     * @param dir working directory of the spawned process, inherited from parent process when not set (or set to null).
     */
    public void setScriptDir(String dir) {
        if (dir == null) {
            this.scriptDir = null;
            return;
        }
        this.scriptDir = new File(dir);
        Assert.isTrue(scriptDir.exists(), "working directory must exist");
        Assert.isTrue(scriptDir.isDirectory(), "working directory value must be a directory");

    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Assert.hasLength(executable, "'executable' property value is required");
        Assert.notNull(scriptFiles, "'scriptFile' property value is required");
        Assert.notEmpty(scriptFiles, "'scriptFile' property value is required");
        Assert.notNull(scriptDir, "'scriptDir' property is required");
        Assert.isTrue(timeout > 0, "timeout value must be greater than zero");
    }

    public void setJobExplorer(JobExplorer jobExplorer) {
        this.jobExplorer = jobExplorer;
    }

    /**
     * Timeout in milliseconds.
     *
     * @param timeout upper limit for how long the execution of the external program is allowed to last.
     */
    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    /**
     * The time interval how often the tasklet will check for termination status.
     *
     * @param checkInterval time interval in milliseconds (1 second by default).
     */
    public void setTerminationCheckInterval(long checkInterval) {
        this.checkInterval = checkInterval;
    }

    /**
     * Will try to interrupt the thread executing the system executable.
     *
     * @see StoppableTasklet#stop()
     */
    @Override
    public void stop() {
        log.debug("Stop executable received");
        stopping = true;
    }

    private void kill(FutureTask task, String reason) throws JobExecutionException {
        if (killProcessesOnStop) {
            if (processUid == null) {
                // This should not happen.
                log.warn("processUid is null, so no way to lookup processes");
                return;
            }
            if (!runsOnWindows()) {
                // On linux, there will be several sub-processes and there is no way to get access to the PIDs of these.
                // Instead, we have added the processUid to the end of the command and we can now use the pkill command to
                // kill all process with that UID.
                ProcessBuilder pb = new ProcessBuilder("pkill", "--signal", this.killSignal, "-f", processUid);
                try {
                    log.debug("Trying to kill all processes with UID {}", processUid);
                    boolean finished = pb.start().waitFor(5, TimeUnit.SECONDS);
                    log.debug("Kill command exited. Finished before timeout: {}", finished);
                } catch (Exception e) {
                    log.warn("Error when trying to kill processes with UID " + processUid, e);

                }
            } else {
                //TODO: Handle in windows
                log.warn("Killing processes is not implemented for windows OS");
            }
        }
        else {
            // Only think we can do here is to cancel the task
            task.cancel(true);
            // .. and throw an excecption to make the step as failed
            throw new JobExecutionException("Terminating job. Reason: " + reason);
        }
    }

    /**
     * For convenience and for backward compatibility, if you have only one single script file, you can use this method. Makes it a bit more
     * convenient in the Spring configuration.
     */
    public void setScriptFile(String scriptFile) {
        if (this.scriptFiles == null) {
            ArrayList<String> scriptFiles = new ArrayList<>();
            scriptFiles.add(scriptFile);
            this.scriptFiles = scriptFiles;
        } else {
            throw new IllegalArgumentException("Properties scriptFile and scriptFiles can not both have values. Only one can be used");
        }
    }

    public void setScriptFiles(List<String> scriptFiles) {
        this.scriptFiles = scriptFiles;
    }


    /**
     * A list of script parameters to be passed to the script. Equivalent to "python myscript.py param1 param2".
     *
     * @param scriptParameters A list of identifiers for either a job-parameter or a spring property. A job parameter is identified by
     *                         jobparam.[job-param-key]. A spring property is identified by env.[spring-property-key]
     */
    public void setScriptParameters(List<String> scriptParameters) {
        this.scriptParameters = scriptParameters;
    }


    public void setKillProcessesOnStop(boolean killProcessesOnStop) {
        this.killProcessesOnStop = killProcessesOnStop;
    }

    public void setKillSignal(String killSignal) {
        this.killSignal = killSignal;
    }

    public void setSetExplicitCodepage(boolean setExplicitCodepage) {
        this.setExplicitCodepage = setExplicitCodepage;
    }

    public void setLogSuffix(String logSuffix) {
        this.logSuffix = logSuffix;
    }

    public void setReportName(String reportName) {
        this.reportName = reportName;
    }
}