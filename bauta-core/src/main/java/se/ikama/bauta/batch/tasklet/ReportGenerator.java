package se.ikama.bauta.batch.tasklet;

import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;

import java.io.File;

/**
 * Interface for a report generator. Typically used by a Tasklet or a Tasklet
 * delegate.
 */
public interface ReportGenerator {

    /**
     * A user-friendly name of the generated report
     */
    public String getReportName();

    /**
     * Name for the generated report file
     */
    public String getReportFilename();

    /**
     * Generate a report
     * @param reportFile The file to write the report to
     * @param sc The current step contribution
     * @param cc The current ChunkContext
     * @return A ReportGenerationResult. Typically with the status OK.
     * For certain types of report generators with a notion of "failure", it may make sense to return Failed, to support. See @{@link SqlValidationTasklet}
     * @throws Exception
     */
    public ReportGenerationResult generateReport(File reportFile, StepContribution sc, ChunkContext cc) throws Exception;
}
