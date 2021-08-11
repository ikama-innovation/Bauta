package se.ikama.bauta.core.metadata;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Spring Batch API does not expose the meta-data for Flow beans.
 * As a workaround, parse XML configuration files to extract metadata.
 */
@Component
@Slf4j
public class JobMetadataReader {

	@Value("${bauta.jobBeansDir}")
    private String jobBeansDir;
	
	@Value("${bauta.jobBeansDir2}")
    private String jobBeansDir2;
	
	@Value("${bauta.jobBeansDir3}")
    private String jobBeansDir3;

    private final String jobBeanFilenamePattern = "job_(.*)\\.xml";

    @Getter
    private Map<String, JobMetadata> jobMetadata = null;

    @Autowired
    ResourceLoader resourceLoader;


    @PostConstruct
    public void init() {
        try {
            log.info("Collecting metadata from Job defined in Spring bean defintion files..");
            jobMetadata = parseAll();
            log.info("Done. Read {} jobs.", this.getJobMetadata().size());

        } catch (URISyntaxException | IOException e) {
            throw new BeanInitializationException("Failed to parse metadata", e);
        }
    }

    public JobMetadata getMetadata(String jobName) {
        return this.jobMetadata.get(jobName);
    }

    private Map<String, JobMetadata> parseAll() throws URISyntaxException, IOException {
        TreeMap<String, JobMetadata> out = new TreeMap();
        Resource[] resources1 = ResourcePatternUtils
                .getResourcePatternResolver(resourceLoader)
                .getResources("file://"+jobBeansDir + "/*.xml");
        Resource[] resources2 = ResourcePatternUtils
                .getResourcePatternResolver(resourceLoader)
                .getResources("file://"+jobBeansDir2 + "/*.xml");
        Resource[] resources3 = ResourcePatternUtils
                .getResourcePatternResolver(resourceLoader)
                .getResources("file://"+jobBeansDir3 + "/*.xml");
        ArrayList<Resource> resources = new ArrayList<>();
        CollectionUtils.mergeArrayIntoCollection(resources1, resources);
        CollectionUtils.mergeArrayIntoCollection(resources2, resources);
        CollectionUtils.mergeArrayIntoCollection(resources3, resources);
        
        for (Resource r : resources) {
            try (InputStream is = r.getInputStream()) {
                Collection<JobMetadata> jobs = parse(is);
                for (JobMetadata job : jobs) {
                    out.put(job.getName(), job);
                }

            } catch (IOException ex) {
                Logger.getLogger(JobMetadataReader.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        return out;
    }

    private Collection<JobMetadata> parse(InputStream is) {
        ArrayList<JobMetadata> jobs = new ArrayList<>();

        DocumentBuilderFactory builderFactory
                = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = null;
        try {
            builder = builderFactory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new RuntimeException("Failed to setup XML parser", e);
        }
        try {
            Document document = builder.parse(is);
            Element root = document.getDocumentElement();

            NodeList nodes = root.getChildNodes();
            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                if (node instanceof Element) {
                    Element e = (Element) node;
                    if (e.getNodeName().endsWith(":job")) {
                        parseJob(e, jobs);

                    }
                }
            }
        } catch (SAXException | IOException e) {
            throw new RuntimeException(e);
        }

        return jobs;
    }

    private void parseJob(Element e, List<JobMetadata> jobs) {
        JobMetadata job = new JobMetadata();
        job.setName(e.getAttribute("id"));
        if ("true".equalsIgnoreCase(e.getAttribute("abstract"))) {
            log.debug("Skipping abstract job {}", job.getName());
        }
        jobs.add(job);
        NodeList jobNodes = e.getChildNodes();
        for (int j = 0; j < jobNodes.getLength(); j++) {
            Node jobNode = jobNodes.item(j);
            if (jobNode instanceof Element) {
                Element je = (Element) jobNode;
                if (je.getNodeName().endsWith(":description")) {
                    String description = je.getChildNodes().item(0).getTextContent();
                    job.setDescription(description);
                } else if (je.getNodeName().endsWith(":step")) {
                    parseStep(je, job, null);
                } else if (je.getNodeName().endsWith(":split")) {
                    parseSplit(je, job);
                }
            }
        }
        HashMap<String, ArrayList<StepMetadata>> splitIdTosteps = new HashMap<>();
        for (StepMetadata step: job.getAllSteps()) {
            if (step.getSplit() != null) {
                String splitId = step.getSplit().getId();
                log.debug("Handling split {}", splitId);
                if (!splitIdTosteps.containsKey(splitId)) {
                    splitIdTosteps.put(splitId, new ArrayList<StepMetadata>());
                }
                splitIdTosteps.get(splitId).add(step);
            }
        }
        for (ArrayList<StepMetadata> steps : splitIdTosteps.values()) {
            steps.get(0).setFirstInSplit(true);
            steps.get(steps.size()-1).setLastInSplit(true);
        }
        log.debug("Parsed job : " + job.toTreeString());

    }

    private void parseStep(Element e, JobMetadata job, FlowMetadata flow) {
        StepMetadata step = new StepMetadata();

        step.setId(e.getAttribute("id"));
        step.setNextId(e.getAttribute("next"));
        if (flow != null) {
            step.setSplit(flow.getSplit());
            flow.addStep(step);
        } else {
            job.addToFlow(step);
        }
        job.addToAllSteps(step);
        NodeList childNodes = e.getChildNodes();
        for (int j = 0; j < childNodes.getLength(); j++) {
            Node childNode = childNodes.item(j);
            if (childNode instanceof Element) {
                Element je = (Element) childNode;
                if (je.getNodeName().endsWith(":description") && je.getChildNodes().getLength() > 0) {
                    Node descriptionNode = je.getChildNodes().item(0);
                    if (descriptionNode.getNodeType() == Node.TEXT_NODE || descriptionNode.getNodeType() == Node.CDATA_SECTION_NODE) {
                        String description = descriptionNode.getTextContent();
                        step.setDescription(description);
                    }
                } else if (je.getNodeName().endsWith(":tasklet") && je.getChildNodes().getLength() > 0) {
                    parseTasklet(je, step);
                }
            }
        }
    }

    private void parseSplit(Element e, JobMetadata job) {
        SplitMetadata split = new SplitMetadata();
        job.addToFlow(split);
        split.setId(e.getAttribute("id"));
        split.setNextId(e.getAttribute("next"));
        NodeList childNodes = e.getChildNodes();
        for (int j = 0; j < childNodes.getLength(); j++) {
            Node childNode = childNodes.item(j);
            if (childNode instanceof Element) {
                Element je = (Element) childNode;
                if (je.getNodeName().endsWith(":flow")) {
                    parseFlow(je, job, split);
                }
            }
        }
    }

    private void parseFlow(Element e, JobMetadata job, SplitMetadata split) {
        FlowMetadata flow = new FlowMetadata();
        flow.setId(UUID.randomUUID().toString());
        flow.setSplit(split);
        split.addFlow(flow);
        NodeList childNodes = e.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node childNode = childNodes.item(i);
            if (childNode instanceof Element) {
                Element je = (Element) childNode;
                if (je.getNodeName().endsWith(":step")) {
                    parseStep(je, job, flow);
                }
            }
        }
    }

    private void parseTasklet(Element je, StepMetadata step) {
        NodeList childNodes = je.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node childNode = childNodes.item(i);
            if (childNode instanceof Element) {
                Element ce = (Element) childNode;
                if (ce.getNodeName().endsWith("bean")) {
                    String class_ = ce.getAttribute("class");
                    String parent = ce.getAttribute("parent");
                    boolean isScriptTasklet = false;
                    if (class_ != null && class_.length() > 0) {
                        if ("se.ikama.bauta.batch.tasklet.oracle.ScheduledJobTasklet".equals(class_)) {
                            step.setStepType(StepMetadata.StepType.SCH);
                        } else if ("se.ikama.bauta.batch.tasklet.ResourceAssertTasklet".equals(class_)) {
                            step.setStepType(StepMetadata.StepType.ASSERT);
                        } else if ("se.ikama.bauta.batch.tasklet.javascript.JavascriptTasklet".equals(class_)) {
                            step.setStepType(StepMetadata.StepType.JS);
                            isScriptTasklet = true;
                        } else if ("se.ikama.bauta.batch.tasklet.oracle.ScriptTasklet".equals(class_) || "se.ikama.bauta.batch.tasklet.oracle.SqlClTasklet".equals(class_)) {
                            step.setStepType(StepMetadata.StepType.SQL);
                            isScriptTasklet = true;
                        } else if ("se.ikama.bauta.batch.tasklet.php.PhpTasklet".equals(class_)) {
                            step.setStepType(StepMetadata.StepType.PHP);
                            isScriptTasklet = true;
                        } else if (class_.contains("Report")) {
                            step.setStepType(StepMetadata.StepType.REP);
                        } else {
                            log.debug("class: {}", class_);
                            log.debug("parent: {}", parent);
                            step.setStepType(StepMetadata.StepType.OTHER);
                        }
                    } else if (parent != null && parent.length() > 0) {
                	
                        if (StringUtils.containsIgnoreCase(parent, "sql")) {
                            step.setStepType(StepMetadata.StepType.SQL);
                            isScriptTasklet = true;
                        }   
                        else if (StringUtils.containsIgnoreCase(parent, "php")){
                            step.setStepType(StepMetadata.StepType.PHP);
                            isScriptTasklet = true;
                        } 
                        else if (StringUtils.containsIgnoreCase(parent, "py")){
                                step.setStepType(StepMetadata.StepType.PY);
                                isScriptTasklet = true;
                        } 
                        else if (StringUtils.containsIgnoreCase(parent, "js")){
                            step.setStepType(StepMetadata.StepType.JS);
                            isScriptTasklet = true;
                        } else if (StringUtils.containsIgnoreCase(parent, "kts")){
                            step.setStepType(StepMetadata.StepType.KTS);
                            isScriptTasklet = true;
                        }
                    }
                    if (isScriptTasklet) {
                	// Look for scriptFiles and parameters
                	NodeList propertyElements = ce.getElementsByTagName("property");
                	for (int j = 0;j < propertyElements.getLength();j++) {
                	    Element propertyElement = (Element)propertyElements.item(j);
                	    String propertyName = propertyElement.getAttribute("name");
                	    List<String> listItems = parseListItems(propertyElement);
                	    if ("scriptFiles".equals(propertyName)) {
                		step.setScripts(listItems);
                	    }
                	    else if ("scriptParameters".equals(propertyName)) {
                		step.setScriptParameters(listItems);
                	    } 
                	}
                    }
                } else if (ce.getNodeName().endsWith(":chunk")) {
                    step.setStepType(StepMetadata.StepType.RW);
                    return;
                }
            }
        }
    }
    private List<String> parseListItems(Element propertyElement) {
	if (propertyElement.hasChildNodes()) {
	    NodeList listNodes = propertyElement.getElementsByTagName("list");
	    if (listNodes.getLength() == 1) {
		Element listElement = (Element) listNodes.item(0);
		NodeList values = listElement.getElementsByTagName("value");
		if (values.getLength() > 0) {
		    List<String> out = new ArrayList<>();
    		    for (int i = 0; i < values.getLength(); i++) {
    		        Element valueElement = (Element)values.item(i);
    		        out.add(valueElement.getTextContent());
    		    }
    		    return out;
		}
	    }
	}
	return null;
    }

    public static void main(String args[]) {
	JobMetadataReader reader = new JobMetadataReader();
	reader.jobBeansDir = "C:\\projects\\se_mig_scripts\\jobs";
	reader.init();
	//for (Entry<String, JobMetadata> entry : reader.getJobMetadata().entrySet()) {
	//    System.out.println(entry.getValue());
	//}
	System.out.println(reader.getMetadata("Job.400.Setup"));
    }

}


