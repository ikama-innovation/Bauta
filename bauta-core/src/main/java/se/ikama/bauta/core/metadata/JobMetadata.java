package se.ikama.bauta.core.metadata;

import lombok.Data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;

@Data
public class JobMetadata {
    
    private String name;
    private String description;
    private final LinkedHashMap<String, StepMetadata> allSteps = new LinkedHashMap<>();
    private final List<SubFlow> flow = new ArrayList<>();


    public Collection<StepMetadata> getAllSteps() {
        return allSteps.values();
    }
    public void addToFlow(SubFlow f) {
        flow.add(f);
    }
    public List<SubFlow> getFlow() {
        return this.flow;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        String lf = System.getProperty("line.separator");
        sb.append("Job ").append(name).append("(").append(description).append(")").append(lf);
        for (SubFlow f : flow) {
            sb.append("   ").append(f.toString()).append(lf);
        }
        return sb.toString();
    }

    public String toTreeString() {
        StringBuilder sb = new StringBuilder();
        String lf = System.getProperty("line.separator");
        sb.append("step ").append(name).append(lf);
        for (SubFlow f : flow) {
            if (f instanceof StepMetadata) {
                StepMetadata step = (StepMetadata)f;
                sb.append("step ").append(step.getId()).append(lf);
            }
            else if (f instanceof SplitMetadata) {
                SplitMetadata split = (SplitMetadata)f;
                sb.append("split ").append(split.getId()).append("{").append(lf);
                for (FlowMetadata flow : split.getFlows()) {
                    sb.append("  flow ").append(flow.getId()).append(" {").append(lf);
                    for (StepMetadata step : flow.getSteps()) {
                        sb.append("    ").append(step.getId()).append(lf);
                    }
                    sb.append("  }").append(lf);
                }
                sb.append("}").append(lf);
            }
        }
        return sb.toString();
    }

    void addToAllSteps(StepMetadata step) {
        allSteps.put(step.getId(), step);
    }

    public StepMetadata getStepMetadata(String stepId) {
        return allSteps.get(stepId);
    }
    
}
