package se.ikama.bauta.core.metadata;

import lombok.Data;

import java.util.List;

@Data
public class StepMetadata extends SubFlow {

    public static enum StepType {
        /* SQL script(s) */
        SQL,
        PHP,
        /* Scheduled stored procedure */
        SCH,
        REP,
        OTHER,
        /* Reader/writer (standard spring batch step) */
        RW,
        ASSERT
    }

    private StepType stepType = StepType.OTHER;

    private String description;
    private List<String> scripts;
    private List<String> scriptParameters;
    private SplitMetadata split;
    private FlowMetadata flow;
    private boolean firstInSplit;
    private boolean lastInSplit;

    public String toString() {
        return "StepMetadata("
                + "id="+id
                + ", stepType="+stepType.toString()
                +", scripts="+scripts.toString()
                +", scriptParameters="+scriptParameters.toString()
                +", description="+description
                +", next=" + nextId
                +", firstInSplit=" + firstInSplit
                +", lastInSplit=" + lastInSplit
                + ", split="+(split != null ? split.getId():"null")
                + ")";
    }
}