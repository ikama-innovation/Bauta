package ikama.bauta.batch.tasklet;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.JobExecutionException;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.core.io.FileSystemResource;

import java.io.File;
import java.io.IOException;

/**
 * Deletes all files in the given directory.
 */
public class CleanDirectoryTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(CleanDirectoryTasklet.class);
    FileSystemResource directory = null;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {

        try {
            File dirFile = directory.getFile();
            if (!dirFile.exists()) {
                log.warn("The directory to clean does not exist: {}", directory.toString());
                return RepeatStatus.FINISHED;
            }
            if (!dirFile.isDirectory()) {
                throw new JobExecutionException("The provided directory path does not point to a directory: " + directory.toString());
            }
            FileUtils.cleanDirectory(dirFile);

        } catch (IOException ex) {
            throw new JobExecutionException("Failed to clean directory: " + directory.toString(), ex);
        }

        return RepeatStatus.FINISHED;
    }

    /**
     * The directory to clean.
     *
     * @param directory
     */
    public void setDirectory(FileSystemResource directory) {
        this.directory = directory;
    }

}
