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
    private JobTriggerDao jobTriggerDao;

    private int index;

    public JobFlowGraph(JobTriggerDao jobTriggerDao) {
        this.graph = new HashMap<>();
        this.rootNodes = new ArrayList<>();
        this.nameToNode = new HashMap<>();
        this.potentialNodes = new ArrayList<>();
        this.jobTriggerDao = jobTriggerDao;
        index = 0;
        if (jobTriggerDao != null) {
            for (JobTrigger jobTrigger : jobTriggerDao.loadTriggers()) {
                String jobName = jobTrigger.getJobName();
                //cron trigger
                if (jobTrigger.getTriggerType() == JobTrigger.TriggerType.CRON) {
                    JobFlowNode jobFlowNodeCron = new JobFlowNode(jobName, jobTrigger.getCron(), jobTrigger.getTriggerType(), index);
                    graph.put(jobFlowNodeCron, new ArrayList<>());
                    nameToNode.put(jobName, jobFlowNodeCron);
                    index++;
                } else if (!nameToNode.containsKey(jobName)) {
                    // triggered by job
                    JobFlowNode jobFlowNode = new JobFlowNode(jobName, jobTrigger.getTriggerType(), index);
                    graph.put(jobFlowNode, new ArrayList<>());
                    nameToNode.put(jobName, jobFlowNode);
                    index++;
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
                    jobFlowNode.setIndex(index);
                    nameToNode.put(jobFlowNode.getName(), jobFlowNode);
                    graph.put(jobFlowNode, new ArrayList<>());
                    index++;
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

                nameToNode.get(triggeredJob).setRoot(false);
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

    public void addEdge(JobTrigger jobTrigger) {
        String jobName = jobTrigger.getJobName();
        if (!nameToNode.containsKey(jobName)) {
            JobFlowNode jobFlowNode = new JobFlowNode(jobName, jobTrigger.getTriggerType(), index);
            graph.put(jobFlowNode, new ArrayList<>());
            nameToNode.put(jobName, jobFlowNode);
            index++;
        }
        if (!nameToNode.containsKey(jobTrigger.getTriggeringJobName())) {
            JobFlowNode jobFlowNode1 = new JobFlowNode(jobTrigger.getTriggeringJobName(), jobTrigger.getTriggerType(), index);
            graph.put(jobFlowNode1, new ArrayList<>());
            nameToNode.put(jobTrigger.getTriggeringJobName(), jobFlowNode1);
            index++;
        }
        graph.get(nameToNode.get(jobTrigger.getTriggeringJobName())).add(jobName);

    }

    public boolean containsCycles() {
        // TODO: Implement DFS to search for cycles
        boolean[] visited = new boolean[index];
        boolean[] recStack = new boolean[index];

        for (JobFlowNode node : graph.keySet()) {
            if (containsCyclesUtil(node, visited, recStack)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsCyclesUtil(JobFlowNode node, boolean[] visited, boolean[] recStack) {
        if (recStack[node.getIndex()])
            return true;

        if (visited[node.getIndex()])
            return false;

        visited[node.getIndex()] = true;
        recStack[node.getIndex()] = true;
        List<JobFlowNode> children = getNodesFor(node);

        for (JobFlowNode child : children) {
            if (containsCyclesUtil(child, visited, recStack)) {
                return true;
            }
        }

        recStack[node.getIndex()] = false;
        return false;
    }

    public void printGraph() {
        System.out.println("------------------------------------------------------------------");
        System.out.println("Graph below: ");
        for (JobFlowNode jobFlowNode : graph.keySet()) {
            System.out.println("{name: " + jobFlowNode.getName()+ ", triggerType: "
                    + jobFlowNode.getTriggerType()+ ", index = " + jobFlowNode.getIndex() + "}");
            System.out.print(jobFlowNode.getName() + " --> [");
            graph.get(jobFlowNode).forEach(n -> System.out.print(n + ", "));
            System.out.print("]\n\n");
        }
        System.out.println("\n");
        System.out.println("Root nodes: ");
        System.out.print("{");
        rootNodes.forEach(n -> System.out.print(n.getName() + ", "));
        System.out.print("}");
        System.out.println("\n" + "index = " + index);
        System.out.println("\n\n");
    }
}
