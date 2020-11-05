package se.ikama.bauta.core.metadata;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.stereotype.Component;
import org.thymeleaf.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.annotation.PostConstruct;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Spring Batch API does not expose the meta-data for Flow beans.
 * As a workaround, parse XML configuration files to extract metadata.
 */
@Component
@Slf4j
public class JobMetadataReader {

    @Value("${bauta.jobBeansDir}")
    private String jobBeansDir;

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
            log.info("Done!");

        } catch (URISyntaxException | IOException e) {
            throw new BeanInitializationException("Failed to parse metadata", e);
        }
    }

    public JobMetadata getMetadata(String jobName) {
        return this.jobMetadata.get(jobName);
    }

    private Map<String, JobMetadata> parseAll() throws URISyntaxException, IOException {
        TreeMap<String, JobMetadata> out = new TreeMap();
        Resource[] resources = ResourcePatternUtils
                .getResourcePatternResolver(resourceLoader)
                .getResources("file://"+jobBeansDir + "/*.xml");

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
                    if (class_ != null && class_.length() > 0) {
                        if ("se.ikama.bauta.batch.tasklet.oracle.ScheduledJobTasklet".equals(class_)) {
                            step.setStepType(StepMetadata.StepType.SP);
                            return;
                        } else if ("se.ikama.bauta.batch.tasklet.ResourceAssertTasklet".equals(class_)) {
                            step.setStepType(StepMetadata.StepType.ASSERT);
                            return;
                        } else if (class_.contains("Report")) {
                            step.setStepType(StepMetadata.StepType.REPORT);
                            return;
                        } else {
                            log.info("class: {}", class_);
                            step.setStepType(StepMetadata.StepType.OTHER);
                            return;
                        }
                    } else if (parent != null && parent.length() > 0) {
                        if (StringUtils.containsIgnoreCase(parent, "sql", Locale.US)) {
                            step.setStepType(StepMetadata.StepType.SQL);
                            return;
                        }
                        else if (StringUtils.containsIgnoreCase(parent, "php", Locale.US)) {
                            step.setStepType(StepMetadata.StepType.PHP);
                            return;
                        } else {
                            log.info("parent: {}", parent);
                        }
                    }
                } else if (ce.getNodeName().endsWith(":chunk")) {
                    step.setStepType(StepMetadata.StepType.RW);
                    return;
                }
            }
        }
    }

}


