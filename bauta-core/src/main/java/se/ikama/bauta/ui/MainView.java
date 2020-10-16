package se.ikama.bauta.ui;


import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.component.page.Viewport;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PreserveOnRefresh;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import com.vaadin.flow.shared.communication.PushMode;
import com.vaadin.flow.theme.Theme;
import com.vaadin.flow.theme.lumo.Lumo;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableLong;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.launch.JobInstanceAlreadyExistsException;
import org.springframework.batch.core.launch.NoSuchJobException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import se.ikama.bauta.core.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Push(value = PushMode.MANUAL)
@Route("")
@PreserveOnRefresh()
//@Theme(value = Material.class, variant = Material.LIGHT)
@Theme(value = Lumo.class, variant = Lumo.DARK)
// @PWA(name = "Project Base for Vaadin Flow with Spring", shortName = "Project Base")
@CssImport(value = "./styles/job-grid-theme.css", themeFor = "vaadin-grid")
@CssImport(value = "./styles/bauta-styles.css")
@Viewport("width=device-width, minimum-scale=1, initial-scale=1, user-scalable=yes, viewport-fit=cover")
@DependsOn("bautaManager")
public class MainView extends AppLayout implements JobEventListener {

    Logger log = LoggerFactory.getLogger(this.getClass());
    @Autowired
    BautaManager bautaManager;


    //private UI ui;
    Grid<JobInstanceInfo> grid = null;
    Grid<String> serverInfoGrid = null;
    Label buildInfo = null;
    ArrayList<Button> actionButtons = new ArrayList<>();
    Tabs menuTabs = null;

    // Set of all expanded job views. If the job name is present in this set, the corresponding job view should be expanded
    HashSet<String> expandedJobs = new HashSet<>();
    private Div jobGrid;
    private TreeMap<String, StepFlow> jobNameToStepFLow = new TreeMap<>();
    private TreeMap<String, JobButtons> jobNameToJobButtons = new TreeMap<>();
    private TreeMap<String, JobInfo> jobNameToJobInfo = new TreeMap<>();
    private TreeMap<String, StepProgressBar> jobNameToProgressBar = new TreeMap<>();

    public MainView(@Autowired SchedulingView schedulingView) {
        log.debug("Constructing main view. Hashcode: {}", this.hashCode());
        createMainView(schedulingView);

    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        String browser = attachEvent.getSession().getBrowser().getBrowserApplication();
        String address = attachEvent.getSession().getBrowser().getAddress();
        log.debug("Attach {}, {}, {}", this.hashCode(), browser, address);
        try {
            serverInfoGrid.setItems(bautaManager.getServerInfo());
            buildInfo.setText(bautaManager.getShortServerInfo());
            //grid.setItems(bautaManager.jobDetails());
            updateJobGrid(bautaManager.jobDetails());
        } catch (Exception e) {
            log.warn("Failed to fetch job details", e);
            showErrorMessage("Failed to fetch job details");
        }
        bautaManager.registerJobChangeListener(this);
    }

    private void updateJobGrid(List<JobInstanceInfo> jobs) {
        jobGrid.removeAll();
        jobNameToJobButtons.clear();
        jobNameToStepFLow.clear();
        for (JobInstanceInfo job : jobs) {
            String jobName = job.getName();
            Div jobRow = new Div();
            jobRow.addClassNames("job-grid-row");

            Div cell2 = new Div(createStepComponent(job));
            cell2.addClassNames("job-grid-cell","job-grid-steps-cell");
            JobButtons jb = new JobButtons(job, this, bautaManager);
            jobNameToJobButtons.put(jobName, jb);
            Div cell3 = new Div(jb);
            cell3.addClassNames("job-grid-cell");

            Div cell0 = new Div();
            cell0.addClassNames("job-grid-cell");
            cell0.setText(job.getName());
            JobInfo jobInfo = new JobInfo(job);
            jobNameToJobInfo.put(jobName, jobInfo);
            Div cell1 = new Div(jobInfo);
            cell1.addClassNames("job-grid-cell");

            jobRow.add(cell0, cell1, cell2, cell3);
            jobGrid.add(jobRow);
        }
    }


    @Override
    protected void onDetach(DetachEvent detachEvent) {
        log.debug("Detach {}", hashCode());
        bautaManager.unregisterJobChangeListener(this);
        //grid.setItems(Collections.emptyList());
    }

