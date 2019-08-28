package se.ikama.bauta.core;

public interface JobEventListener {
    public void onJobChange(JobInstanceInfo jobInstanceInfo);
}
