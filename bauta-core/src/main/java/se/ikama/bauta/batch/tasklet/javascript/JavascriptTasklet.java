package se.ikama.bauta.batch.tasklet.javascript;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.JobExecutionException;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.listener.StepExecutionListenerSupport;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.StoppableTasklet;
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

public class JavascriptTasklet extends StepExecutionListenerSupport implements StoppableTasklet, InitializingBean {


    private static final Logger log = LoggerFactory.getLogger(JavascriptTasklet.class);

    private List<String> scriptFiles = null;

    private String executable = "node";

    private static final String SCRIPT_PARAMETER_PREFIX_JOBPARAM = "jobparam.";
    private static final String SCRIPT_PARAMETER_PREFIX_ENV = "env.";


    private Map<String, String> environmentParams = null;

    private List<String> scriptParameters = null;

    private File scriptDir = null;

    private long timeout = 0;

    private long checkInterval = 300;

    private boolean killProcessesOnStop = true;

    private String killSignal = "15";

    private boolean setExplicitCodepage = true;

    private volatile boolean stopping = true;

    private long currentExecutionId = -1;

    private String processUid;

    private boolean addProperties;

    private String propertyRegex = "";

    @Autowired
    Environment env;

    @Value("${bauta.reportDir}")
    protected String reportDir;


    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        log.debug("execute..");
        stopping = false;
        String logFileName = "nodejs.log";
        StepExecution stepExecution = chunkContext.getStepContext().getStepExecution();
        File logFile = ReportUtils.generateReportFile(reportDir, stepExecution, logFileName);
        if (stepExecution.getJobExecutionId() != currentExecutionId) {
            this.currentExecutionId = stepExecution.getJobExecutionId();
            log.debug("Setting up log urls");
            FileUtils.forceMkdirParent(logFile);
            // Delete file if it exists. Could happen if this is a re-run.
            FileUtils.deleteQuietly(logFile);
            List<String> urls = new ArrayList<>();
            urls.add(ReportUtils.generateReportUrl(stepExecution, logFileName));
            chunkContext.getStepContext().getStepExecution().getExecutionContext().put("reportUrls", urls);
            log.debug("Setting log urls in execution context {}", urls);
            return RepeatStatus.CONTINUABLE;
        }

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
            try (PrintWriter pw = new PrintWriter(new FileWriter(logFile, true))) {
                String line = StringUtils.repeat("-", scriptFile.length());
                pw.println(line);
                pw.println(scriptFile + ":");
                pw.println(line);
                pw.flush();

            }
            FutureTask<Integer> systemCommandTask = new FutureTask<Integer>(new Callable<Integer>() {

                @Override
                public Integer call() throws Exception {
                    ArrayList<String> commands = new ArrayList<>();
                    String scriptParams = StringUtils.join(scriptParameterValues, " ");
                    String cmd = "exit|"+executable+" " + scriptFile+" "+scriptParams;
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
                            log.info("key: {}, value: {}", key, val);
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

                    Map<String, String> env = pb.environment();
                    log.info("env params: {}", environmentParams);
                    if (environmentParams != null){
                        env.putAll(environmentParams);
                    }
                    log.debug("environmentParams: {}", environmentParams);
                    log.debug("Environment: {}", pb.environment());

                    pb.directory(scriptDir);
                    pb.redirectErrorStream(true);
                    pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile));
                    pb.redirectError(ProcessBuilder.Redirect.appendTo(logFile));

                    Process process = pb.start();
                    log.debug("Starting process for {}. {}", scriptFile, Thread.currentThread().getId());
                    try {
                        return process.waitFor();
                    }
                    catch(InterruptedException ie) {
                        log.debug("Interrupted. Trying to close Javascript process..");
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
                        throw new JobExecutionException("Javascript exited with code " + exitCode);
                    }
                    break;
                } else if (System.currentTimeMillis() - t0 > timeout) {
                    kill (systemCommandTask, "timeout");
                } else if (chunkContext.getStepContext().getStepExecution().isTerminateOnly()) {
                    kill (systemCommandTask, "terminateOnly");
                } else if (stopping) {
                    // We are in the middle of executing a Javascript script.
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

    private void checkForErrorsInLog(File logFile) throws JobExecutionException {
        try (LineNumberReader reader = new LineNumberReader(new InputStreamReader(new FileInputStream(logFile)))) {
            String line = null;
            while ((line = reader.readLine()) != null) {
                log.debug("line in log: {}", line);

            }
        } catch (IOException ioe) {
            throw new RuntimeException("Failed to check log file for errors", ioe);
        }
    }

    public void setExecutable(String executable) {
        this.executable = executable;
    }

    public void setEnvironmentParams(Map<String, String> envp) {
        this.environmentParams = envp;
    }

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

    
    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public void setTerminationCheckInterval(long checkInterval) {
        this.checkInterval = checkInterval;
    }

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

    public void setScriptParameters(List<String> scriptParameters) {
        this.scriptParameters = scriptParameters;
    }

    /**
     * Add Spring properties as environment variables when executing the script. 
     * If you dont want to add all properties, see {@link #setPropertyRegex(String)}
     * @param addProperties
     */
	public void setAddProperties(boolean addProperties) {
		this.addProperties = addProperties;
	}

	/**
	 * If {@link #addProperties} is set to true, only Spring properties matching the provided regexp will be added as environment variables.
	 * @param propertyRegex
	 */
	public void setPropertyRegex(String propertyRegex) {
		this.propertyRegex = propertyRegex;
	}
    
    


}