    private void createMainView(SchedulingView schedulingView) {
        log.debug("createMainView");
        Image img = new Image("../static/images/bauta-logo-light.png", "Bauta logo");
        img.setHeight("28px");
        DrawerToggle drawerToggle = new DrawerToggle();
        this.addToNavbar(new DrawerToggle(), img);
        this.setDrawerOpened(false);

        Map<Tab, Component> tabsToPages = new HashMap<>();
        Component jobPage = createJobView();
        jobPage.setVisible(true);
        // Job tab
        Tab jobTab = new Tab("Jobs");
        Component aboutPage = createAboutView();
        aboutPage.setVisible(false);
        // About tab
        Tab aboutTab = new Tab("About");
        // Scheduling
        Tab schedulingTab = new Tab("Scheduling");

        tabsToPages.put(jobTab, jobPage);
        tabsToPages.put(aboutTab, aboutPage);
        tabsToPages.put(schedulingTab, schedulingView);
        schedulingView.setVisible(false);

        Tabs tabs = new Tabs(jobTab, schedulingTab, aboutTab);
        tabs.setSelectedTab(jobTab);
        tabs.setOrientation(Tabs.Orientation.VERTICAL);
        Div pages = new Div(jobPage, schedulingView, aboutPage);
        pages.setHeightFull();
        Set<Component> pagesShown = Stream.of(jobPage)
                .collect(Collectors.toSet());
        tabs.addSelectedChangeListener(event -> {
            pagesShown.forEach(page -> page.setVisible(false));
            pagesShown.clear();
            Component selectedPage = tabsToPages.get(tabs.getSelectedTab());
            selectedPage.setVisible(true);

            pagesShown.add(selectedPage);
        });
        this.addToDrawer(tabs);
        try {
            this.setContent(pages);
        } catch (Exception e) {
            this.setContent(new Label("Failed to create job view: " + e.getMessage()));
        }
        Div rightPanel = new Div();


        rightPanel.getStyle().set("margin-right", "20px");

        Button upgradeInstanceButton = new Button("", clickEvent -> {
            try {
                bautaManager.rebuildServer();
                showInfoMessage("Restarting server. Hold on until it is up and running again..");

            } catch (Exception e) {
                showErrorMessage("Failed to rebuild server: " + e.getMessage());
            }
        });
        upgradeInstanceButton.getElement().setProperty("title", "Upgrades this instance by fetching latest scripts and job definitions from VCS");
        upgradeInstanceButton.setIcon(VaadinIcon.REFRESH.create());
        upgradeInstanceButton.getStyle().set("margin-right", "5px");

        buildInfo = new Label();
        buildInfo.setClassName("build-info");
        // Job report
        Anchor download = new Anchor(new StreamResource("job_report.csv", () -> {
            try {
                return createJobReport();
            }
            catch(Exception e) {
                showErrorMessage("Failed to generate report: " + e.getMessage());
                return null;
            }

        }),"");
        download.getElement().setAttribute("download", true);
        download.add(new Button(new Icon(VaadinIcon.DOWNLOAD_ALT)));
        download.getElement().setProperty("title", "Job report with execution durations");

        rightPanel.add(buildInfo);
        rightPanel.add(download);
        rightPanel.add(upgradeInstanceButton);

        rightPanel.getStyle().set("margin-left", "auto").set("text-alight", "right");

        this.addToNavbar(rightPanel);
        log.debug("createMainView.end");

    }

