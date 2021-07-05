package se.ikama.bauta.ui;


import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.shared.ui.Transport;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableLong;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.launch.JobInstanceAlreadyExistsException;
import org.springframework.batch.core.launch.NoSuchJobException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.security.access.annotation.Secured;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Label;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.html.UnorderedList;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.menubar.MenuBar;
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

import se.ikama.bauta.core.BasicJobInstanceInfo;
import se.ikama.bauta.core.BautaManager;
import se.ikama.bauta.core.JobEventListener;
import se.ikama.bauta.core.JobInstanceInfo;
import se.ikama.bauta.core.StepInfo;
import se.ikama.bauta.security.SecurityUtils;

import javax.swing.*;

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
@Secured({"ROLE_BATCH_VIEW", "ROLE_BATCH_EXECUTE", "ROLE_ADMIN"})
public class MainView extends AppLayout implements JobEventListener {

    Logger log = LoggerFactory.getLogger(this.getClass());
    @Autowired
    BautaManager bautaManager;


    //private UI ui;
    Grid<String> serverInfoGrid = null;
    Span buildInfo = null;
    ArrayList<Button> actionButtons = new ArrayList<>();
    Tabs menuTabs = null;
    private MenuItem miUser;
    private Button bUpgradeInstance, bRefreshInstance;

    // Set of all expanded job views. If the job name is present in this set, the corresponding job view should be expanded
    HashSet<String> expandedJobs = new HashSet<>();
    private Div jobGrid;
    private TextField tfJobFilter;
    private String filterValue = "";
    private ComboBox cbFilterOnStatus;
    private ComboBox<String> cbSortingList;
    private ComboBox<String> cbShowTimeList;
    private String sortingValue = "";
    private static Date showJobsFrom = new Date(0);
    private static Date showJobsTo = new Date();
    private TreeMap<String, StepFlow> jobNameToStepFLow = new TreeMap<>();
    private TreeMap<String, JobButtons> jobNameToJobButtons = new TreeMap<>();
    private TreeMap<String, JobInfo> jobNameToJobInfo = new TreeMap<>();
    private TreeMap<String, StepProgressBar> jobNameToProgressBar = new TreeMap<>();
    private TreeMap<String, Div> jobNameToJobRow = new TreeMap<>();
    private HashSet<String> runningJobs = new HashSet<>();

    public MainView(@Autowired SchedulingView schedulingView) {
        log.debug("Constructing main view. Hashcode: {}", this.hashCode());
        createMainView(schedulingView);
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        String browser = attachEvent.getSession().getBrowser().getBrowserApplication();
        String address = attachEvent.getSession().getBrowser().getAddress();
        if (SecurityUtils.isSecurityEnabled()) {
            miUser.setText("" + SecurityUtils.currentUser());
            miUser.addComponentAsFirst(VaadinIcon.USER.create());
            // Must be ADMIN to rebuild/upgrade
            if (bautaManager.isRebuildSupported()) {
                bUpgradeInstance.setEnabled(SecurityUtils.isUserInRole("ADMIN"));
            }
            else {
                bUpgradeInstance.setVisible(false);
            }
            if (bautaManager.isRefreshSupported()) {
                bRefreshInstance.setEnabled(SecurityUtils.isUserInRole("ADMIN"));
            }
            else {
                bRefreshInstance.setVisible(false);
            }
        }
        log.debug("Attach {}, {}, {}", this.hashCode(), browser, address);
        try {
            serverInfoGrid.setItems(bautaManager.getServerInfo());
            buildInfo.setText(bautaManager.getShortServerInfo());
            //grid.setItems(bautaManager.jobDetails());
            updateJobGrid(bautaManager.jobDetails());
            filterJobGrid();
        } catch (Exception e) {
            log.warn("Failed to fetch job details", e);
            showErrorMessage("Failed to fetch job details");
        }
        bautaManager.registerJobChangeListener(this);
    }

