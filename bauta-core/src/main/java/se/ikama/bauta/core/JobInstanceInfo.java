package se.ikama.bauta.core;

import java.util.*;

public class JobInstanceInfo {

    private String name;
    private String executionStatus;
    private String exitStatus;
    private Long latestExecutionId;
    private Long instanceId;
    private Date startTime;
    private Date endTime;
    private long duration;
    private Properties jobParameters;
    private List<StepInfo> steps = new ArrayList<>();
    private List<String> requiredJobParamKeys;
    private List<String> optionalJobParamKeys;
    private int executionCount;


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

    public Long getLatestExecutionId() {
        return latestExecutionId;
    }

    public void setLatestExecutionId(Long latestExecutionId) {
        this.latestExecutionId = latestExecutionId;
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

    public int getExecutionCount() {
        return this.executionCount;
    }
    public void setExecutionCount(int executionCount) {
        this.executionCount = executionCount;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public boolean isRestartable() {
        return latestExecutionId != null && exitStatus != null && (exitStatus.equals("STOPPED") || exitStatus.equals("FAILED"));
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
                ", executionId=" + latestExecutionId +
                ", instanceId=" + instanceId +
                '}';
    }

    public void setJobParameters(Properties jobParameters) {
        this.jobParameters = jobParameters;
    }

    public Properties getJobParameters() {
        return jobParameters;
    }

    public void setRequiredJobParamKeys(List<String> requiredKeys) {
        this.requiredJobParamKeys = requiredKeys;
    }

    public void setOptionalJobParamKeys(List<String> optionalKeys) {
        this.optionalJobParamKeys = optionalKeys;
    }

    public List<String> getOptionalJobParamKeys() {
        return this.optionalJobParamKeys;
    }
    public List<String> getRequiredJobParamKeys() {
        return this.requiredJobParamKeys;
    }

    /**
     * Does this job take any job parameters?
     */
    public boolean hasJobParameters() {
        return (optionalJobParamKeys != null && optionalJobParamKeys.size() > 0) || (requiredJobParamKeys != null && requiredJobParamKeys.size() > 0);
    }
}
