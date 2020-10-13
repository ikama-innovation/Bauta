package se.ikama.bauta.core;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

@Getter
@Setter
public class JobInstanceInfo {

    private String name;
    private String executionStatus = "UNKNOWN";
    private String exitStatus = "UNKNOWN";
    private Long latestExecutionId;
    private Long instanceId;
    private Date startTime;
    private Date endTime;
    private long duration;
    private long latestDuration;
    private Properties jobParameters;
    private List<StepInfo> steps = new ArrayList<>();
    private List<String> requiredJobParamKeys;
    private List<String> optionalJobParamKeys;
    private int executionCount = 0;

    public JobInstanceInfo(String name) {
        this.name = name;
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


    /**
     * Does this job take any job parameters?
     */
    public boolean hasJobParameters() {
        return (optionalJobParamKeys != null && optionalJobParamKeys.size() > 0) || (requiredJobParamKeys != null && requiredJobParamKeys.size() > 0);
    }


    //TODO: More efficient?
    public StepInfo getStep(String stepName) {
        for (StepInfo si : steps) {
            if (StringUtils.equals(stepName, si.getName())) {
                return si;
            }
        }
        return null;
    }
}
