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

    public void update() throws Exception{
        graph.clear();
        rootNodes.clear();
        nameToNode.clear();
        if (bautaManager != null) {
            if (!bautaManager.listJobNames().isEmpty()) {
                for (String jobName : bautaManager.listJobNames()) {
                    JobInstanceInfo jobInfo = bautaManager.jobDetails(jobName);
                    JobFlowNode jobFlowNode = new JobFlowNode(jobName, jobInfo.getExecutionStatus());
                    nameToNode.put(jobName, jobFlowNode);
                    if (!jobTriggerDao.getJobCompletionTriggersFor(jobName).isEmpty()) {
                        graph.put(jobFlowNode, new ArrayList<>());
                        for (JobTrigger nextJob : jobTriggerDao.getJobCompletionTriggersFor(jobName)) {
                            graph.get(jobFlowNode).add(nextJob.getJobName());
                            if (!graph.containsKey(nameToNode.get(nextJob.getJobName()))) {
                                graph.put(nameToNode.get(nextJob.getJobName()), new ArrayList<>());
                            }
                        }
                    }
                }
            }
            for (JobFlowNode jobFlowNode : graph.keySet()) {
                for (String triggeredJob : graph.get(jobFlowNode)) {
                    if (nameToNode.containsKey(triggeredJob)) {
                        nameToNode.get(triggeredJob).setRoot(false);
                    }
                }
            }
            graph.keySet().forEach(n -> System.out.println(n.getName()));
            for (JobFlowNode jobFlowNode : graph.keySet()) {
                System.out.println(jobFlowNode == null);
                System.out.println(jobFlowNode.getName());
                if (jobFlowNode.isRoot())
                    rootNodes.add(jobFlowNode);
            }
        }
    }

    public List<JobFlowNode> getRoots() {
        return rootNodes;
    }


    public List<JobFlowNode> getNodes(JobFlowNode node) {
        System.out.println(node.getName());
        List<JobFlowNode> finalList = graph.get(node)
                                            .stream()
                                            .map(jobName -> nameToNode.get(jobName))
                                            .collect(Collectors.toList());

        System.out.println("actual list size = " + finalList.size());
        return finalList;
    }

    public void printGraph() {
        graph.forEach((node, list) -> list.forEach(nextNode -> System.out.println(node.getName() + " --> " + nextNode)));
    }
}
