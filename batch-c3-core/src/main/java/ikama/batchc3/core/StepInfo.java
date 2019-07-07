package ikama.batchc3.core;

import java.util.List;
import java.util.Objects;

public class StepInfo {

    private String name;
    private String executionStatus;
    private List<String> reportUrls;

    public StepInfo(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getExecutionStatus() {
        return executionStatus;
    }

    public void setExecutionStatus(String executionStatus) {
        this.executionStatus = executionStatus;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StepInfo stepInfo = (StepInfo) o;
        return Objects.equals(name, stepInfo.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    public void setReportUrls(List<String> reportUrls) {
        this.reportUrls = reportUrls;
    }

    public List<String> getReportUrls() {
        return reportUrls;
    }

    public boolean isRunning() {
        return "STARTED".equals(this.executionStatus) || "STARTING".equals(this.executionStatus);
    }
}
