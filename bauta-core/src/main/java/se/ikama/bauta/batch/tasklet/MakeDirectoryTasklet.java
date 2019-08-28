package se.ikama.bauta.batch.tasklet;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.JobExecutionException;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.File;
import java.io.IOException;

/**
 * Makes a directory, including any necessary but nonexistent parent
 * directories. Fails if a file already exists with specified name but it is not a
 * directory.
 */
public class MakeDirectoryTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(MakeDirectoryTasklet.class);
    FileSystemResource[] resources = null;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {

        try {
            for (Resource resource : resources) {
                File dirFile = resource.getFile();
                FileUtils.forceMkdir(dirFile);
            }

        } catch (IOException ex) {
            throw new JobExecutionException("Failed to make directory", ex);
        }

        return RepeatStatus.FINISHED;
    }

    /**
     *
     * @param resources The directories to clean.
     */
    public void setResources(FileSystemResource[] resources) {
        this.resources = resources;
    }

}
