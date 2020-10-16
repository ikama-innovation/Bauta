package se.ikama.bauta.core;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class JobUpdateSignal {
    static enum UpdateType {StepUpdate, JobUpdate};
    private UpdateType updateType;
    private Long jobExecutionId;
    private String stepName;
}
