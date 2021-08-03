package se.ikama.bauta.core;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.ikama.bauta.scheduling.JobTrigger;
import se.ikama.bauta.scheduling.JobTriggerDao;
import se.ikama.bauta.ui.JobFlowNode;

import java.util.*;
import java.util.stream.Collectors;


@Getter
public class JobFlowGraph {

    Logger log = LoggerFactory.getLogger(this.getClass());

    private final Map<JobFlowNode, List<String>> graph;
    private final List<JobFlowNode> rootNodes;
    private final Map<String, JobFlowNode> nameToNode;
    private final List<JobFlowNode> potentialNodes;

    public JobFlowGraph(JobTriggerDao jobTriggerDao) {
        this.graph = new HashMap<>();
        this.rootNodes = new ArrayList<>();
        this.nameToNode = new HashMap<>();
        this.potentialNodes = new ArrayList<>();
        if (jobTriggerDao != null) {
            for (JobTrigger jobTrigger : jobTriggerDao.loadTriggers()) {
                String jobName = jobTrigger.getJobName();
                //cron trigger
                if (jobTrigger.getTriggerType() == JobTrigger.TriggerType.CRON) {
                    JobFlowNode jobFlowNodeCron = new JobFlowNode(jobName, jobTrigger.getCron(), jobTrigger.getTriggerType());
                    graph.put(jobFlowNodeCron, new ArrayList<>());
                    nameToNode.put(jobName, jobFlowNodeCron);
                } else {
                    // triggered by job
                    JobFlowNode jobFlowNode = new JobFlowNode(jobName, jobTrigger.getTriggerType());
                    graph.put(jobFlowNode, new ArrayList<>());
                    nameToNode.put(jobName, jobFlowNode);
                }
                // manual trigger, could be a cron so have to be put in a potential nodes list
                if (!(jobTrigger.getTriggerType() == JobTrigger.TriggerType.CRON)) {
                    String triggerName = jobTrigger.getTriggeringJobName();
                    if (!nameToNode.containsKey(triggerName)) {
                        JobFlowNode jobFlowNode = new JobFlowNode(triggerName, jobTrigger.getTriggerType());
                        potentialNodes.add(jobFlowNode);
                    }
                }
            }
            // checking potential nodes
            for (JobFlowNode jobFlowNode : potentialNodes) {
                if (!nameToNode.containsKey(jobFlowNode.getName())) {
                    nameToNode.put(jobFlowNode.getName(), jobFlowNode);
                    graph.put(jobFlowNode, new ArrayList<>());
                }
            }

            // creating relations between nodes
            for (JobTrigger jobTrigger : jobTriggerDao.loadTriggers()) {
                if (!(jobTrigger.getTriggerType() == JobTrigger.TriggerType.CRON)) {
                    graph.get(nameToNode.get(jobTrigger.getTriggeringJobName())).add(jobTrigger.getJobName());
                }
            }
        }

        // determining which nodes are roots
        for (JobFlowNode jobFlowNode : graph.keySet()) {
            for (String triggeredJob : graph.get(jobFlowNode)) {
                if (nameToNode.containsKey(triggeredJob)) {
                    nameToNode.get(triggeredJob).setRoot(false);
                }
            }
        }

        // adding them to roots
        for (JobFlowNode jobFlowNode : graph.keySet()) {
            if (jobFlowNode.isRoot())
                rootNodes.add(jobFlowNode);
        }
        // sorting root nodes
        Collections.sort(rootNodes, (n1, n2) -> n1.getName().compareToIgnoreCase(n2.getName()));
    }

    public List<JobFlowNode> getNodesFor(JobFlowNode node) {
        return graph.get(node)
                        .stream()
                        .map(nameToNode::get)
                        .collect(Collectors.toList());
    }

    public void printGraph() {
        System.out.println("------------------------------------------------------------------");
        System.out.println("Graph below: ");
        for (JobFlowNode jobFlowNode : graph.keySet()) {
            System.out.println("{name: " + jobFlowNode.getName()+ ", triggerType: " + jobFlowNode.getTriggerType()+ "}");
            System.out.print(jobFlowNode.getName() + " --> [");
            graph.get(jobFlowNode).forEach(n -> System.out.print(n + ", "));
            System.out.print("]\n\n");
        }
        System.out.println("\n");
        System.out.println("Root nodes: ");
        System.out.print("{");
        rootNodes.forEach(n -> System.out.print(n.getName() + ", "));
        System.out.println("}\n\n");
    }
}
