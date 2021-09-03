package se.ikama.bauta.ui;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableLong;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Pre;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.dom.Element;

import se.ikama.bauta.core.JobInstanceInfo;
import se.ikama.bauta.core.ReadWriteInfo;
import se.ikama.bauta.core.StepInfo;

@Tag("div")
public class StepFlow extends Component {
    private static final long serialVersionUID = 1L;

    HashMap<String, Element> stepToElement = new HashMap<>();

    String jobName = "";

    public void init(JobInstanceInfo job) {
        getElement().setAttribute("class", "step-flow");
        String currentSplit = null;
        String currentFlow = null;
        LinkedHashMap<String, MutableLong> flowDurations = new LinkedHashMap<>();
        // Which flows runs within which splits?
        LinkedHashMap<String, Set<String>> splitToFlows = new LinkedHashMap<>();
        LinkedHashMap<String, Long> splitDurations = new LinkedHashMap<>();
        LinkedHashMap<String, String> flowAlias = new LinkedHashMap<>();
        LinkedHashMap<String, Element> splitToElement = new LinkedHashMap<>();
        LinkedHashMap<String, Element> flowToElement = new LinkedHashMap<>();
        int flowCount = 0;
        // First round, calculate flow durations
        this.jobName = job.getName();
        for (StepInfo step : job.getSteps()) {
            if (step.getFlowId() != null) {
                if (currentFlow == null || !step.getFlowId().equals(currentFlow)) {
                    currentFlow = step.getFlowId();
                    flowDurations.put(currentFlow, new MutableLong(0));
                    flowCount++;
                    flowAlias.put(currentFlow, "flow-"+flowCount);
                    splitToFlows.putIfAbsent(step.getSplitId(), new HashSet<>());
                    splitToFlows.get(step.getSplitId()).add(step.getFlowId());
                }
                if (flowDurations.containsKey(currentFlow)) {
                    flowDurations.get(currentFlow).add(step.getDuration());
                }
            } else {
                currentFlow = null;
            }
        }
        // Calculate Split durations by calculating max of its flow durations
        for (StepInfo step : job.getSteps()) {
            if (step.getSplitId() != null) {
                if (currentSplit == null || !step.getSplitId().equals(currentSplit)) {
                    currentSplit = step.getSplitId();
                    Set<String> flowIds = splitToFlows.get(step.getSplitId());
                    if (flowIds != null) {
                        long maxFlowDuration = 0;
                        for (String flowId : flowIds) {
                            maxFlowDuration = Math.max(maxFlowDuration, flowDurations.get(flowId).longValue());
                        }
                        splitDurations.put(currentSplit, maxFlowDuration);
                    }
                }
            } else {
                currentSplit = null;
            }
        }
        // Calculate total duration: sum of splits + steps that are not part of a split
        Long totalDuration = splitDurations.values().stream().collect(Collectors.summingLong(Long::longValue));
        for (StepInfo step : job.getSteps()) {
            if (step.getSplitId() == null) {
                totalDuration += step.getDuration();
            }
        }
        for (String split : splitToFlows.keySet()) {
            Element splitContainer = new Element("div");
            splitContainer.setAttribute("class", "step-flow-split");
            splitToElement.put(split, splitContainer);
            for (String flow : splitToFlows.get(split)) {
                Element flowContainer = new Element("div");
                flowContainer.setAttribute("class","step-flow-flow");
                flowToElement.put(flow, flowContainer);
                splitContainer.appendChild(flowContainer);
            }
        }

        HashSet<String> addedSplits = new HashSet<>();
        for (StepInfo step : job.getSteps()) {
            Element stepElement = new Element("div");
            stepElement.setAttribute("class", "step-flow-step batch_status");
            stepElement.setAttribute("title", step.getName());
            stepToElement.put(step.getName(), stepElement);
            update(step, stepElement);
            // Add the splits at correct position in the flow
            if (step.getSplitId() != null) {
                if (!addedSplits.contains(step.getSplitId())) {
                    addedSplits.add(step.getSplitId());
                    getElement().appendChild(splitToElement.get(step.getSplitId()));
                }
            }
            if (step.getFlowId() != null) {
                Element flowContainer = flowToElement.get(step.getFlowId());
                flowContainer.appendChild(stepElement);
            }
            else {
                getElement().appendChild(stepElement);
            }
        }
    }

