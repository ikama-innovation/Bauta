package ikama.batchc3.core;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

public class JobInstanceInfo {

    private String name;
    private String executionStatus;
    private String exitStatus;
    private Long executionId;
    private Long instanceId;
    private Date startTime;
    private Date endTime;
    private long duration;
    private List<StepInfo> steps = new ArrayList<>();

    public JobInstanceInfo(String jobName) {
        this.name = jobName;
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

    public Long getExecutionId() {
        return executionId;
    }

    public void setExecutionId(Long executionId) {
        this.executionId = executionId;
    }

    public Long getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(Long instanceId) {
        this.instanceId = instanceId;
    }

    public List<StepInfo> getSteps() {
        return steps;
    }

    public void setSteps(List<StepInfo> steps) {
        this.steps = steps;
    }

    public void appendStep(StepInfo step) {
        steps.add(step);
    }

    public void prependStep(StepInfo step) {
        steps.add(0, step);
    }

    public void insertStepAt(StepInfo step, int index) {
        steps.add(index, step);
    }

    public String getExitStatus() {
        return exitStatus;
    }

    public void setExitStatus(String exitStatus) {
        this.exitStatus = exitStatus;
    }

    public Date getStartTime() {
        return startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public Date getEndTime() {
        return endTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public boolean isRestartable() {
        return executionId != null && exitStatus != null && (exitStatus.equals("STOPPED") || exitStatus.equals("FAILED"));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JobInstanceInfo jobInstanceInfo = (JobInstanceInfo) o;
        return name.equals(jobInstanceInfo.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return "JobInstanceInfo{" +
                "name='" + name + '\'' +
                ", executionStatus='" + executionStatus + '\'' +
                ", executionId=" + executionId +
                ", instanceId=" + instanceId +
                '}';
    }
}
