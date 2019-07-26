package ikama.batchc3.core;

public interface JobEventListener {
    public void onJobChange(JobInstanceInfo jobInstanceInfo);
}
