package se.ikama.bauta.batch.tasklet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecutionException;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.StoppableTasklet;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

import java.util.Date;

/**
 * A dummy tasklet for testing/demo purposes.
 */
public class DummyTasklet implements StoppableTasklet, Tasklet {

    private static final Logger log = LoggerFactory.getLogger(DummyTasklet.class);
    private static int failureCount = 0;

    boolean stopping = false;
    String name;
    int repeats = 10;
    int sleepTimeMs = 10;
    Integer checkCount = null;
    String jobName;
    int emulateFailures = 0;

    public DummyTasklet() {
        log.debug("Creating DummyTasklet {}", name);

    }

    @Override
    public RepeatStatus execute(StepContribution sc, ChunkContext cc) throws Exception {
        if (stopping) {
            log.debug("Should stop");
            sc.setExitStatus(ExitStatus.STOPPED);
            stopping = false;
            return RepeatStatus.FINISHED;
        }
        boolean finished = false;

        //String jobName = (String)cc.getAttribute("job_name");
        if (jobName == null) {
            // Schedule
            log.debug("Scheduling..");
            //cc.setAttribute("job_name", "JABBA" + new Date());
            jobName = "JABBA" + new Date();
            //sc.incrementWriteCount(1);
            Thread.currentThread().sleep(sleepTimeMs);
            log.debug("Done!");


        } else {
            //sc.incrementReadCount();
            //Integer checkCount = (Integer)cc.getAttribute("CHECK_COUNT");
            if (checkCount == null) {
                checkCount = 1;
            } else {
                checkCount = checkCount + 1;
            }

            //cc.setAttribute("CHECK_COUNT", checkCount);
            Thread.currentThread().sleep(sleepTimeMs);
            if (checkCount > repeats) {
                finished = true;
            }
        }


        if (finished) {
            if (failureCount < emulateFailures) {
                failureCount++;
                throw new JobExecutionException("Emulated execution failure. Failure count: " + failureCount);
            }
            checkCount = 0;
            failureCount=0;
            return RepeatStatus.FINISHED;
        } else {
            return RepeatStatus.CONTINUABLE;
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getRepeats() {
        return repeats;
    }

    public void setRepeats(int repeats) {
        this.repeats = repeats;
    }

    public int getSleepTimeMs() {
        return sleepTimeMs;
    }

    public void setSleepTimeMs(int sleepTimeMs) {
        this.sleepTimeMs = sleepTimeMs;
    }


    @Override
    public void stop() {
        log.debug("stop() called. Stopping");
        stopping = true;
    }

    public void setEmulateFailures(int emulateFailures) {
        this.emulateFailures = emulateFailures;
    }


}
