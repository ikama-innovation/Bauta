package se.ikama.bauta.core;

import lombok.Getter;
import lombok.Setter;

import java.util.*;

@Getter
@Setter
public class JobInstanceInfo extends BasicJobInstanceInfo {

    private LinkedHashMap<String, StepInfo> steps = new LinkedHashMap<>();

    public void updateCount() {
        completedCount = 0;
        runningCount = 0;
        unknownCount = 0;
        stoppedCount = 0;
        failedCount = 0;
        for (StepInfo s : steps.values()) {
            updateCount(s);
        }
    }
    private void updateCount(StepInfo s) {

        if (s.isCompleted()) completedCount++;
        else if (s.isFailed()) failedCount++;
        else if (s.isStopped()) stoppedCount++;
        else if (s.isRunning()) runningCount++;
        else if (s.isUnknown()) unknownCount++;
    }
    public JobInstanceInfo(String name) {
        super(name);
    }

    public void appendStep(StepInfo step) {
        steps.put(step.getName(), step);
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
                ", executionStatus='" + getExecutionStatus() + '\'' +
                ", executionId=" + getLatestExecutionId() +
                ", instanceId=" + getInstanceId() +
                '}';
    }

    public Collection<StepInfo> getSteps() {
        return steps.values();
    }

    public StepInfo getStep(String stepName) {
        return steps.get(stepName);
    }
}
