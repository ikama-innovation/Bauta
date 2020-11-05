package se.ikama.bauta.core.metadata;

import lombok.Data;

@Data
public class StepMetadata extends SubFlow {

    public static enum StepType {
        /* SQL script(s) */
        SQL,
        PHP,
        /* Scheduled stored procedure */
        SP,
        REPORT,
        OTHER,
        /* Reader/writer (standard spring batch step) */
        RW,
        ASSERT
    }

    private StepType stepType = StepType.OTHER;

    private String description;

    private SplitMetadata split;
    private FlowMetadata flow;
    private boolean firstInSplit;
    private boolean lastInSplit;

    public String toString() {
        return "StepMetadata("
                + "id="+id
                + ", stepType="+stepType.toString()
                +", description="+description
                +", next=" + nextId
                +", firstInSplit=" + firstInSplit
                +", lastInSplit=" + lastInSplit
                + ", split="+(split != null ? split.getId():"null")
                + ")";
    }
}