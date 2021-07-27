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
            if (bautaManager.listJobNames() != null) {
                for (String jobName : bautaManager.listJobNames()) {
                    JobInstanceInfo jobInfo = bautaManager.jobDetails(jobName);
                    JobFlowNode jobFlowNode = new JobFlowNode(jobName, jobInfo.getExecutionStatus());
                    if (!jobTriggerDao.getJobCompletionTriggersFor(jobName).isEmpty()) {
                        graph.put(jobFlowNode, new ArrayList<>());
                        nameToNode.put(jobName, jobFlowNode);
                        for (JobTrigger nextJob : jobTriggerDao.getJobCompletionTriggersFor(jobName)) {
                            graph.get(jobFlowNode).add(nextJob.getJobName());
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
            for (JobFlowNode jobFlowNode : graph.keySet()) {
                if (jobFlowNode.isRoot()) rootNodes.add(jobFlowNode);
            }
        }
    }

    public List<JobFlowNode> getRoots() {
        return rootNodes;
    }


    public List<JobFlowNode> getNodes(JobFlowNode node) {
        List<JobFlowNode> list = new ArrayList<>();
        List<String> templist = graph.get(node);
        templist.forEach(name -> {
            if (nameToNode.containsKey(name)) {
                list.add(nameToNode.get(name));
            }
        });
        return list;
    }

    public void printGraph() {
        graph.forEach((node, list) -> list.forEach(nextNode -> System.out.println(node.getName() + " --> " + nextNode)));
    }
}
