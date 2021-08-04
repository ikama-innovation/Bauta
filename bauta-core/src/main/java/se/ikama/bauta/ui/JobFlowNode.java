package se.ikama.bauta.ui;

import lombok.Getter;
import se.ikama.bauta.scheduling.JobTrigger;

import java.time.Duration;
import java.util.Date;

@Getter
public class JobFlowNode {
    private String name;
    private String cron;
    private JobTrigger.TriggerType triggerType;
    private int index;
    private Date startTime;
    private Date endTime;
    private Duration duration;
    private boolean isRoot = true;

    public JobFlowNode(String name, JobTrigger.TriggerType triggerType, int index) {
        this.name = name;
        this.triggerType = triggerType;
        this.index = index;
    }

    public JobFlowNode(String name, JobTrigger.TriggerType triggerType) {
        this.name = name;
        this.triggerType = triggerType;
    }

    public JobFlowNode(String name, String cron, JobTrigger.TriggerType triggerType, int index) {
        this.name = name;
        this.cron = cron;
        this.triggerType = triggerType;
        this.index = index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public void setRoot(boolean value) {
        isRoot = value;
    }
}