package se.ikama.bauta.core.metadata;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Metadata representation of the entire "Job flow".
 */
@Data
public class FlowMetadata {
    private List<StepMetadata> steps = new ArrayList<>();
    private SplitMetadata split;
    public void addStep(StepMetadata step) {
        steps.add(step);
    }
    public String toString() {
        return "FlowMetadata("
                + "split="+(split != null ? split.getId():"null")
                + ", steps="+ StringUtils.join(steps)
                + ")";
    }
}