    private void sortJobGrid() {
        if (!sortingValue.equalsIgnoreCase("")) {
            List<Component> jobList = new ArrayList<>();
            jobGrid.getChildren().forEach(jobList::add);
            jobGrid.removeAll();

            if (sortingValue.equalsIgnoreCase("Job Name")) {
                jobList.sort(Comparator.comparing(c -> c.getElement().getAttribute("data-job-name").toLowerCase()));
            } else if (sortingValue.equalsIgnoreCase("End Time")) {
                jobList.sort(Comparator.comparing(c -> c.getElement().getAttribute("data-job-endtime")));
                Collections.reverse(jobList);
            } else if (sortingValue.equalsIgnoreCase("Start Time")) {
                jobList.sort(Comparator.comparing(c -> c.getElement().getAttribute("data-job-startTime")));
                Collections.reverse(jobList);
            }
            jobList.forEach(jobGrid::add);
        }
    }

    private void filterJobGrid() {
        jobGrid.getChildren().forEach(component -> {
            String jobName = component.getElement().getAttribute("data-job-name");
            String jobStatus = component.getElement().getAttribute("data-execution-status");
            Long startTime = Long.valueOf(component.getElement().getAttribute("data-job-startTime"));
            Long endTime = Long.valueOf(component.getElement().getAttribute("data-job-endtime"));

            boolean show = (showJobsFrom.getTime() <= startTime && showJobsTo.getTime() >= startTime) ||
                    showJobsFrom.getTime() <= endTime && showJobsTo.getTime() >= endTime ||
                    runningJobs.contains(jobName);

            if (!tfJobFilter.isEmpty() && !StringUtils.containsIgnoreCase(jobName, tfJobFilter.getValue())) show = false;
            if (filterValue.equalsIgnoreCase("Running") && !runningJobs.contains(jobName)) show = false;
            if (filterValue.equalsIgnoreCase("Completed") && !jobStatus.equalsIgnoreCase("Completed")) show = false;
            if (filterValue.equalsIgnoreCase("Failed") && !jobStatus.equalsIgnoreCase("Failed")) show = false;
            if (filterValue.equalsIgnoreCase("Unknown") && !jobStatus.equalsIgnoreCase("Unknown")) show = false;

            component.setVisible(show);
        });
    }

