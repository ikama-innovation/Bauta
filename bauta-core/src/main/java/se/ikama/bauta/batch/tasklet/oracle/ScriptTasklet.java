package se.ikama.bauta.batch.tasklet.oracle;

import se.ikama.bauta.batch.tasklet.ReportUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.*;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.listener.StepExecutionListenerSupport;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.StoppableTasklet;
import org.springframework.batch.core.step.tasklet.SystemCommandException;
import org.springframework.batch.core.step.tasklet.SystemProcessExitCodeMapper;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.util.Assert;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

/**
 * Runs multiple SQL/PLSQL scripts using SQL*Plus.
 * Requires SQL*Plus to be installed on the system running the tasklet.
 */
public class ScriptTasklet extends StepExecutionListenerSupport implements StoppableTasklet, InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(ScriptTasklet.class);

    private List<String> scriptFiles = null;

    private String executable = "sqlplus";

    private static final String SCRIPT_PARAMETER_PREFIX_JOBPARAM = "jobparam.";
    private static final String SCRIPT_PARAMETER_PREFIX_ENV = "env.";

    /**
     * username/password@host:port/serviceName
     */
    String easyConnectionIdentifier = null;

    private Map<String, String> environmentParams = null;

    private List<String> scriptParameters = null;

    private File scriptDir = null;

    private long timeout = 0;

    private long checkInterval = 300;

    private JobExplorer jobExplorer;

    private boolean sendExitCommand = true;

    private volatile boolean stopping = true;

    @Autowired
    Environment env;

    @Value("${bauta.reportDir}")
    protected String reportDir;

    /**
     * Execute system executable (sqlplus ..) and map its exit code to {@link ExitStatus}
     * using {@link SystemProcessExitCodeMapper}.
     */
    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        log.debug("execute..");
        stopping = false;
        String logFileName = "sqlplus.log";

        StepExecution stepExecution = chunkContext.getStepContext().getStepExecution();
        File logFile = ReportUtils.generateReportFile(reportDir, stepExecution, logFileName);
        FileUtils.forceMkdirParent(logFile);
        // Delete file if it exists. Could happen if this is a re-run.
        FileUtils.deleteQuietly(logFile);
        List<String> urls = new ArrayList<>();
        urls.add(ReportUtils.generateReportUrl(stepExecution, logFileName));
        chunkContext.getStepContext().getStepExecution().getExecutionContext().put("reportUrls", urls);

        ArrayList<String> scriptParameterValues = new ArrayList<>();
        if (scriptParameters != null && scriptParameters.size() > 0) {
            for (String sp : scriptParameters) {
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
                    commands.add(executable);
                    commands.add(easyConnectionIdentifier);
                    commands.add("@" + scriptFile);
                    commands.addAll(scriptParameterValues);
                    log.debug("Command is: " + StringUtils.join(commands, ","));
                    ProcessBuilder pb = new ProcessBuilder(commands);
                    Map<String, String> env = pb.environment();
                    env.putAll(environmentParams);
                    pb.directory(scriptDir);
                    pb.redirectErrorStream(true);
                    pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile));
                    Process process = pb.start();
                    log.debug("Starting process for {}", scriptFile);
                    if (process.isAlive() && sendExitCommand) {
                        // Pass en exit executable to
                        log.debug("Passing EXIT executable..");
                        try (OutputStream o = process.getOutputStream()) {
                            String ls = System.getProperty("line.separator");
                            o.write((ls + "EXIT" + ls).getBytes());
                            o.flush();
                        } catch (Exception e) {
                            log.warn("Unexpected error when passing EXIT executable", e);
                        }
                    }

                    return process.waitFor();
                }

            });

            long t0 = System.currentTimeMillis();
            TaskExecutor taskExecutor = new SimpleAsyncTaskExecutor();
            taskExecutor.execute(systemCommandTask);

            while (true) {
                Thread.sleep(checkInterval);

                if (systemCommandTask.isDone()) {
                    int exitCode = systemCommandTask.get();
                    log.debug("{} done. ExitCode: {}", scriptFile, exitCode);
                    // Not all errors in SQLplus leads to an error code being returned. 
                    // The log file must be checked for erros.
                    checkForErrorsInLog(logFile);
                    if (exitCode != 0) {
                        throw new JobExecutionException("SQLPLUS exited with code " + exitCode);
                    }
                    break;
                } else if (System.currentTimeMillis() - t0 > timeout) {
                    systemCommandTask.cancel(true);
                    throw new SystemCommandException("Execution of system executable did not finish within the timeout");
                } else if (chunkContext.getStepContext().getStepExecution().isTerminateOnly()) {
                    systemCommandTask.cancel(true);
                    throw new JobInterruptedException("Job interrupted while running script '" + scriptFile + "'");
                } else if (stopping) {
                    // We are in the middle of executing a SQL script. There is no way to stop and restart in a graceful way, so
                    // an interruptedexception is probably the best we can do.
                    stopping = false;
                    log.debug("Stop issued. Trying to cancel executable..");
                    boolean cancelResult = systemCommandTask.cancel(true);
                    log.debug("Cancel result: {}", cancelResult);
                    throw new JobExecutionException("Job manually stopped while running script '" + scriptFile + "'");
                }
            }
        }
        return RepeatStatus.FINISHED;
    }

    /**
     * Checks the log file for SQL errors.
     *
     * @param logFile The log file to check
     * @throws JobExecutionException If errors are found in the log file.
     */
    private void checkForErrorsInLog(File logFile) throws JobExecutionException {
        try (LineNumberReader reader = new LineNumberReader(new InputStreamReader(new FileInputStream(logFile)))) {
            String line = null;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("SP2-") || line.startsWith("CPY0") || line.startsWith("Warning:")) {
                    throw new JobExecutionException("There were SQL*Plus errors: " + line);
                } else if (line.startsWith("ORA-")) {
                    throw new JobExecutionException("There were ORA errors: " + line);
                }
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

    /**
     * By default, when executing an sql file with SQLPLUS in a "one-liner" fashion, i.e. "sglplus .. @myscript.sql", SQLPLUS will not exit from the
     * prompt unless the script contains a final "exit" executable.
     * @param sendExitCommand Set this to true if your script does not contain exit executable. Then an exit executable
     * will be passed to SQLPlus after the script has finished.
     */
    public void setSendExitCommand(boolean sendExitCommand) {
        this.sendExitCommand = sendExitCommand;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Assert.hasLength(executable, "'executable' property value is required");
        Assert.notNull(scriptFiles, "'scriptFile' property value is required");
        Assert.notEmpty(scriptFiles, "'scriptFile' property value is required");
        Assert.hasLength(easyConnectionIdentifier, "'easyConnectionIdentifier' property value is required");
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

    /**
     * For convenience and for backward compatibility, if you have only one single script file, you can use this method. Makes it a bit more
     * convenient in the Spring configuration.
     *
     * @param scriptFile The script file to run.
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
     * @param easyConnectionIdentifier username/password@host:port/serviceName
     */
    public void setEasyConnectionIdentifier(String easyConnectionIdentifier) {
        this.easyConnectionIdentifier = easyConnectionIdentifier;
    }

    /**
     * A list of script parameters to be passed to the script. Equivalent to "sqlplus @myscript.sql param1 param2".
     *
     * @param scriptParameters A list of identifiers for either a job-parameter or a spring property. A job parameter is identified by
     *                         jobparam.[job-param-key]. A spring property is identified by env.[spring-property-key]
     */
    public void setScriptParameters(List<String> scriptParameters) {
        this.scriptParameters = scriptParameters;
    }

}
