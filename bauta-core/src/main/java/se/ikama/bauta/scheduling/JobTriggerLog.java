package se.ikama.bauta.scheduling;

import lombok.Data;

import java.util.Date;

@Data
public class JobTriggerLog {
    Date tstamp;
    String status;
    JobTrigger.TriggerType triggerType;
    String triggeringJobName;
    String jobName;
    String cron;
    String errorMsg;
}