    public void update(StepInfo step) {
        Element  stepElement = stepToElement.get(step.getName());
        update(step, stepElement);
    }
    public void update(Collection<StepInfo> steps) {
        for(StepInfo step: steps) {
            update(step);
        }
    }
    private void update(StepInfo step, Element stepElement) {
        //System.out.println("job: "+this.jobName);
        stepElement.setAttribute("data-status", step.getExecutionStatus());
        StringBuilder title = new StringBuilder();
        title.append(step.getName());
        title.append(", start time: ").append((step.getStartTime() != null ? DateFormatUtils.format(step.getStartTime(), "yyMMdd HH:mm:ss", Locale.US) : ""));
        title.append(", duration: " ).append(DurationFormatUtils.formatDuration(step.getDuration(), "HH:mm:ss"));
        if (step.getScriptFiles() != null && step.getScriptFiles().size() > 0) title.append(", scriptFiles: ").append(StringUtils.join(step.getScriptFiles()));
        if (step.getScriptParameters() != null && step.getScriptParameters().size() > 0) title.append(", scriptParameters: ").append(StringUtils.join(step.getScriptParameters()));
        if (step.getAction() != null) title.append(", action: ").append(step.getAction());
        
        stepElement.setProperty("title", title.toString());
        
        stepElement.removeAllChildren();
        String stepName = step.getName();
        if (step.getType() != null && !"OTHER".equals(step.getType())) {
            stepName += " ("+step.getType()+")";
        }
        stepElement.appendChild(Element.createText(stepName));

        if (step.getReportUrls() != null || StringUtils.isNotEmpty(step.getExitDescription())) {
            stepElement.setText(stepName);
        }
        if (step.getReportUrls() != null) {
            for (String url : step.getReportUrls()) {
                Icon icon = null;

                if (url.endsWith(".html")) {
                    icon = VaadinIcon.CHART.create();
                } else if (url.endsWith(".log")) {
                    icon = VaadinIcon.FILE_PROCESS.create();
                } else if (url.toUpperCase().endsWith(".CSV") || url.toUpperCase().endsWith(".XLSX")) {
                    icon = VaadinIcon.FILE_TABLE.create();
                } else {
                    icon = VaadinIcon.FILE_O.create();
                }
                icon.setSize("1.2em");
                Anchor reportAnchor = new Anchor("../" + url, icon);

                reportAnchor.setTarget("reports");
                reportAnchor.getStyle().set("font-size", "0.8em").set("margin-left", "5px").set("color","#eeeeee");
                stepElement.appendChild(reportAnchor.getElement());
            }
        }
        if (step.getExitDescription() != null && step.getExitDescription().length() > 0) {
            Icon icon = VaadinIcon.EXCLAMATION_CIRCLE.create();
            Button descriptionButton = new Button(icon, clickEvent -> {
                Dialog infoDialog = new Dialog();
                VerticalLayout infoLayout = new VerticalLayout();
                infoLayout.setMargin(false);
                infoLayout.setPadding(false);
                infoDialog.setCloseOnEsc(true);
                Pre l = new Pre(step.getExitDescription());
                l.getStyle().set("font-size", "0.6em").set("font-family", "monospace").set("background-color","inherit");
                l.setWidthFull();
                //l.setHeightFull();
                infoLayout.add(new H3(step.getName()));
                infoLayout.add(l);
                infoDialog.add(infoLayout);
                infoDialog.setWidth("800px");
                infoDialog.setHeight("300px");
                infoDialog.open();
            });
            //icon.setSize("1.2em");

            descriptionButton.setIcon(icon);
            descriptionButton.getStyle().set("font-size", "0.8em").set("color","#eeeeee").set("margin-left","5px");
            descriptionButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
            stepElement.appendChild(descriptionButton.getElement());

        }
        if (step.getReadWriteInfo() != null) {
            ReadWriteInfo rw = step.getReadWriteInfo();
            Span span  = new Span("  R/W/S: " + rw.toRWSString());
            span.getStyle().set("margin-left","5px");
            stepElement.appendChild(span.getElement());
        }
    }

}
