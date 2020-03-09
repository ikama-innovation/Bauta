package se.ikama.bauta.core.metadata;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SplitMetadata extends SubFlow {

    private List<FlowMetadata> flows = new ArrayList<>();

    public void addFlow(FlowMetadata flow) {
        flows.add(flow);
    }

    public List<FlowMetadata> getFlows() {
        return this.flows;
    }

}
