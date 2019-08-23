package ikama.bauta.ui;


import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.TemplateRenderer;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.Theme;
import com.vaadin.flow.theme.lumo.Lumo;
import ikama.bauta.core.BautaManager;
import ikama.bauta.core.JobEventListener;
import ikama.bauta.core.JobInstanceInfo;
import ikama.bauta.core.StepInfo;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.DependsOn;
import org.thymeleaf.util.DateUtils;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Push
@Route("")
//@Theme(value = Material.class, variant = Material.LIGHT)
@Theme(value = Lumo.class, variant = Lumo.DARK)
@StyleSheet("../static/css/bauta-theme.css")
// @PWA(name = "Project Base for Vaadin Flow with Spring", shortName = "Project Base")
public class MainView extends AppLayout implements JobEventListener {

    Logger log = LoggerFactory.getLogger(this.getClass());
    @Autowired
    BautaManager batchManager;

    private UI ui;
    Grid<JobInstanceInfo> grid = null;
    ArrayList<Button> actionButtons = new ArrayList<>();
    Tabs menuTabs = null;
    private boolean initialized = false;

    public MainView() {

        log.info("Creating main view");
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        log.info("Attach");
        batchManager.registerJobChangeListener(this);

        if (!initialized) {
            this.ui = attachEvent.getUI();
            Image img = new Image("../static/images/bauta-logo-light.png", "Bauta logo");
            img.setHeight("28px");
            DrawerToggle drawerToggle = new DrawerToggle();
            this.addToNavbar(new DrawerToggle(), img);
            this.setDrawerOpened(false);

            Map<Tab, Component> tabsToPages = new HashMap<>();
            Component jobPage = createJobView();
            jobPage.setVisible(true);
            Tab jobTab = new Tab("Jobs");
            Component aboutPage = createAboutView();
            aboutPage.setVisible(false);
            Tab aboutTab = new Tab("About");
            tabsToPages.put(jobTab, jobPage);
            tabsToPages.put(aboutTab, aboutPage);
            Tabs tabs = new Tabs(jobTab, aboutTab);
            tabs.setSelectedTab(jobTab);
            tabs.setOrientation(Tabs.Orientation.VERTICAL);
            Div pages = new Div(jobPage, aboutPage);
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


            rightPanel.getStyle().set("margin-right","20px");
            /*Button upgradeBautaButton = new Button("");
            upgradeBautaButton.getElement().setProperty("title", "Upgrades the Bauta framework to the latest version");
            upgradeBautaButton.setIcon(VaadinIcon.POWER_OFF.create());
            //upgradeBautaButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            */

            Button upgradeInstanceButton = new Button("",clickEvent -> {
                try {
                    batchManager.rebuildServer();
                }
                catch(Exception e) {
                    showErrorMessage("Failed to rebuild server: " + e.getMessage());
                }
            });
            upgradeInstanceButton.getElement().setProperty("title", "Upgrades this instance by fetching latest scripts and job definitions from VCS");
            upgradeInstanceButton.setIcon(VaadinIcon.REFRESH.create());
            //upgradeInstanceButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            upgradeInstanceButton.getStyle().set("margin-right","5px");

            Label buildInfo = new Label(batchManager.getShortServerInfo());
            buildInfo.getStyle().set("font-size","0.75em");
            buildInfo.getStyle().set("margin-right","10px");
            rightPanel.add(buildInfo);
            rightPanel.add(upgradeInstanceButton);
            //rightPanel.add(upgradeBautaButton);

            //rightPanel.add(new Button("Another"));

            rightPanel.getStyle().set("margin-left", "auto").set("text-alight","right");

            this.addToNavbar(rightPanel);
            initialized = true;
        }
    }



    @Override
    protected void onDetach(DetachEvent detachEvent) {
        log.info("Detach");
        batchManager.unregisterJobChangeListener(this);
    }

    @PostConstruct
    @DependsOn("batchManager")
    public void init() {


    }
    private Component createAboutView() {
        VerticalLayout aboutView = new VerticalLayout();
        aboutView.setWidthFull();
        for(String i : batchManager.getServerInfo()) {
            aboutView.add(new Label(i));
        }
        return aboutView;
    }
    private Component createJobView()  {
        VerticalLayout jobView = new VerticalLayout();
        jobView.setWidthFull();
        //setDefaultHorizontalComponentAlignment(FlexComponent.Alignment.STRETCH);
        this.grid = new Grid();
        grid.addClassName("jobgrid");
        grid.setId("jobgrid");
        grid.setHeightByRows(true);
        grid.setVerticalScrollingEnabled(false);
        grid.setSelectionMode(Grid.SelectionMode.NONE);
        grid.setDetailsVisibleOnClick(false);
        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER,
                GridVariant.LUMO_NO_ROW_BORDERS);

