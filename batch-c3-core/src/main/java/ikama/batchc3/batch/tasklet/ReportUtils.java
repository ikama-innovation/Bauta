package ikama.batchc3.batch.tasklet;

import org.apache.commons.io.FilenameUtils;
import org.springframework.batch.core.StepExecution;

import java.io.File;

/**
 * Utilities for tasklets that generate reports.
 */
public class ReportUtils {

    public static File generateReportFile(String reportDir, StepExecution stepExecution, String filename) {
        String outputDir = FilenameUtils.concat(reportDir, stepExecution.getJobExecution().getJobInstance().getJobName() + "/" + stepExecution.getJobExecution().getJobId() + "/" + stepExecution.getStepName());
        String path = FilenameUtils.concat(outputDir, filename);
        File file = new File(path);
        return file;
    }
    
    public static String generateReportUrl(StepExecution stepExecution, String filename) {
        String reportUrl = "reports/" + stepExecution.getJobExecution().getJobInstance().getJobName() + "/" + stepExecution.getJobExecution().getJobId() + "/" + stepExecution.getStepName() +"/"+ filename;
        return reportUrl;
    }
}
