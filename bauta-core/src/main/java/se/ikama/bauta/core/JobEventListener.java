package se.ikama.bauta.core;

public interface JobEventListener {
    public void onJobChange(JobInstanceInfo jobInstanceInfo);
    public void onStepChange(BasicJobInstanceInfo basicJobInstanceInfo, StepInfo stepInfo);
}
