package se.ikama.bauta.core;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class JobUpdateSignal {
    private Long jobExecutionId;
    private boolean useCache = false;
}
