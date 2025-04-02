package se.ikama.bauta.batch.tasklet;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.batch.core.*;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.SimpleSystemProcessExitCodeMapper;
import org.springframework.batch.core.step.tasklet.StoppableTasklet;
import org.springframework.batch.core.step.tasklet.SystemCommandException;
import org.springframework.batch.core.step.tasklet.SystemProcessExitCodeMapper;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.util.Assert;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

@Slf4j()
public class ReportingSystemCommandTasklet implements StepExecutionListener, StoppableTasklet, InitializingBean {

    @Setter
    private String command;

    @Setter
    private File workingDirectory = null;

    @Setter
    private Map<String, String> environmentParams = null;

    @Setter
    private long checkInterval = 300;

    @Setter
    private long timeout = 0;

    @Setter
    private SystemProcessExitCodeMapper systemProcessExitCodeMapper = new SimpleSystemProcessExitCodeMapper();

    private volatile boolean stopping = true;

    @Autowired
    Environment env;

    @Value("${bauta.reportDir}")
    protected String reportDir;

    /**
     * Execute system executable (sql ..) and map its exit code to
     * {@link ExitStatus}
     * using {@link SystemProcessExitCodeMapper}.
     */
    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        log.debug("execute..");
        stopping = false;
        String logFileName = "command.log";

        StepExecution stepExecution = chunkContext.getStepContext().getStepExecution();
        File logFile = ReportUtils.generateReportFile(reportDir, stepExecution, logFileName);
        FileUtils.forceMkdirParent(logFile);
        // Delete file if it exists. Could happen if this is a re-run.
        FileUtils.deleteQuietly(logFile);
        List<String> urls = new ArrayList<>();
        urls.add(ReportUtils.generateReportUrl(stepExecution, logFileName));
        chunkContext.getStepContext().getStepExecution().getExecutionContext().put("reportUrls", urls);

        chunkContext.getStepContext().getJobParameters();

        log.debug("Running commmand {}", command);
        try (PrintWriter pw = new PrintWriter(new FileWriter(logFile, true))) {

            String line = StringUtils.repeat("-", command.length());
            pw.println(line);
            pw.println(command + ":");
            pw.println(line);
            pw.flush();

        }
        FutureTask<Integer> systemCommandTask = new FutureTask<Integer>(new Callable<Integer>() {

            @Override
            public Integer call() throws Exception {
                ArrayList<String> commands = new ArrayList<>();
                if (System.getProperty("os.name").toLowerCase().contains("win")) {
                    log.debug("Running on windows.");
                    commands.add("cmd.exe");
                    commands.add("/c");

                    commands.add(command);
                } else {
                    commands.add("/bin/sh");
                    commands.add("-c");
                    commands.add(command);
                }

                log.debug("Command is: " + StringUtils.join(commands, ","));
                ProcessBuilder pb = new ProcessBuilder(commands);

                if (environmentParams != null) {
                    Map<String, String> env = pb.environment();
                    env.putAll(environmentParams);
                }

                pb.directory(workingDirectory);
                pb.redirectErrorStream(true);
                pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile));
                pb.redirectError(ProcessBuilder.Redirect.appendTo(logFile));

                Process process = pb.start();

                log.debug("Starting process for {}", command);

                return process.waitFor();
            }
        });

        long t0 = System.currentTimeMillis();
        try (SimpleAsyncTaskExecutor taskExecutor = new SimpleAsyncTaskExecutor()) {
            taskExecutor.execute(systemCommandTask);

            while (true) {
                Thread.sleep(checkInterval);

                if (systemCommandTask.isDone()) {

                    int exitCode = systemCommandTask.get();

                    log.debug("{} done. ExitCode: {}", command, exitCode);
                    // Not all errors in SQLcl leads to an error code being returned.
                    // The log file must be checked for erros.

                    if (exitCode != 0) {
                        throw new JobExecutionException("Command exited with code " + exitCode);
                    }
                    break;
                } else if (System.currentTimeMillis() - t0 > timeout) {
                    systemCommandTask.cancel(true);
                    throw new SystemCommandException(
                            "Execution of system executable did not finish within the timeout");
                } else if (chunkContext.getStepContext().getStepExecution().isTerminateOnly()) {
                    systemCommandTask.cancel(true);
                    throw new JobInterruptedException("Job interrupted while running command '" + command + "'");
                } else if (stopping) {
                    // We are in the middle of executing a SQL script. There is no way to stop and
                    // restart in a graceful way, so
                    // an interruptedexception is probably the best we can do.
                    stopping = false;
                    log.debug("Stop issued. Trying to cancel executable..");
                    boolean cancelResult = systemCommandTask.cancel(true);
                    log.debug("Cancel result: {}", cancelResult);
                    throw new JobExecutionException("Job manually stopped while running command '" + command + "'");
                }
            }
        }

        return RepeatStatus.FINISHED;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Assert.hasLength(command, "'command' property value is required");
        Assert.notNull(workingDirectory, "'workingDirectory' property value is required");
        Assert.isTrue(timeout > 0, "timeout value must be greater than zero");
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
}