    private Component createAboutView() {
        VerticalLayout aboutView = new VerticalLayout();
        aboutView.setHeightFull();
        aboutView.setWidthFull();
        serverInfoGrid = new Grid<String>(String.class, false);
        serverInfoGrid.addColumn(item -> item);
        serverInfoGrid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_NO_ROW_BORDERS, GridVariant.LUMO_ROW_STRIPES);
        serverInfoGrid.setHeightFull();
        aboutView.add(serverInfoGrid);
        return aboutView;
    }

    private Component createJobView() {
        jobGrid = new Div();
        jobGrid.addClassNames("job-grid");
        jobGrid.setWidthFull();
        return jobGrid;

    }
    /*
    private Component createJobView() {
        VerticalLayout jobView = new VerticalLayout();
        jobView.setPadding(true);
        jobView.setWidthFull();
        //setDefaultHorizontalComponentAlignment(FlexComponent.Alignment.STRETCH);
        this.grid = new Grid();
        grid.addClassName("jobgrid");
        grid.setId("jobgrid");
        grid.setHeightByRows(true);
        grid.setVerticalScrollingEnabled(false);
        grid.setSelectionMode(Grid.SelectionMode.NONE);
        grid.setDetailsVisibleOnClick(false);
        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER);

        Grid.Column<JobInstanceInfo> c = grid.addColumn(item -> item.getName());
        c.setClassNameGenerator(item -> "job_cell");
        //grid.addColumn(item->createJobInfo(grid, item)).setHeader("Status");
        grid.addColumn(TemplateRenderer.<JobInstanceInfo>of(
                "<div class='job-cell-info'>"+
                        "Instance ID: [[item.instanceId]]<br>" +
                        "ExecutionID: [[item.executionId]]<br>" +
                        "Executions: [[item.executionCount]]<br>" +
                        "Status: <div class='batch_status batch_status_label' data-status$={{item.status}}>[[item.status]]</div><br>" +
                        "Started: [[item.startTime]]<br>" +
                        "Ended: [[item.endTime]]<br>" +
                        "Latest Duration: [[item.latestDuration]]<br>" +
                        "Total Duration: [[item.duration]]<br>" +
                        "Exit status: <div class='batch_status batch_status_label' data-status$={{item.exitStatus}}>[[item.exitStatus]]</div><br>" +
                        "Params: [[item.params]]<br>" +
                        "</div>"
                )
                        .withProperty("executionId", JobInstanceInfo::getLatestExecutionId)
                        .withProperty("instanceId", JobInstanceInfo::getInstanceId)
                        .withProperty("status", JobInstanceInfo::getExecutionStatus)
                        .withProperty("exitStatus", JobInstanceInfo::getExitStatus)
                        .withProperty("executionCount", JobInstanceInfo::getExecutionCount)
                        .withProperty("startTime", ji -> ji.getStartTime() != null ? DateFormatUtils.format(ji.getStartTime(), "yyMMdd HH:mm:ss", Locale.US):"-")
                        .withProperty("endTime", ji -> ji.getEndTime() != null ? DateFormatUtils.format(ji.getEndTime(), "yyMMdd HH:mm:ss", Locale.US) : "-")
                        .withProperty("duration", ji -> ji != null ? DurationFormatUtils.formatDuration(ji.getDuration(), "HH:mm:ss") : "")
                        .withProperty("latestDuration", ji -> ji != null ? DurationFormatUtils.formatDuration(ji.getLatestDuration(), "HH:mm:ss") : "")
                        .withProperty("params", ji -> ji.getJobParameters() != null ? ji.getJobParameters().toString() : "")
        );
        grid.addColumn(TemplateRenderer.<JobInstanceInfo>of(
                "<div class='step-progress-bar' style='width: 100%'>"+
                        "<dom-if if=\"[[item.showCompleted]]\"><template><div class='step-progress-section step-progress-completed' style='flex-grow: [[item.completedCount]]'>[[item.completedCount]]</div></template></dom-if>"+
                        "<dom-if if=\"[[item.showFailed]]\"><template><div class='step-progress-section step-progress-failed' style='flex-grow: [[item.failedCount]]'>[[item.failedCount]]</div></template></dom-if>"+
                        "<dom-if if=\"[[item.showRunning]]\"><template><div class='step-progress-section step-progress-running' style='flex-grow: [[item.runningCount]]'>[[item.runningCount]]</div></template></dom-if>"+
                        "<dom-if if=\"[[item.showStopped]]\"><template><div class='step-progress-section step-progress-stopped' style='flex-grow: [[item.stoppedCount]]'>[[item.stoppedCount]]</div></template></dom-if>"+
                        "<dom-if if=\"[[item.showUnknown]]\"><template><div class='step-progress-section step-progress-unknown' style='flex-grow: [[item.unknownCount]]'>[[item.unknownCount]]</div></template></dom-if>"+
                        "</div><vaadin-button>hej</vaadin-button>"
                )
                .withProperty("completedCount", JobInstanceInfo::getCompletedCount)
                .withProperty("failedCount", JobInstanceInfo::getFailedCount)
                .withProperty("runningCount", JobInstanceInfo::getRunningCount)
                .withProperty("stoppedCount", JobInstanceInfo::getStoppedCount)
                .withProperty("unknownCount", JobInstanceInfo::getUnknownCount)
                .withProperty("showRunning", item ->item.getRunningCount()> 0)
                .withProperty("showFailed", item ->item.getFailedCount()> 0)
                .withProperty("showCompleted", item ->item.getCompletedCount()> 0)
                .withProperty("showStopped", item ->item.getStoppedCount()> 0)
                .withProperty("showUnknown", item ->item.getUnknownCount()> 0)

        );
        grid.addComponentColumn(item -> createStepComponent2(grid, item));
        //grid.addComponentColumn(item -> createStepComponent(grid, item));
        grid.addComponentColumn(item -> createButtons(grid, item));

        jobView.add(grid);
        return jobView;
    }
    */

    private Component createStepComponent(JobInstanceInfo item) {
        StepProgressBar progressBar = new StepProgressBar();
        jobNameToProgressBar.put(item.getName(), progressBar);
        progressBar.update(item);
        Button bExpand = new Button(new Icon(VaadinIcon.ANGLE_DOWN));
        bExpand.addClassName("margin-left");
        StepFlow stepFlow = new StepFlow();
        jobNameToStepFLow.put(item.getName(), stepFlow);
        stepFlow.init(item);
        if (expandedJobs.contains(item.getName())) {
            stepFlow.setVisible(true);
            bExpand.setIcon(new Icon(VaadinIcon.ANGLE_UP));
        }
        else {
            stepFlow.setVisible(false);
            bExpand.setIcon(new Icon(VaadinIcon.ANGLE_DOWN));
        }
        bExpand.addClickListener(e -> {
                    bExpand.setIcon(new Icon(VaadinIcon.ANGLE_UP));
                    if (expandedJobs.remove(item.getName())) {
                        stepFlow.setVisible(false);
                        bExpand.setIcon(new Icon(VaadinIcon.ANGLE_DOWN));
                    }
                    else {
                        expandedJobs.add(item.getName());
                        stepFlow.setVisible(true);
                        bExpand.setIcon(new Icon(VaadinIcon.ANGLE_UP));
                    }
                    // A trick to force the grid to resize
                    // grid.getElement().executeJs("this.notifyResize()");
                }
        );
        FlexLayout barAndButtonLayout = new FlexLayout(progressBar, bExpand);
        barAndButtonLayout.setWidthFull();
        barAndButtonLayout.setFlexDirection(FlexLayout.FlexDirection.ROW);
        barAndButtonLayout.setFlexGrow(1, progressBar);
        barAndButtonLayout.setFlexWrap(FlexLayout.FlexWrap.NOWRAP);
        barAndButtonLayout.setAlignItems(FlexComponent.Alignment.CENTER);
        barAndButtonLayout.getStyle().set("margin-bottom", "8px");
        FlexLayout mainLayout = new FlexLayout(barAndButtonLayout, stepFlow);
        mainLayout.setFlexGrow(1, stepFlow);
        mainLayout.setFlexDirection(FlexLayout.FlexDirection.COLUMN);
        mainLayout.setWidthFull();
        return mainLayout;
    }

    private void doStartJob(JobInstanceInfo item, Map<String, String> params) {
        try {
            bautaManager.startJob(item.getName(), params);
        }
        catch(JobParametersInvalidException e) {
            showErrorMessage(e.getMessage());
        } catch (JobInstanceAlreadyExistsException e) {
            showErrorMessage("This job is already running");
        } catch (NoSuchJobException e) {
            showErrorMessage(e.getMessage());
        }
    }
    /*
    private Component createButtons(Grid<JobInstanceInfo> grid, JobInstanceInfo item) {
        FlexLayout vl = new FlexLayout();
        vl.setFlexDirection(FlexLayout.FlexDirection.COLUMN);
        FlexLayout hl = new FlexLayout();
        Button startButton = new Button("", clickEvent -> {
            if (item.hasJobParameters()) {
                Dialog d = createJobParamsDialog(item);
                d.open();
            }
            else {
                doStartJob(item, null);
            }
        });
        startButton.setIcon(VaadinIcon.PLAY.create());
        startButton.getElement().setProperty("title", "Start a new job instance");

        hl.add(startButton);

        Button stopButton = new Button("", clickEvent -> {
            try {
                bautaManager.stopJob(item.getName());
            }
            catch(Exception e) {
                showErrorMessage(e.getMessage());
            }
        });
        stopButton.setIcon(VaadinIcon.STOP.create());
        stopButton.addClassName("margin-left");
        stopButton.getElement().setProperty("title", "Stop a running job");

        hl.add(stopButton);

        Button restartButton = new Button("", clickEvent -> {
            try {
                bautaManager.restartJob(item.getLatestExecutionId());
            } catch (Exception e) {
                showErrorMessage(e.getMessage());
                log.warn("Failed to restart job", e);
            }
        });
        restartButton.setIcon(VaadinIcon.ROTATE_LEFT.create());
        restartButton.addClassName("margin-left");
        restartButton.getElement().setProperty("title", "Restart a failed or interrupted job. Will pick up where it left off.");

        hl.add(restartButton);

        Button abandonButton = new Button("", clickEvent -> {
            try {
                bautaManager.abandonJob(item.getLatestExecutionId());
            } catch (Exception e) {
                showErrorMessage(e.getMessage());
                log.warn("Failed to abandon job", e);
            }
        });
        abandonButton.getElement().setProperty("title", "Abandons a job. Useful when the process was killed while a job was running and is now stuck in running state.");
        abandonButton.setIcon(VaadinIcon.TRASH.create());

        abandonButton.addClassName("margin-left");
        hl.add(abandonButton);

        Button infoButton = new Button("", clickEvent -> {

            Dialog infoDialog = new Dialog();
            infoDialog.add(createJobHistory(item.getName()));
            infoDialog.setWidth("800px");
            infoDialog.setHeight("300px");
            infoDialog.open();
        });
        infoButton.setIcon(VaadinIcon.BULLETS.create());
        infoButton.addClassName("margin-left");
        hl.add(infoButton);

        
        if ("STARTED".equals(item.getExecutionStatus())) {
            startButton.setEnabled(false);
            restartButton.setEnabled(false);
            stopButton.setEnabled(true);
        } else {
            stopButton.setEnabled(false);
            startButton.setEnabled(true);
            restartButton.setEnabled(item.isRestartable());
        }
        startButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        restartButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        stopButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        abandonButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        infoButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        vl.add(hl);

        if (item.getOptionalJobParamKeys() != null && item.getOptionalJobParamKeys().size() > 0) {
            Label l = new Label("Optional params: " + StringUtils.join(item.getOptionalJobParamKeys(),","));
            l.getStyle().set("font-size", "0.8em");
            vl.add(l);
        }
        if (item.getRequiredJobParamKeys() != null && item.getRequiredJobParamKeys().size() > 0) {
            Label l = new Label("Required params: " + StringUtils.join(item.getRequiredJobParamKeys(),","));
            l.getStyle().set("font-size", "0.8em");
            vl.add(l);
        }
        return vl;

    }

     */

    private InputStream createJobReport() throws Exception {
        StringBuilder sb = new StringBuilder();
        final String EOL = "\n";
        final String SEP = ";";
        final String SEP2 = ";;";
        final String SEP3 = ";;;";
        final String SEP4 = ";;;;";
        final String charset = "utf-8";
        // Job;Split;Flow;Status;Duration
        sb.append("Job").append(SEP).append("Split").append(SEP).append("Flow").append(SEP).append("Status").append(SEP).append("Duration").append(EOL);
        NumberFormat format = new DecimalFormat("####0.#", DecimalFormatSymbols.getInstance(new Locale("sv","SE")));
        List<JobInstanceInfo> jobDetails = bautaManager.jobDetails();
        for (JobInstanceInfo job : jobDetails) {
            String currentSplit = null;
            String currentFlow = null;
            HashMap<String, MutableLong> flowDurations = new HashMap<>();
            // Which flows runs within which splits?
            HashMap<String, Set<String>> splitToFlows = new HashMap<>();
            HashMap<String, Long> splitDurations = new HashMap<>();
            HashMap<String, String> flowAlias = new HashMap<>();
            int flowCount = 0;
            // First round, calculate flow durations
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

            sb.append(SEP4).append(EOL);
            sb.append(job.getName()).append(SEP4)
                    .append(job.getExecutionStatus()).append(SEP)
                    .append(DurationFormatUtils.formatDuration(totalDuration, "HH:mm:ss"))
                    .append(EOL);

            for (StepInfo step : job.getSteps()) {
                if (step.getSplitId() != null && splitDurations.containsKey(step.getSplitId())) {
                    Long duration = splitDurations.remove(step.getSplitId());
                    sb.append(SEP)
                            .append(step.getSplitId()).append(SEP)
                            .append(SEP3)
                            .append(duration != null ? DurationFormatUtils.formatDuration(duration, "HH:mm:ss") : "")
                            .append(EOL);
                }
                if (step.getFlowId() != null && flowDurations.containsKey(step.getFlowId())) {
                    MutableLong duration = flowDurations.remove(step.getFlowId());
                    String alias = flowAlias.get(step.getFlowId());
                    sb.append(SEP)
                            .append(step.getSplitId()).append(SEP)
                            .append(alias).append(SEP)
                            .append(SEP2)
                            .append(duration != null ? DurationFormatUtils.formatDuration(duration.longValue(), "HH:mm:ss") : "")
                            .append(EOL);
                }
                sb.append(SEP)
                        .append(StringUtils.trimToEmpty(step.getSplitId())).append(SEP)
                        .append(step.getFlowId() != null ? flowAlias.get(step.getFlowId()) : "").append(SEP)
                        .append(step.getName()).append(SEP)
                        .append(step.getExecutionStatus()).append(SEP)
                        .append(DurationFormatUtils.formatDuration(step.getDuration(), "HH:mm:ss"))
                        .append(EOL);
            }
        }
        String s = sb.toString();
        try {
            byte[] bytes = s.getBytes(charset);
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            return bais;
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return null;

    }
    /*
    private Component createStepComponent(Grid<JobInstanceInfo> grid, JobInstanceInfo jobInstanceInfo) {
        FlexLayout vl = new FlexLayout();
        vl.setFlexDirection(FlexLayout.FlexDirection.COLUMN);
        vl.setAlignItems(FlexComponent.Alignment.START);

        String currentFlowId = null;
        int flowColor = 0;
        int stepCompletedCount = 0;
        int stepStoppedCount = 0;
        int stepRunningCount = 0;
        int stepFailedCount = 0;
        int stepUnknownCount = 0;
        int stepCount = jobInstanceInfo.getSteps().size();
        for (StepInfo step : jobInstanceInfo.getSteps()) {
            long diff = 0;
            if (jobInstanceInfo.getLatestExecutionId() != null && step.getJobExecutionId() != null) {
                diff = jobInstanceInfo.getLatestExecutionId() - step.getJobExecutionId();
            }

            Component statusLabel = createStatusLabel(step.getExecutionStatus(), diff > 0);
            if (step.isFailed()) stepFailedCount++;
            if (step.isCompleted()) stepCompletedCount++;
            if (step.isRunning()) stepRunningCount++;
            if (step.isUnknown()) stepUnknownCount++;
            if (step.isStopped()) stepStoppedCount++;

            Label stepNameLabel = new Label(step.getName());
            stepNameLabel.addClassName("step-label");
            if (step.getFlowId() != null) {
                if (!step.getFlowId().equals(currentFlowId)) {
                    currentFlowId = step.getFlowId();
                    flowColor = flowColor == 0 ? 1 : 0;
                }
                stepNameLabel.addClassName("flow-"+flowColor);
            }

            Div div = new Div(stepNameLabel, statusLabel);
            div.addClassName("step-row");

            if (step.isFirstInSplit()) {
                div.addClassName("split-first");
            }
            else if (step.isLastInSplit()) {
                div.addClassName("split-last");
            }
            else if (step.getSplitId() != null) {
                div.addClassName("split");
            }

            div.getElement().setProperty("title", "ExecutionId: "+step.getJobExecutionId()
                    +", start time: " + (step.getStartTime() != null ? DateFormatUtils.format(step.getStartTime(), "yyMMdd HH:mm:ss", Locale.US) : "")
                    +", duration: " + DurationFormatUtils.formatDuration(step.getDuration(), "HH:mm:ss")
                    + ", next: " + step.getNextId());


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
                    reportAnchor.getStyle().set("font-size", "0.8em").set("margin-left", "5px");
                    div.add(reportAnchor);
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
                descriptionButton.getStyle().set("font-size", "0.8em");
                descriptionButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_ERROR);
                div.add(descriptionButton);

            }
            vl.add(div);
            vl.setFlexDirection(FlexLayout.FlexDirection.COLUMN);
        }
        Div progressBar = new Div();

        progressBar.addClassName("step-progress-bar");
        //progressBar.getStyle().set("display", "flex").set("flex-wrap","nowrap").set("border-radius","4px").set("overflow","hidden").set("font-size","0.75em");

        if (stepCompletedCount > 0) {
            Div stepsCompleted = new Div();
            //stepsCompleted.getStyle().set("text-align","center").set("color","#eeeeee").set("padding-top","2px").set("padding-bottom","2px").set("background-color","var(--lumo-success-color)");
            stepsCompleted.addClassNames("step-progress-section", "step-progress-completed");
            stepsCompleted.setText("" + stepCompletedCount);
            stepsCompleted.getStyle().set("flex-grow", ""+stepCompletedCount);
            progressBar.add(stepsCompleted);
        }
        if (stepRunningCount > 0) {
            Div stepsRunning = new Div();
            stepsRunning.addClassNames("step-progress-section", "step-progress-running");

            stepsRunning.setText(Integer.toString(stepRunningCount));
            stepsRunning.getStyle().set("flex-grow", ""+stepRunningCount);
            progressBar.add(stepsRunning);
        }
        if (stepFailedCount > 0) {
            Div stepsFailed = new Div();
            stepsFailed.addClassNames("step-progress-section", "step-progress-failed");
            //stepsFailed.getStyle().set("text-align","center").set("color","#eeeeee").set("padding-top","2px").set("padding-bottom","2px").set("background-color","var(--lumo-error-color)");
            stepsFailed.setText(Integer.toString(stepFailedCount));
            stepsFailed.getStyle().set("flex-grow", ""+stepFailedCount);
            progressBar.add(stepsFailed);
        }
        if (stepStoppedCount > 0) {
            Div stepsStopped = new Div();
            //stepsStopped.getStyle().set("text-align","center").set("color","#eeeeee").set("padding-top","2px").set("padding-bottom","2px").set("background-color","var(--lumo-primary-color-50pct)");
            stepsStopped.addClassNames("step-progress-section", "step-progress-stopped");
            stepsStopped.setText("" + stepStoppedCount);
            stepsStopped.getStyle().set("flex-grow", ""+stepStoppedCount);
            progressBar.add(stepsStopped);
        }
        if (stepUnknownCount > 0) {
            Div stepsUnknown = new Div();
            stepsUnknown.addClassNames("step-progress-section", "step-progress-unknown");
            //stepsUnknown.getStyle().set("text-align","center").set("color","#eeeeee").set("padding-top","2px").set("padding-bottom","2px").set("background-color","var(--lumo-contrast-10pct)");
            stepsUnknown.setText("" + stepUnknownCount);
            stepsUnknown.getStyle().set("flex-grow", ""+stepUnknownCount);
            progressBar.add(stepsUnknown);
        }

        progressBar.setWidthFull();

        Button bExpand = new Button(new Icon(VaadinIcon.ANGLE_DOWN));
        bExpand.addClassName("margin-left");
        if (expandedJobs.contains(jobInstanceInfo.getName())) {
            vl.setVisible(true);
            bExpand.setIcon(new Icon(VaadinIcon.ANGLE_UP));
        }
        else {
            vl.setVisible(false);
            bExpand.setIcon(new Icon(VaadinIcon.ANGLE_DOWN));
        }
        FlexLayout hl = new FlexLayout(progressBar, bExpand);
        hl.setWidthFull();
        //hl.setFlexGrow(1, progressBar);
        hl.setFlexDirection(FlexLayout.FlexDirection.ROW);
        hl.setAlignItems(FlexComponent.Alignment.CENTER);
        FlexLayout masterLayout = new FlexLayout(hl, vl);
        masterLayout.setFlexDirection(FlexLayout.FlexDirection.COLUMN);
        bExpand.addClickListener(e -> {
                bExpand.setIcon(new Icon(VaadinIcon.ANGLE_UP));
                if (expandedJobs.remove(jobInstanceInfo.getName())) {
                    vl.setVisible(false);
                    bExpand.setIcon(new Icon(VaadinIcon.ANGLE_DOWN));
                }
                else {
                    expandedJobs.add(jobInstanceInfo.getName());
                    vl.setVisible(true);
                    bExpand.setIcon(new Icon(VaadinIcon.ANGLE_UP));
                }
                // A trick to force the grid to resize
                grid.getElement().executeJs("this.notifyResize()");
        }
        );
        masterLayout.setWidthFull();

        return masterLayout;
    }
    */


    private Component createStatusLabel(String executionStatus, boolean oldExecution) {
        Div statusLabel = new Div();
        statusLabel.addClassNames("batch_status", "batch_status_label");
        if (oldExecution) statusLabel.addClassName("old_execution");
        statusLabel.getElement().setAttribute("data-status",executionStatus);
        statusLabel.setText(executionStatus);
        //statusLabel.getStyle().set("background-color", batchStatusToColor(executionStatus));
        return statusLabel;
    }

    protected Component createJobHistory(String jobName) {
        log.debug("createJobHistory: {}", jobName);
        VerticalLayout vl = new VerticalLayout();
        List<JobInstanceInfo> jobs = null;
        try {
            jobs = bautaManager.jobHistory(jobName);
        } catch (Exception e) {
            showErrorMessage("Failed to fetch job history: " + e.getMessage());
        }
        for (JobInstanceInfo ji : jobs) {
            log.debug("jobInstanceInfo: {}", ji);
            log.debug("Start time is {}", ji.getStartTime());
            log.debug("End time is {}", ji.getEndTime());
            Div div = new Div();
            div.setWidthFull();
            UnorderedList ul = new UnorderedList();

            ListItem li = new ListItem("test");
            ul.add(new ListItem("InstanceId: " + ji.getInstanceId().toString()));
            ul.add(new ListItem("ExecutionId: " + ji.getLatestExecutionId().toString()));
            ul.add(new ListItem("Start/end time: " + DateFormatUtils.format(ji.getStartTime(), "yyMMdd HH:mm:ss", Locale.US) + "/" + (ji.getEndTime() != null ? DateFormatUtils.format(ji.getEndTime(), "yyMMdd HH:mm:ss", Locale.US) : "-")));
            ul.add(new ListItem("Duration: " + DurationFormatUtils.formatDuration(ji.getDuration(), "HH:mm:ss")));
            ul.add(new ListItem("Params: " + ji.getJobParameters()));
            ul.add(new ListItem(new Label("Exit status: "), createStatusLabel(ji.getExitStatus(), false)));
            div.add(ul);
            Grid<StepInfo> grid = new Grid<>();
            grid.setHeightByRows(true);
            grid.setWidthFull();
            grid.addColumn(StepInfo::getName).setHeader("Name").setAutoWidth(true);
            //grid.addColumn(StepInfo::getExecutionStatus).setHeader("Status");

            grid.addComponentColumn(item -> createStatusLabel(item.getExecutionStatus(), false)).setHeader("Status");
            grid.addComponentColumn(item -> new Label(DurationFormatUtils.formatDuration(item.getDuration(), "HH:mm:ss"))).setHeader("Duration");
            grid.addComponentColumn(item -> {
                Div reportDiv = new Div();
                if (item.getReportUrls() != null) {
                    for (String url : item.getReportUrls()) {
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
                        reportAnchor.getStyle().set("font-size", "0.8em").set("margin-left", "5px");
                        reportDiv.add(reportAnchor);
                    }
                }
                return reportDiv;
            });
            grid.setItems(ji.getSteps());
            div.add(grid);
            vl.add(div);

        }
        return vl;
    }
    private Dialog createJobParamsDialog(JobInstanceInfo job) {
        Dialog dialog = new Dialog();
        dialog.setWidth("700");
        HashMap<String, TextField> requiredTextFields = new HashMap<>();
        HashMap<String, TextField> optionalTextFields = new HashMap<>();

        VerticalLayout formLayout = new VerticalLayout();
        formLayout.setWidthFull();

        formLayout.add(new H4("Job Parameters"));
        if (job.getRequiredJobParamKeys() != null) {
            for (String key : job.getRequiredJobParamKeys()) {
                TextField paramField = new TextField();
                paramField.setLabel(key);
                paramField.setRequired(true);
                //paramField.setRequiredIndicatorVisible(true);
                paramField.setWidthFull();
                formLayout.add(paramField);
                requiredTextFields.put(key, paramField);
            }
        }
        if (job.getOptionalJobParamKeys() != null) {
            for (String key : job.getOptionalJobParamKeys()) {
                TextField paramField = new TextField();
                paramField.setRequired(false);
                paramField.setLabel(key);
                paramField.setWidthFull();
                formLayout.add(paramField);
                optionalTextFields.put(key, paramField);
            }
        }

        Button startButton = new Button("Start", clickEvent -> {
            HashMap<String, String> params = new HashMap<>();
            for (Map.Entry<String, TextField> field:requiredTextFields.entrySet()) {
                if (StringUtils.isNotEmpty(field.getValue().getValue())) {
                    params.put(field.getKey(), field.getValue().getValue());
                }
                // TODO: Add early validation of required fields
            }
            for (Map.Entry<String, TextField> field:optionalTextFields.entrySet()) {
                if (StringUtils.isNotEmpty(field.getValue().getValue())) {
                    params.put(field.getKey(), field.getValue().getValue());
                }
            }
            dialog.close();
            doStartJob(job, params);

        });
        startButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancelButton = new Button("Cancel", clickEvent -> {
            dialog.close();
        });
        HorizontalLayout hl = new HorizontalLayout();
        hl.add(startButton);
        hl.add(cancelButton);
        formLayout.add(hl);
        dialog.add(formLayout);

        return dialog;
    }

    @Override
    public void onJobChange(JobInstanceInfo jobInstanceInfo) {
        log.debug("{}, onJobChange {} ", hashCode(), jobInstanceInfo);

        UI ui = this.getUI().get();
        if (ui != null) {
            ui.access(() -> {
                // grid.getDataProvider().refreshItem();
                JobButtons jobButtons = jobNameToJobButtons.get(jobInstanceInfo.getName());
                jobButtons.setJobInstanceInfo(jobInstanceInfo);
                StepFlow stepFlow = jobNameToStepFLow.get(jobInstanceInfo.getName());
                // TODO: Only needed for job start?
                stepFlow.update(jobInstanceInfo.getSteps());
                JobInfo jobInfo = jobNameToJobInfo.get(jobInstanceInfo.getName());
                jobInfo.update(jobInstanceInfo);
                ui.push();
            });
        }
    }

    @Override
    public void onStepChange(BasicJobInstanceInfo basicJobInstanceInfo, StepInfo stepInfo) {
        log.debug("{}, onstepChange {} ", hashCode(), stepInfo);
        UI ui = this.getUI().get();
        if (ui != null) {
            ui.access(() -> {
                StepFlow stepFlow = jobNameToStepFLow.get(basicJobInstanceInfo.getName());
                stepFlow.update(stepInfo);
                StepProgressBar stepProgressBar = jobNameToProgressBar.get(basicJobInstanceInfo.getName());
                stepProgressBar.update(basicJobInstanceInfo);
                ui.push();
            });
        }

    }

    private void showErrorMessage(String message) {
        Label label = new Label(message);
        Notification notification = new Notification(label);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        notification.setPosition(Notification.Position.BOTTOM_START);
        notification.setDuration(10000);
        notification.open();
    }
    private void showInfoMessage(String message) {
        Label label = new Label(message);
        Notification notification = new Notification(label);
        notification.addThemeVariants(NotificationVariant.LUMO_PRIMARY);
        notification.setPosition(Notification.Position.BOTTOM_START);
        notification.setDuration(10000);
        notification.open();
    }

}
