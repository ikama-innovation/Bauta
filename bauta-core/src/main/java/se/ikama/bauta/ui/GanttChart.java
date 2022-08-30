package se.ikama.bauta.ui;

import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dependency.NpmPackage;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import elemental.json.Json;
import elemental.json.JsonArray;
import elemental.json.JsonObject;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Component;
import se.ikama.bauta.core.BautaManager;
import se.ikama.bauta.core.JobInstanceInfo;
import se.ikama.bauta.core.StepInfo;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Tag("vega-component")
@NpmPackage(value = "vega-lite", version = "5.3.0")
@NpmPackage(value = "vega-embed", version = "6.21.0")
@NpmPackage(value = "vega", version = "5.22.1")
@JsModule("./vega-wrapper.js")

@EnableScheduling
@Secured({ "ROLE_BATCH_VIEW", "ROLE_BATCH_EXECUTE", "ROLE_ADMIN" })  //Redundant? finns i MainView
@DependsOn("bautaManager")
@Component
public class GanttChart extends Div {

    private TextField filter;

    private ComboBox<String> typeSwitch;

    private Button refreshButton;

    private ArrayList<JobInstanceInfo> filteredJobs = new ArrayList<>();

    public List<JobInstanceInfo> jobList;

    private String jobOrStep;

    Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    BautaManager bautaManager;


    /* TODO
    * Live-reload running jobs every x seconds? + UI options (refresh button, sufficient?)
    *
    *
    * setFullHeight setFullWidth makes the graph render in a "corrupt" way.
    * Changing the browser size makes the graph resize in a strange way,
    *   a different solution is needed for setting an absolute size of the graph
    *
    * View schedueling plans - started, running, future jobs in order
    *
    * Bar chart 100% - show step-duration as percentages of job duration
     */


    public GanttChart() {
        HorizontalLayout options = new HorizontalLayout();
        options.setWidthFull();
        options.setId("options");
        options.setSpacing(true);
        options.setPadding(true);
        options.getElement().setAttribute("align-items", "baseline");
        filter = new TextField(event -> {
            renderByJobName();
        });
        filter.setPlaceholder("Job filter");
        filter.setLabel("Name:");
        typeSwitch = new ComboBox<>();
        typeSwitch.setLabel("Job or step");
        typeSwitch.setItems("Job", "Step");
        typeSwitch.setClearButtonVisible(true);
        typeSwitch.addValueChangeListener(event -> {
            if (event.getValue() == null) {
                jobOrStep = "Job";
            } else {
                if (event.getValue().equals("Job")) {
                    jobOrStep = "Job";
                    renderGraph();
                } else if (event.getValue().equals("Step")) {
                    jobOrStep = "Step";
                    renderGraph();
                }
            }
        });

        refreshButton = new Button("", clickEvent -> {
            renderGraph();
        });
        refreshButton.setIcon(VaadinIcon.ROTATE_RIGHT.create());
        refreshButton.getElement().setProperty("title", "Reload the graph with new data.");


        VerticalLayout container = new VerticalLayout();
        container.setWidthFull();
        container.setHeightFull();
        Div graph = new Div();
        //graph.setHeightFull();
        //graph.setWidthFull();
        graph.setMaxWidth("1900px");
        graph.setMinWidth("1900px");
        graph.setMaxHeight("1000px");
        graph.setMinHeight("1000px");
        graph.setId("vega");
        graph.getStyle().set("padding", "20px");

        jobOrStep = "Job";
        options.setVerticalComponentAlignment(FlexComponent.Alignment.BASELINE, filter, typeSwitch, refreshButton);
        options.add(filter);
        options.add(typeSwitch);
        options.add(refreshButton);
        container.add(options);
        container.add(graph);
        add(container);
    }

    public void renderGraph() {
        Date startTime = DateUtils.addYears(new Date(), -2);
        Date endTime = new Date();
        Date earliestJobStart = new Date();
        Date latestJobEnd = DateUtils.addYears(new Date(), -2);
        JsonArray jsonArr = Json.createArray();
        try {
            jobList = bautaManager.jobDetails();
            if (jobOrStep.equals("Job")) {

                if (filteredJobs.size() == 0) {
                    //jobList.sort(sortByStatus);
                    jsonArr = convertJobsToJson(new ArrayList<JobInstanceInfo>(jobList));
                    earliestJobStart = getEarliestStartFromJobs(jobList);
                    latestJobEnd = getLatestEndFromJobs(jobList);
                } else {
                    jsonArr = convertJobsToJson(filteredJobs);
                    earliestJobStart = getEarliestStartFromJobs(filteredJobs);
                    latestJobEnd = getLatestEndFromJobs(filteredJobs);
                }
            } else if (jobOrStep.equals("Step")) {
                ArrayList<StepInfo> stepList = new ArrayList<>();

                if (filteredJobs.size() > 0) {
                    for (JobInstanceInfo jib : filteredJobs) {
                        stepList.addAll(jib.getSteps());
                    }
                    jsonArr = convertStepsToJson(stepList);
                    earliestJobStart = getEarliestStartFromSteps(stepList);
                    latestJobEnd = getLatestEndFromSteps(stepList);
                } else {
                    for (JobInstanceInfo jib : jobList) {
                        stepList.addAll(jib.getSteps());
                    }
                    jsonArr = convertStepsToJson(stepList);
                    earliestJobStart = getEarliestStartFromSteps(stepList);
                    latestJobEnd = getLatestEndFromSteps(stepList);
                }
            }
        }catch (Exception e) {
            log.error("RenderGraph: ", e);
        }
        if (earliestJobStart.after(startTime)) {
            startTime = DateUtils.addHours(earliestJobStart, -1);
        }
        if (latestJobEnd.before(endTime)) {
            endTime = latestJobEnd;
        }
        String datePattern = "";
        long startEndDifference = TimeUnit.MINUTES.convert(Math.abs(endTime.getTime() - startTime.getTime()), TimeUnit.MILLISECONDS);
        if (startEndDifference >= 2880) {
            datePattern = "%-d/%-m/%-y";
        } else if (startEndDifference >= 1440) {
            datePattern = "%-d/%-m %H:%M   ";
        } else {
            datePattern = "%H:%M";
        }
        this.getElement().executeJs("renderGraph($0, $1, $2, $3, $4)", jsonArr, startTime.toLocaleString(), endTime.toLocaleString(), jobOrStep, datePattern);
    }

