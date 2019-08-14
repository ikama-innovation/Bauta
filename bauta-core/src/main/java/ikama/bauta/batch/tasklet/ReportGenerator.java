package ikama.bauta.batch.tasklet;

import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;

import java.io.File;

/**
 * Interface for a report generator. Typically used by a Tasklet or a Tasklet
 * delegate.
 */
public interface ReportGenerator {

    public String getReportFilename();

    public void generateReport(File reportFile, StepContribution sc, ChunkContext cc) throws Exception;
}
