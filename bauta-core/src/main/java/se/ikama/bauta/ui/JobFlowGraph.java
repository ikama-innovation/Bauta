package se.ikama.bauta.ui;

import com.vaadin.flow.spring.annotation.UIScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.batch.BatchProperties;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
import se.ikama.bauta.core.BautaManager;
import se.ikama.bauta.core.JobInstanceInfo;
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

    private Map<JobFlowNode, List<String>> graph;
    private List<JobFlowNode> rootNodes;
    private Map<String, JobFlowNode> nameToNode;

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
                    System.out.println("current node: " + jobName);
                    JobFlowNode jobFlowNode = new JobFlowNode(jobName, bautaManager.jobDetails(jobName).getExecutionStatus());
                    graph.put(jobFlowNode, new ArrayList<>());
                    nameToNode.put(jobName, jobFlowNode);
                    System.out.println("Trigger type: "+ jobTrigger.getTriggerType().toString());

                    printGraph();
                }
//                for (JobFlowNode jobFlowNode : graph.keySet()) {
//                    if (!(jobFlowNode.getTriggerType() == JobTrigger.TriggerType.CRON)) {
//                        // triggad av annat jobb
//
//
//                    }
//                }
            }
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
    }

    public List<JobFlowNode> getRoots() { return rootNodes; }

    public List<JobFlowNode> getNodesFor(JobFlowNode node) {
//        System.out.println("RootNodes: ");
//        rootNodes.forEach(n -> System.out.print(n.getName() + ", "));
//        System.out.println("\nGraph: ");
//        printGraph();
//        System.out.println("current node: " + node.getName());
        List<JobFlowNode> finalList = graph.get(node)
                                            .stream()
                                            .map(jobName -> nameToNode.get(jobName))
                                            .collect(Collectors.toList());

        //System.out.println("actual list size = " + finalList.size());
        return finalList;
    }

    public void printGraph() {
        for (JobFlowNode jobFlowNode : graph.keySet()) {
            System.out.print(jobFlowNode.getName() + " --> [");
            graph.get(jobFlowNode).forEach(n -> System.out.print(n + ", "));
            System.out.print("]\n");
        }
    }
}