    private JsonArray convertStepsToJson(ArrayList<StepInfo> stepList) {
        JsonArray jsonArr = Json.createArray();
        ArrayList<JsonObject> tempList = new ArrayList<>();
        for (StepInfo stepInfo : stepList) {
            log.info(stepInfo.toString());
            if (stepInfo.getExecutionStatus().equals("STARTED")) {
                stepInfo.setStartTime(stepInfo.getStartTime());
                stepInfo.setEndTime(new Date());
            }
            JsonObject base = extractJsonFromStep(stepInfo);
            tempList.add(base);
        }
        for (int j = 0; j < tempList.size(); j++) {
            jsonArr.set(j, tempList.get(j));
        }
        return jsonArr;
    }

    private JsonArray convertJobsToJson(ArrayList<JobInstanceInfo> jobList) {
        JsonArray jsonArr = Json.createArray();
        for (int i = 0; i < jobList.size(); i++) {
            JobInstanceInfo job = jobList.get(i);
            if (!job.getExecutionStatus().equals("UNKNOWN")) {
                if (job.getExecutionStatus().equals("STARTED")) {
                    job.setEndTime(new Date());
                }
                JsonObject base = extractJsonFromJob(job);
                jsonArr.set(i, base);
            }
        }
        return jsonArr;
    }


    public void renderByJobName() {
        filteredJobs.clear();
        ArrayList<JobInstanceInfo> newJobs = new ArrayList<>();
        String input = filter.getValue();
        jobList.forEach(jobInstanceInfo ->  {
            if (jobInstanceInfo.getName().toLowerCase().contains(input.toLowerCase())) {
                newJobs.add(jobInstanceInfo);
            }
        });
        if (newJobs.size() > 0) {
            filteredJobs = newJobs;
            renderGraph();
        } else {
            UIUtils.showErrorMessage("No jobs could be found with input \""+input+"\".");
        }
    }

    public JsonObject extractJsonFromStep(StepInfo step) {
        JsonObject base = Json.createObject();
        if (!step.getExecutionStatus().equals("UNKNOWN")) {
            base.put("Name", step.getName());
            base.put("Start", step.getStartTime().toLocaleString());
            base.put("End", step.getEndTime().toLocaleString());
            base.put("Status", step.getExecutionStatus());
            base.put("Duration", DurationFormatUtils.formatDuration(step.getDuration(), "HH:mm:ss"));
        }
        return base;
    }

    public JsonObject extractJsonFromJob(JobInstanceInfo job) {
        JsonObject base = Json.createObject();
        base.put("Name", job.getName());
        base.put("Start", job.getStartTime().toLocaleString());
        base.put("End", job.getEndTime().toLocaleString());
        base.put("Status", job.getExecutionStatus());
        base.put("ExecutionId", job.getLatestExecutionId());
        base.put("InstanceId", job.getInstanceId());
        base.put("Duration", DurationFormatUtils.formatDuration(job.getDuration(), "HH:mm:ss"));
        return base;
    }

    public Date getEarliestStartFromJobs(List<JobInstanceInfo> jobList) {
        Date earliestJobStart = new Date();
        for (JobInstanceInfo job : jobList) {
            if (job.getStartTime().before(earliestJobStart)) {
                earliestJobStart = job.getStartTime();
            }
        }
        return earliestJobStart;
    }


    public Date getLatestEndFromJobs(List<JobInstanceInfo> jobList) {
        Date latestJobEnd = DateUtils.addYears(new Date(), -2);
        for (JobInstanceInfo job : jobList) {
            if (job.getEndTime().after(latestJobEnd)) {
                latestJobEnd = job.getEndTime();
            }
        }
        return latestJobEnd;
    }

    public Date getEarliestStartFromSteps(List<StepInfo> stepList) {
        Date earliestJobStart = new Date();
        for(StepInfo step : stepList) {
            if (!step.getExecutionStatus().equals("UNKNOWN")) {
                if (step.getStartTime().before(earliestJobStart)) {
                    earliestJobStart = step.getStartTime();
                }
            }
        }
        return earliestJobStart;
    }

    public Date getLatestEndFromSteps(List<StepInfo> stepList) {
        Date latestJobEnd = DateUtils.addYears(new Date(), -2);
        for(StepInfo step : stepList) {
            if (!step.getExecutionStatus().equals("UNKNOWN")) {
                if (step.getEndTime().after(latestJobEnd)) {
                    latestJobEnd = step.getEndTime();
                }
            }
        }
        return latestJobEnd;
    }

    public List<JobInstanceInfo> getJobList() {
        return jobList;
    }

    public void setJobList(List<JobInstanceInfo> jobList) {
        this.jobList = jobList;
    }

    public void setBautaManager(BautaManager bautaManager) {
        this.bautaManager = bautaManager;
    }

}

