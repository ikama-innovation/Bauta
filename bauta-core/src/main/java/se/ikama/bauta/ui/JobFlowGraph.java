package se.ikama.bauta.ui;

import com.vaadin.flow.spring.annotation.UIScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import se.ikama.bauta.core.BautaManager;
import se.ikama.bauta.scheduling.JobTrigger;
import se.ikama.bauta.scheduling.JobTriggerDao;
import java.util.*;
import java.util.stream.Collectors;


@Component
@UIScope
public class JobFlowGraph {

    Logger log = LoggerFactory.getLogger(this.getClass());
    @Autowired
    BautaManager bautaManager;

    @Autowired
    JobTriggerDao jobTriggerDao;

    private final Map<JobFlowNode, List<String>> graph;
    private final List<JobFlowNode> rootNodes;
    private final Map<String, JobFlowNode> nameToNode;

    public JobFlowGraph() {
        this.graph = new HashMap<>();
        this.rootNodes = new ArrayList<>();
        this.nameToNode = new HashMap<>();
    }

    public void update() throws Exception {
        graph.clear();
        rootNodes.clear();
        nameToNode.clear();
        if (jobTriggerDao != null && bautaManager != null) {
            if (!bautaManager.listJobNames().isEmpty()) {
                for (JobTrigger jobTrigger : jobTriggerDao.loadTriggers()) {
                    String jobName = jobTrigger.getJobName();
                    JobFlowNode jobFlowNode = new JobFlowNode(jobName, bautaManager.jobDetails(jobName).getExecutionStatus());
                    graph.put(jobFlowNode, new ArrayList<>());
                    nameToNode.put(jobName, jobFlowNode);
                    if (!(jobTrigger.getTriggerType() == JobTrigger.TriggerType.CRON))  {
                        String triggerName = jobTrigger.getTriggeringJobName();
                        if (!nameToNode.containsKey(triggerName)) {
                            JobFlowNode jobFlowNode1 = new JobFlowNode(triggerName, bautaManager.jobDetails(triggerName).getExecutionStatus());
                            nameToNode.put(triggerName, jobFlowNode1);
                            graph.put(jobFlowNode1, new ArrayList<>());
                        }
                    }
                }
                for (JobTrigger jobTrigger : jobTriggerDao.loadTriggers()) {
                    if (!(jobTrigger.getTriggerType() == JobTrigger.TriggerType.CRON)) {
                        // triggad av annat jobb
                            graph.get(nameToNode.get(jobTrigger.getTriggeringJobName())).add(jobTrigger.getJobName());
                    }
                }
            }
            printGraph();
        }
        for (JobFlowNode jobFlowNode : graph.keySet()) {
            for (String triggeredJob : graph.get(jobFlowNode)) {
                if (nameToNode.containsKey(triggeredJob)) {
                    nameToNode.get(triggeredJob).setRoot(false);
                }
            }
        }
        for (JobFlowNode jobFlowNode : graph.keySet()) {
//            System.out.println(jobFlowNode == null);
//            System.out.println(jobFlowNode.getName());
            if (jobFlowNode.isRoot())
                rootNodes.add(jobFlowNode);
        }
        Collections.sort(rootNodes, (n1, n2) -> n1.getName().compareToIgnoreCase(n2.getName()));
    }

    public List<JobFlowNode> getRoots() { return rootNodes; }

    public List<JobFlowNode> getNodesFor(JobFlowNode node) {
//        System.out.println("RootNodes: ");
//        rootNodes.forEach(n -> System.out.print(n.getName() + ", "));
//        System.out.println("\nGraph: ");
//        printGraph();
//        System.out.println("current node: " + node.getName());

        //System.out.println("actual list size = " + finalList.size());
        return graph.get(node)
                        .stream()
                        .map(nameToNode::get)
                        .collect(Collectors.toList());
    }

    public void printGraph() {
        System.out.println("Graph below: ");
        for (JobFlowNode jobFlowNode : graph.keySet()) {
            System.out.print(jobFlowNode.getName() + " --> [");
            graph.get(jobFlowNode).forEach(n -> System.out.print(n + ", "));
            System.out.print("]\n");
        }
        System.out.println("\n");
    }
}