    private void updateJobGrid(List<JobInstanceInfo> jobs) {
        jobGrid.removeAll();
        jobNameToJobButtons.clear();
        jobNameToStepFLow.clear();
        runningJobs.clear();
        boolean enabled = SecurityUtils.isUserInRole("BATCH_EXECUTE");
        log.debug("Run enabled: " + enabled);
        for (JobInstanceInfo job : jobs) {
            String jobName = job.getName();
            if (job.isRunning())
                runningJobs.add(jobName);
            else
                runningJobs.remove(jobName);
            if (!tfJobFilter.isEmpty() && jobName.matches(tfJobFilter.getValue()))
                continue;
            Div jobRow = new Div();
            jobRow.addClassNames("job-grid-row");
            jobRow.getElement().setAttribute("data-job-name", jobName);
            jobRow.getElement().setAttribute("data-job-endtime", job.getEndTime() != null ? Long.toString(job.getEndTime().getTime()) : "0");
            jobRow.getElement().setAttribute("data-job-startTime", job.getStartTime() != null ? Long.toString(job.getStartTime().getTime()) : "0");
            jobRow.getElement().setAttribute("data-execution-status", job.getExecutionStatus());

            Div cell2 = new Div(createStepComponent(job));
            cell2.addClassNames("job-grid-cell","job-grid-steps-cell");
            JobButtons jb = new JobButtons(job, this, bautaManager);
            jb.setRunEnabled(enabled);

            jobNameToJobButtons.put(jobName, jb);
            Div cell3 = new Div(jb);
            cell3.addClassNames("job-grid-cell");

            Div cell0 = new Div();
            cell0.addClassNames("job-grid-cell");
            JobInfo jobInfo = new JobInfo(job);
            jobNameToJobInfo.put(jobName, jobInfo);
            Div jobNameDiv = new Div();
            jobNameDiv.setText(jobName);
            cell0.add(jobNameDiv);
            if (job.getDescription() != null) {
	            Div jobDescriptionDiv = new Div();
	            jobDescriptionDiv.getStyle().set("font-size", "0.7em");
	            jobDescriptionDiv.setText(job.getDescription());
	            cell0.add(jobDescriptionDiv);
            }
	        
            Div cell1 = new Div(jobInfo);
            cell1.addClassNames("job-grid-cell");

            jobRow.add(cell0, cell1, cell2, cell3);
            jobNameToJobRow.put(jobName, jobRow);
            jobGrid.add(jobRow);
        }
        sortJobGrid();
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
        HorizontalLayout  rightPanel = new HorizontalLayout();
        rightPanel.setPadding(false);
        rightPanel.setMargin(false);
        rightPanel.setSpacing(false);
        rightPanel.setAlignItems(FlexComponent.Alignment.CENTER);
        //rightPanel.setAlignSelf(FlexComponent.Alignment.END);
        rightPanel.setJustifyContentMode(FlexComponent.JustifyContentMode.END);

        bUpgradeInstance = new Button("", clickEvent -> {
            try {
                clickEvent.getSource().setEnabled(false);
                bautaManager.rebuildServer();
                showInfoMessage("Restarting server. Hold on until it is up and running again..");

            } catch (Exception e) {
                showErrorMessage("Failed to rebuild server: " + e.getMessage());
            }
        });
        bUpgradeInstance.getElement().setProperty("title", "Upgrades this instance.");
        bUpgradeInstance.setIcon(VaadinIcon.POWER_OFF.create());
        bUpgradeInstance.getStyle().set("margin-right","5px");

        bRefreshInstance = new Button("", clickEvent -> {
            try {
                bautaManager.refreshServer();
                showInfoMessage("Refreshed server!");

            } catch (Exception e) {
                showErrorMessage("Failed to refresh server: " + e.getMessage());
            }
        });
        bRefreshInstance.getElement().setProperty("title", "Refreshes this instance.");
        bRefreshInstance.setIcon(VaadinIcon.REFRESH.create());
        bRefreshInstance.getStyle().set("margin-right","5px");

        //upgradeInstanceButton.getStyle().set("margin-right", "5px");

        buildInfo = new Span();
        buildInfo.setClassName("build-info");
        // Job report
        Anchor download = new Anchor(new StreamResource("job_report.csv", () -> {
            try {
                return createJobReport();
            }
            catch(Exception e) {
                log.error("Failed to generate report", e);
                showErrorMessage("Failed to generate report: " + e.getMessage());
                return null;
            }

        }),"");
        buildInfo.getStyle().set("margin-right", "10px");
        download.getElement().setAttribute("download", true);
        download.add(new Button(new Icon(VaadinIcon.DOWNLOAD_ALT)));
        download.getElement().setProperty("title", "Job report with execution durations");
        download.getStyle().set("margin-right", "5px");

        rightPanel.add(buildInfo);
        rightPanel.add(download);
        rightPanel.add(bRefreshInstance);
        rightPanel.add(bUpgradeInstance);
        if (SecurityUtils.isSecurityEnabled()) {
            rightPanel.add(createUserMenu());
        }

        rightPanel.getStyle().set("margin-left", "auto").set("text-alight", "right").set("flex-grow","1").set("margin-top", "4px").set("margin-bottom","4px").set("padding-right","20px");

        this.addToNavbar(rightPanel);
        log.debug("createMainView.end");

    }
    private Component createUserMenu() {
        Label lSignout = new Label("Logout");
        lSignout.addComponentAsFirst(VaadinIcon.SIGN_OUT.create());
        Anchor logout = new Anchor("../logout", lSignout);
        logout.getStyle().set("padding", "2px");

        MenuBar mbUser = new MenuBar();
        miUser = mbUser.addItem("replace_me_with_username");
        //miUser.getElement().getStyle().set("color", "#0099ff");
        miUser.addComponentAsFirst(VaadinIcon.USER.create());
        miUser.getSubMenu().add(logout);
        //mbUser.setMinWidth("300px");
        //mbUser.getStyle().set("margin-right", "20px");
        return mbUser;
    }

