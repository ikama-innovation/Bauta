package se.ikama.bauta.core.metadata;

import lombok.Data;

import java.util.List;

@Data
public class StepMetadata extends SubFlow {

    public static enum StepType {
        /* SQL script(s) */
        SQL,
        PHP,
        JS,
        PY,
        KTS,
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
    // For script-executing steps, e.g. SQL, PHP, JS etc, this property holds the list of scripts to execute in this step
    private List<String> scripts;
    // For script-executing steps, e.g. SQL, PHP, JS etc, this property holds the parameters that are passed to the script(s)
    private List<String> scriptParameters;
    // For the SCH type, the action property holds the actual SQL to execute as a scheduled job
    private String action;
    private SplitMetadata split;
    private FlowMetadata flow;
    private boolean firstInSplit;
    private boolean lastInSplit;

    public String toString() {
        return "StepMetadata("
                + "id="+id
                + ", stepType="+stepType.toString()
                +", scripts="+ (scripts != null ? scripts.toString():"null")
                +", scriptParameters=" + (scriptParameters != null ? scriptParameters.toString() :"null")
                +", description="+description
                +", next=" + nextId
                +", firstInSplit=" + firstInSplit
                +", lastInSplit=" + lastInSplit
                + ", split="+(split != null ? split.getId():"null")
                + ")";
    }
}