        Grid.Column<JobInstanceInfo> c = grid.addColumn(item -> item.getName());
        c.setClassNameGenerator(item -> "job_cell");
        //grid.addColumn(item->createJobInfo(grid, item)).setHeader("Status");
        grid.addColumn(TemplateRenderer.<JobInstanceInfo>of(
                "<div style='font-size:0.8em'>ExecutionID: [[item.executionId]]<br>" +
                        "Instance ID: [[item.instanceId]]<br>" +
                        "Started: [[item.startTime]]<br>" +
                        "Ended: [[item.endTime]]<br>" +
                        "Duration: [[item.duration]]<br>" +
                        "Exitstatus: [[item.exitStatus]]<br>" +
                        "</div>"
                )
        .withProperty("executionId", JobInstanceInfo::getExecutionId)
        .withProperty("instanceId", JobInstanceInfo::getInstanceId)
        .withProperty("exitStatus", JobInstanceInfo::getExitStatus)
        .withProperty("startTime", ji -> DateUtils.format(ji.getStartTime(), "YYMMdd HH:mm:ss", Locale.US))
        .withProperty("endTime", ji -> ji != null ? DateUtils.format(ji.getStartTime(), "YYMMdd HH:mm:ss", Locale.US) : "")
        .withProperty("duration", ji -> ji != null ? DurationFormatUtils.formatDuration(ji.getDuration(), "HH:mm:ss") : ""));
        grid.addComponentColumn(item -> createStepComponent(grid, item));
        grid.addComponentColumn(item -> createButtons(grid, item));

