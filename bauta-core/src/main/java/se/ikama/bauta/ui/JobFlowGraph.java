package se.ikama.bauta.ui;

import com.vaadin.flow.spring.annotation.UIScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
import se.ikama.bauta.core.BautaManager;
import se.ikama.bauta.core.JobInstanceInfo;
import se.ikama.bauta.scheduling.JobTrigger;
import se.ikama.bauta.scheduling.JobTriggerDao;
import java.util.*;


@Component
@UIScope
public class JobFlowGraph {

    Logger log = LoggerFactory.getLogger(this.getClass());
    @Autowired
    BautaManager bautaManager;

    @Autowired
    JobTriggerDao jobTriggerDao;

    Map<JobFlowNode, List<String>> graph;
    List<JobFlowNode> rootNodes;
    Map<String, JobFlowNode> nameToNode;

    public JobFlowGraph() {
        this.graph = new HashMap<>();
        this.rootNodes = new ArrayList<>();
        this.nameToNode = new HashMap<>();
    }

    public void update() throws Exception {
        graph.clear();
        rootNodes.clear();
        nameToNode.clear();
        if (bautaManager != null) {
            for (String jobName : bautaManager.listJobNames()) {
                JobInstanceInfo jobInfo = bautaManager.jobDetails(jobName);
                JobFlowNode jobFlowNode = new JobFlowNode(jobName, jobInfo.getExecutionStatus());
                graph.put(jobFlowNode, new ArrayList<>());
                nameToNode.put(jobName, jobFlowNode);
                for (JobTrigger nextJob : jobTriggerDao.getJobCompletionTriggersFor(jobName)) {
                    graph.get(jobFlowNode).add(nextJob.getJobName());
                }
            }
            System.out.println("\n");
            System.out.println("all nodes");
            graph.keySet().stream().map(e -> e.getName()).forEach(System.out::println);

            for (JobFlowNode jobFlowNode : graph.keySet()) {
                for (String triggeredJob : graph.get(jobFlowNode)) {
                    nameToNode.get(triggeredJob).setRoot(false);
                }
            }
            for (JobFlowNode jobFlowNode : graph.keySet()) {
                if (jobFlowNode.isRoot()) rootNodes.add(jobFlowNode);
            }
        }
        System.out.println("root nodes: ");
        rootNodes.forEach(j -> System.out.println(j.getName()));
        graph.keySet().stream().map(j -> j.isRoot()).forEach(System.out::println);
    }

    public void printGraph() {
        System.out.println(graph);
    }
}
