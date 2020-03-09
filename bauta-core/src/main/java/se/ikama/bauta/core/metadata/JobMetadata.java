package se.ikama.bauta.core.metadata;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class JobMetadata {
    
    private String name;
    private String description;
    private final List<StepMetadata> allSteps = new ArrayList<>();
    private final List<SubFlow> flow = new ArrayList<>();


    public List<StepMetadata> getAllSteps() {
        return allSteps;
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
            sb.append("   "+f.toString()).append(lf);
        }
        return sb.toString();
    }

    void addToAllSteps(StepMetadata step) {
        allSteps.add(step);
    }
    
}
