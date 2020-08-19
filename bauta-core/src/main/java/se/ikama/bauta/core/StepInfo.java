package se.ikama.bauta.core;

import lombok.Data;

import java.util.Date;
import java.util.List;
import java.util.Objects;

@Data
public class StepInfo {

    private String name;
    private String executionStatus;
    private String type;
    private List<String> reportUrls;
    private String exitDescription;
    private Long jobInstanceId;
    private Long jobExecutionId;
    private String splitId;
    private String flowId;
    private boolean firstInSplit;
    private boolean lastInSplit;
    private String nextId;
    private Date startTime;
    private Date endTime;

    public StepInfo(String name) {
        this.name = name;
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

    public long getDuration() {
        if (endTime != null && startTime != null) {
            long duration = endTime.getTime() - startTime.getTime();
            return duration;
        }
        else if (startTime != null){
            long duration = new Date().getTime() - startTime.getTime();
            return duration;
        }
        else {
            return 0;
        }
    }

    public boolean isRunning() {
        return "STARTED".equals(this.executionStatus) || "STARTING".equals(this.executionStatus);
    }
}