        //grid.addColumn(item->item.getSteps().toArray().toString()).setHeader("Steps");
        try {
            grid.setItems(batchManager.jobDetails());
        }
        catch(Exception e) {
            e.printStackTrace();
            showErrorMessage("Failed to fetch job details");
        }
        jobView.add(grid);
        return jobView;
    }

    private Component createButtons(Grid<JobInstanceInfo> grid, JobInstanceInfo item) {
        HorizontalLayout hl = new HorizontalLayout();
        hl.setSpacing(false);
        Button startButton = new Button("", clickEvent -> {
            batchManager.startJob(item.getName());
        });
        startButton.setIcon(VaadinIcon.PLAY.create());
        hl.add(startButton);

        Button stopButton = new Button("", clickEvent -> {
            batchManager.stopJob(item.getName());
        });
        stopButton.setIcon(VaadinIcon.STOP.create());
        stopButton.getStyle().set("margin-left", "4px");
        hl.add(stopButton);

        Button restartButton = new Button("", clickEvent -> {
            try {
                batchManager.restartJob(item.getExecutionId());
            } catch (Exception e) {
                showErrorMessage(e.getMessage());
                //TODO: Error handling
                e.printStackTrace();
            }
        });
        restartButton.setIcon(VaadinIcon.ROTATE_LEFT.create());
        restartButton.getStyle().set("margin-left", "4px");
        hl.add(restartButton);

        Button abandonButton = new Button("", clickEvent -> {
            try {
                batchManager.abandonJob(item.getExecutionId());
            } catch (Exception e) {
                showErrorMessage(e.getMessage());
                e.printStackTrace();
            }
        });
        abandonButton.getElement().setProperty("title", "Abandons a job. Useful when the process was killed while a job was running and is now stuck in running state.");
        abandonButton.setIcon(VaadinIcon.TRASH.create());

        abandonButton.getStyle().set("margin-left", "4px");
        hl.add(abandonButton);

        Button infoButton = new Button("", clickEvent -> {

            Dialog infoDialog = new Dialog();
            infoDialog.add(createJobHistory(item.getName()));
            infoDialog.setWidth("800px");
            infoDialog.setHeight("300px");
            infoDialog.open();
        });
        infoButton.setIcon(VaadinIcon.BULLETS.create());
        infoButton.getStyle().set("margin-left", "4px");
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

        return hl;
    }

    private static String batchStatusToColor(String batchStatus) {
        switch (batchStatus) {
            case "COMPLETED":
                return "var(--lumo-success-color)";
            case "ABANDONED":
                return "var(--lumo-error-color-50pct)";
            case "STARTED":
            case "STARTING":
                return "var(--lumo-primary-color)";
            case "FAILED":
                return "var(--lumo-error-color)";
            case "STOPPED":
            case "STOPPING":
                return "var(--lumo-primary-color)";
            default:
                return "var(--lumo-secondary-color)";
        }
    }

    private Component createStepComponent(Grid<JobInstanceInfo> grid, JobInstanceInfo jobInstanceInfo) {
        VerticalLayout vl = new VerticalLayout();
        vl.setAlignItems(FlexComponent.Alignment.START);
        vl.setSpacing(false);
        for (StepInfo step : jobInstanceInfo.getSteps()) {
            Component statusLabel = createStatusLabel(step.getExecutionStatus());
            Label stepNameLabel = new Label(step.getName());
            stepNameLabel.getStyle().set("font-size", "0.8em");
            Div div = new Div(stepNameLabel, statusLabel);
            div.getStyle().set("display", "flex")
                    .set("align-items", "baseline")
                    .set("flex-flow", "row wrap")
                    .set("margin", "2px");

            if (step.isRunning()) {
                ProgressBar pb = new ProgressBar();
                pb.setIndeterminate(true);
                pb.setWidth("40px");
                pb.setHeight("6px");
                pb.getStyle().set("margin-left", "5px").set("flex-basis", "100%").set("margin-left", "0").set("margin-right", "0");
                div.add(pb);
            }
            if (step.getReportUrls() != null) {
                for (String url : step.getReportUrls()) {
                    Icon icon = null;

                    if (url.endsWith(".html")) {
                        icon = VaadinIcon.CHART.create();
                    } else if (url.endsWith(".log")) {
                        icon = VaadinIcon.FILE_TEXT_O.create();
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
                    infoDialog.setCloseOnEsc(true);
                    Label l = new Label(step.getExitDescription());
                    l.getStyle().set("font-size","0.8em").set("font-family","monospace");

                    infoDialog.add(l);
                    infoDialog.setWidth("600px");
                    infoDialog.setHeight("300px");
                    infoDialog.open();
                });
                //icon.setSize("1.2em");

                descriptionButton.setIcon(icon);
                descriptionButton.getStyle().set("font-size","0.8em");
                descriptionButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_ERROR);
                div.add(descriptionButton);

            }
            vl.add(div);
        }
        return vl;
    }
    private Component createStatusLabel(String executionStatus) {
        Div statusLabel = new Div();
        statusLabel.setClassName("step_status");
        statusLabel.setText(executionStatus);
        statusLabel.getStyle().set("background-color", batchStatusToColor(executionStatus));
        return statusLabel;
    }

    private Component createJobHistory(String jobName) {
        VerticalLayout vl = new VerticalLayout();
        List<JobInstanceInfo> jobs = null;
        try {
             jobs = batchManager.jobHistory(jobName);
        } catch (Exception e) {
            showErrorMessage("Failed to fetch job history: " + e.getMessage());
        }
        for (JobInstanceInfo ji : jobs) {
            Div div = new Div();
            div.setWidthFull();
            UnorderedList ul = new UnorderedList();


            ul.add(new ListItem("InstanceId: " + ji.getInstanceId().toString()));
            ul.add(new ListItem("ExecutionId: " + ji.getExecutionId().toString()));
            ul.add(new ListItem("Start/end time: " + DateUtils.format(ji.getStartTime(), "YYMMdd HH:mm:ss", Locale.US)+ "/"+DateUtils.format(ji.getEndTime(), "YYMMdd HH:mm:ss", Locale.US)));
            ul.add(new ListItem("Duration: " + DurationFormatUtils.formatDuration(ji.getDuration(), "HH:mm:ss")));
            ul.add(new ListItem(new Label("Exit status: "), createStatusLabel(ji.getExitStatus())));
            div.add(ul);
            Grid<StepInfo> grid = new Grid<>();
            grid.setHeightByRows(true);
            grid.setWidthFull();
            grid.addColumn(StepInfo::getName).setHeader("Name").setAutoWidth(true);
            //grid.addColumn(StepInfo::getExecutionStatus).setHeader("Status");
            grid.addComponentColumn(item -> createStatusLabel(item.getExecutionStatus())).setHeader("Status");
            grid.addComponentColumn(item -> new Label(DurationFormatUtils.formatDuration(item.getDuration(), "HH:mm:ss"))).setHeader("Duration");
            grid.setItems(ji.getSteps());
            div.add(grid);
            vl.add(div);

        }
        return vl;
    }

    @Override
    public void onJobChange(JobInstanceInfo jobInstanceInfo) {
        log.info("onJobChange {} ", jobInstanceInfo);

        this.ui.access(() -> {
            grid.getDataProvider().refreshItem(jobInstanceInfo);
        });
    }

    private void showErrorMessage(String message) {
        Label label = new Label(message);
        Notification notification = new Notification(label);
        notification.setPosition(Notification.Position.BOTTOM_START);
        notification.setDuration(5000);
        notification.open();
    }
}
