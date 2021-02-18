package se.ikama.bauta.scheduling;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.Data;

@Entity
@Table(name = "SCHEDULING_JOB_TRIGGER")
@Data
public class JobTrigger {
    public enum TriggerType {CRON, JOB_COMPLETION}
    @Id()
    @GeneratedValue()
    Long id;
    
    TriggerType triggerType;
    String triggeringJobName;
    String jobName;
    String cron;
    String jobParameters;
}
