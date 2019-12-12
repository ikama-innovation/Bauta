package se.ikama.bauta.scheduling;

import lombok.Data;

@Data
public class JobTrigger {
    public enum TriggerType {CRON, JOB_COMPLETION}
    Long id;
    TriggerType triggerType;
    String triggeringJobName;
    String jobName;
    String cron;
    String jobParameters;
}