    private Component createAboutView() {
        VerticalLayout aboutView = new VerticalLayout();
        aboutView.setPadding(true);
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
        VerticalLayout vl = new VerticalLayout();
        HorizontalLayout hl = new HorizontalLayout();
        HorizontalLayout hlRight = new HorizontalLayout();
        HorizontalLayout hlLeft = new HorizontalLayout();

        hl.setPadding(false);
        hl.setMargin(false);
        hl.setSpacing(true);
        hlLeft.setSpacing(true);
        hlLeft.setMargin(false);
        hlLeft.setPadding(false);
        hlRight.setSpacing(true);
        hlRight.setMargin(false);
        hlRight.setPadding(false);
        tfJobFilter = new TextField(event -> {
            filterJobGrid();
        });
        tfJobFilter.setPlaceholder("Job filter");
        tfJobFilter.setLabel("Search by name:");

        cbFilterOnStatus = new ComboBox<>();
        cbFilterOnStatus.setLabel("Filter:");
        cbFilterOnStatus.setItems("Running", "Completed", "Failed", "Unknown");
        cbFilterOnStatus.setClearButtonVisible(true);
        cbFilterOnStatus.addValueChangeListener(event -> {
           if (event.getValue() == null) {
               filterValue = "";
           } else {
               filterValue = (String) event.getValue();
           }
           filterJobGrid();
        });

        cbSortingList = new ComboBox<>();
        cbSortingList.setLabel("Sort By:");
        cbSortingList.setItems("Job Name", "Start Time", "End Time");
        cbSortingList.setClearButtonVisible(true);
        cbSortingList.addValueChangeListener(event -> {
           if (event.getValue() == null) {
               sortingValue = "";
           } else {
               sortingValue = event.getValue();
               sortJobGrid();
           }
        });

        cbShowTimeList = new ComboBox<>();
        cbShowTimeList.setLabel("Show:");
        cbShowTimeList.setItems("Today", "Last 24h", "Last 48h", "Last Week", "Custom");
        cbShowTimeList.setClearButtonVisible(true);
        cbShowTimeList.setPreventInvalidInput(true);
        cbShowTimeList.addValueChangeListener(event -> {
            cbShowTimeList.setHelperText("");
            if (event.getValue() == null) {
                showJobsTo = new Date();
                showJobsFrom = new Date(0);
            } else if (event.getValue().equals("Custom")) {
                openDateTimeDialog(cbShowTimeList);

            } else if (event.getValue().equals("Today")) {
                // TODO: figure out how to set today
            } else if (event.getValue().equals("Last 24h")) {
                showJobsFrom = DateUtils.addHours(new Date(), -24);
                showJobsTo = new Date();

            } else if (event.getValue().equals("Last 48h")) {
                showJobsFrom = DateUtils.addHours(new Date(), -48);
                showJobsTo = new Date();
            } else if (event.getValue().equals("Last Week")) {
                showJobsFrom = DateUtils.addWeeks(new Date(), -1);
                showJobsTo = new Date();
            }
            filterJobGrid();
        });
        hlLeft.add(tfJobFilter);
        hlLeft.add(cbFilterOnStatus);
        hlRight.add(cbSortingList);
        hlLeft.add(cbShowTimeList);
        hlLeft.setVerticalComponentAlignment(FlexComponent.Alignment.CENTER, cbFilterOnStatus);
        hlLeft.setVerticalComponentAlignment(FlexComponent.Alignment.CENTER, tfJobFilter);
        hlRight.setVerticalComponentAlignment(FlexComponent.Alignment.CENTER, cbSortingList);
        hl.add(hlLeft);
        hl.add(hlRight);
        vl.add(hl);
        jobGrid = new Div();
        jobGrid.addClassNames("job-grid");
        vl.add(jobGrid);
        return vl;
    }

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

