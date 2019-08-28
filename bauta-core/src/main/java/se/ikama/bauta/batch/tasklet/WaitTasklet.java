package se.ikama.bauta.batch.tasklet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.StoppableTasklet;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

import java.util.Date;

/**
 * A tasklet that waits a given amount of time.
 */
public class WaitTasklet implements StoppableTasklet, Tasklet {

    private static final Logger log = LoggerFactory.getLogger(WaitTasklet.class);

    boolean stopping = false;
    private int sleepTimeMs = 500;
    private int waitTimeSeconds = 30;

    public WaitTasklet() {
        log.debug("Creating WaitTasklet {}");

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
        Date startTime = (Date) cc.getStepContext().getAttribute("START_TIME");
        if (startTime == null) {
            startTime = new Date();
            log.debug("No start-time found. Setting it to {}", startTime);
            cc.getStepContext().setAttribute("START_TIME", startTime);
        }
        Date now = new Date();
        long passedTimeMs = now.getTime() - startTime.getTime();
        log.debug("Passed time (ms): {}", passedTimeMs);
        if (passedTimeMs > (waitTimeSeconds * 1000)) {
            log.debug("Time is up. Finished!");
            finished = true;
        }

        if (finished) {
            return RepeatStatus.FINISHED;
        } else {
            Thread.sleep(sleepTimeMs);
            return RepeatStatus.CONTINUABLE;
        }
    }

    @Override
    public void stop() {
        log.debug("stop() called. Stopping");
        stopping = true;
    }

    public int getWaitTimeSeconds() {
        return waitTimeSeconds;
    }

    public void setWaitTimeSeconds(int waitTimeSeconds) {
        this.waitTimeSeconds = waitTimeSeconds;
    }

}
