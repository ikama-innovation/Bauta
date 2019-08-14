package ikama.bauta.batch.tasklet;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


/**
 * Tasklet that generates reports. Delegates to one or many {@link ReportGenerator}.
 */
public class ReportTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(ReportTasklet.class);

    @Value("${bauta.reportDir}")
    protected String reportDir;

    protected String name;

    private List<ReportGenerator> reportGenerators = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getReportDir() {
        return reportDir;
    }

    public void setReportDir(String reportDir) {
        this.reportDir = reportDir;
    }

    protected void addReportGenerator(ReportGenerator reportGenerator) {
        reportGenerators.add(reportGenerator);
    }

    public void setReportGenerators(List<ReportGenerator> reportGenerators) {
        this.reportGenerators = reportGenerators;
    }

    @Override
    public RepeatStatus execute(StepContribution sc, ChunkContext cc) throws Exception {
        List<String> fileNames = new ArrayList<>();
        List<String> urls = new ArrayList<>();

        for (ReportGenerator reportGenerator : reportGenerators) {
            String fileName = reportGenerator.getReportFilename();
            File reportFile = ReportUtils.generateReportFile(reportDir, cc.getStepContext().getStepExecution(), fileName);
            FileUtils.forceMkdirParent(reportFile);
            reportGenerator.generateReport(reportFile, sc, cc);
            fileNames.add(fileName);
            urls.add(ReportUtils.generateReportUrl(cc.getStepContext().getStepExecution(), fileName));
        }
        cc.getStepContext().getStepExecution().getExecutionContext().put("fileNames", fileNames);
        cc.getStepContext().getStepExecution().getExecutionContext().put("reportUrls", urls);

        return RepeatStatus.FINISHED;
    }
}