    private InputStream createJobReport() throws Exception {
        StringBuilder sb = new StringBuilder();
        final String EOL = "\n";
        final String SEP = ";";
        final String SEP2 = ";;";
        final String SEP3 = ";;;";
        final String SEP4 = ";;;;";
        final String charset = "utf-8";
        // Job;Split;Flow;Status;Duration
        sb.append("Job").append(SEP).append("Split").append(SEP).append("Flow").append(SEP).append("Step").append(SEP).append("Status").append(SEP).append("Duration").append(SEP).append("Start").append(SEP).append("End").append(EOL);
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
                    .append(DurationFormatUtils.formatDuration(totalDuration, "HH:mm:ss")).append(SEP)
                    .append(job.getStartTime() != null ? DateFormatUtils.format(job.getStartTime(), "yyyy-MM-dd HH:mm:ss") : "").append(SEP)
                    .append(job.getEndTime() != null ? DateFormatUtils.format(job.getEndTime(), "yyyy-MM-dd HH:mm:ss") : "").append(SEP)
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
                        .append(DurationFormatUtils.formatDuration(step.getDuration(), "HH:mm:ss")).append(SEP)
                        .append(step.getStartTime() != null ? DateFormatUtils.format(step.getStartTime(), "yyyy-MM-dd HH:mm:ss") : "").append(SEP)
                        .append(step.getEndTime() != null ? DateFormatUtils.format(step.getEndTime(), "yyyy-MM-dd HH:mm:ss") : "").append(SEP)
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
        	log.error("Failed to generate job history", e);
            return new Label("Failed to fetch job history: " + e.getMessage());
        }
        for (JobInstanceInfo ji : jobs) {
            log.debug("jobInstanceInfo: {}", ji);
            log.debug("Start time is {}", ji.getStartTime());
            log.debug("End time is {}", ji.getEndTime());
            Div div = new Div();
            div.setWidthFull();
            UnorderedList ul = new UnorderedList();

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
            grid.addColumn(item-> item.getType()).setHeader("Type");

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
            grid.addColumn(item -> (item.getReadWriteInfo() != null ? item.getReadWriteInfo().toRWSString() : null)).setHeader("R/W/S");
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
        if (jobInstanceInfo.isRunning()) runningJobs.add(jobInstanceInfo.getName()); else runningJobs.remove(jobInstanceInfo.getName());
        UI ui = this.getUI().get();
        if (ui != null) {
            ui.access(() -> {
                showJobsTo = new Date();
                // grid.getDataProvider().refreshItem();
                String jobName = jobInstanceInfo.getName();
                JobButtons jobButtons = jobNameToJobButtons.get(jobName);
                jobButtons.setJobInstanceInfo(jobInstanceInfo);
                StepFlow stepFlow = jobNameToStepFLow.get(jobName);
                // TODO: Only needed for job start?
                stepFlow.update(jobInstanceInfo.getSteps());
                StepProgressBar progressBar  = jobNameToProgressBar.get(jobName);
                progressBar.update(jobInstanceInfo);
                JobInfo jobInfo = jobNameToJobInfo.get(jobName);
                jobInfo.update(jobInstanceInfo);
                Div jobRow = jobNameToJobRow.get(jobName);
                jobRow.getElement().setAttribute("data-job-endtime", jobInstanceInfo.getEndTime() != null ? Long.toString(jobInstanceInfo.getEndTime().getTime()) : "0");
                jobRow.getElement().setAttribute("data-job-startTime", jobInstanceInfo.getStartTime() != null ? Long.toString(jobInstanceInfo.getStartTime().getTime()) : "0");
                jobRow.getElement().setAttribute("data-execution-status", jobInstanceInfo.getExecutionStatus());
                filterJobGrid();
                sortJobGrid();
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

    public void openDateTimeDialog(ComboBox cbShowTimeList) {
        Dialog dateTimeDialog = createDateTimeDialog(cbShowTimeList);
        dateTimeDialog.open();
    }

    private Dialog createDateTimeDialog(ComboBox cbShowTimeList) {
        Dialog dialog = new Dialog();
        dialog.setWidth("450px");
        dialog.setCloseOnOutsideClick(false);

        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setSpacing(false);

        DateTimePicker dateTimePickerFrom = new DateTimePicker("From");
        DateTimePicker dateTimePickerTo = new DateTimePicker("To");

        HorizontalLayout buttons = new HorizontalLayout();
        buttons.setWidthFull();
        buttons.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        buttons.getStyle().set("margin-top", "20px");

        Button applyButton = new Button("Apply", clickEvent -> {
            try {
                showJobsFrom = Date.from(dateTimePickerFrom.getValue().atZone(ZoneId.systemDefault()).toInstant());
                showJobsTo = Date.from(dateTimePickerTo.getValue().atZone(ZoneId.systemDefault()).toInstant());
                filterJobGrid();
                dialog.close();
                SimpleDateFormat formatter = new SimpleDateFormat("EEE, yyyy-MM-dd HH.mm");
                cbShowTimeList.setHelperText("From: " + formatter.format(showJobsFrom) + "\n" + "To: " + formatter.format(showJobsTo));
            } catch (NullPointerException e) {
                showErrorMessage("Dates must be selected!");
            }
        });
        Button closeButton = new Button("Close", clickEvent -> {
            dialog.close();
        });

        buttons.add(applyButton);
        buttons.add(closeButton);
        layout.add(dateTimePickerFrom);
        layout.add(dateTimePickerTo);
        layout.add(buttons);
        dialog.add(layout);
        return dialog;
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
