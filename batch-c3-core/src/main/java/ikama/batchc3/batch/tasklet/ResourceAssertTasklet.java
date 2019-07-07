package ikama.batchc3.batch.tasklet;

import org.springframework.batch.core.JobExecutionException;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.core.io.Resource;

import java.io.File;

/**
 * Asserts that a given directory (file/directory) exists.
 */
public class ResourceAssertTasklet implements Tasklet {
    Resource resource = null;
    String assertionMessage = null;
    Integer expectedFileCount = null;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        
        if (!resource.exists()) {
            String msg = assertionMessage;
            if (msg == null) {
                msg = "Resource " + resource.getDescription() + " does not exist";
            }
            throw new JobExecutionException(msg);
        }
        else if (expectedFileCount != null) {
            File file = resource.getFile();
            if (!file.isDirectory()) {
                throw new JobExecutionException("Resource is not a directory");
            }
            if (file.list().length != expectedFileCount) {
                throw new JobExecutionException("Directory " + resource.getFilename() + " does not contain the expected nr of files " + expectedFileCount + ". Actual nr of files is " + file.list().length);
            }
        }
        
        return RepeatStatus.FINISHED;
    }
    
    /**
     * The directory to be checked.
     * @param resource 
     */
    public void setResource(Resource resource) {
        this.resource = resource;
    }

    /**
     * An optional message to be presented if assertion fails.
     * @param assertionMessage 
     */
    public void setAssertionMessage(String assertionMessage) {
        this.assertionMessage = assertionMessage;
    }
    /** 
     * The expected amount of files in the provided directory. Optional.
     * @param expectedFileCount 
     */
    public void setExpectedFileCount(Integer expectedFileCount) {
        this.expectedFileCount = expectedFileCount;
    }


}