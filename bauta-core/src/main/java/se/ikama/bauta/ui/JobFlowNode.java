package se.ikama.bauta.ui;

import lombok.Getter;
import se.ikama.bauta.scheduling.JobTrigger;


import java.time.Duration;
import java.util.Date;

@Getter
public class JobFlowNode {
    private String name;
    private String status;
    private Date startTime;
    private Date endTime;
    private Duration duration;
    private boolean isRoot = true;

    public JobFlowNode(String name, String status) {
        this.name = name;
        this.status = status;
    }

    public void setRoot(boolean value) {
        isRoot = value;
    }
}