package ikama.batchc3.ui;


import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Label;
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
import com.vaadin.flow.data.renderer.TemplateRenderer;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.Theme;
import com.vaadin.flow.theme.lumo.Lumo;
import ikama.batchc3.core.C3Manager;
import ikama.batchc3.core.JobEventListener;
import ikama.batchc3.core.JobInfo;
import ikama.batchc3.core.StepInfo;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.thymeleaf.util.DateUtils;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Locale;

@Push
@Route("")
//@Theme(value = Material.class, variant = Material.LIGHT)
@Theme(value = Lumo.class, variant = Lumo.DARK)
// @PWA(name = "Project Base for Vaadin Flow with Spring", shortName = "Project Base")
public class MainView extends AppLayout implements JobEventListener {

    Logger log = LoggerFactory.getLogger(this.getClass());
    @Autowired
    C3Manager batchManager;
    private UI ui;
    Grid<JobInfo> grid = null;
    ArrayList<Button> actionButtons = new ArrayList<>();

    public MainView() {

        log.info("Creating main view");
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        this.ui = attachEvent.getUI();
        batchManager.registerJobChangeListener(this);


        Image img = new Image("../static/images/batch-c3-logo-light.png", "Batch c3 logo");
        img.setHeight("28px");
        DrawerToggle drawerToggle = new DrawerToggle();
        this.addToNavbar(new DrawerToggle(), img);
        this.setDrawerOpened(false);
        Tabs tabs = new Tabs(new Tab("Jobs"), new Tab("About"));


        tabs.setOrientation(Tabs.Orientation.VERTICAL);
        this.addToDrawer(tabs);

        try {
            this.setContent(createJobView());
        } catch (Exception e) {
            this.setContent(new Label("Failed to create job view: " + e.getMessage()));
        }

        log.info("Attach");

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

    private Component createJobView() throws Exception {
        VerticalLayout jobView = new VerticalLayout();
        jobView.setWidthFull();
        //setDefaultHorizontalComponentAlignment(FlexComponent.Alignment.STRETCH);
        this.grid = new Grid();
        grid.setHeightByRows(false);

        grid.setVerticalScrollingEnabled(false);
        grid.setSelectionMode(Grid.SelectionMode.NONE);
        grid.setDetailsVisibleOnClick(false);

        grid.addColumn(item -> item.getName());
        //grid.addColumn(item->createJobInfo(grid, item)).setHeader("Status");
        grid.addColumn(TemplateRenderer.<JobInfo>of(
                "<div style='font-size:0.8em'>ExecutionID: [[item.executionId]]<br>" +
                        "Instance ID: [[item.instanceId]]<br>" +
                        "Started: [[item.startTime]]<br>" +
                        "Ended: [[item.endTime]]<br>" +
                        "Duration: [[item.duration]]<br>" +
                        "Exitstatus: [[item.exitStatus]]<br>" +
                        "</div>"
                )
                        .withProperty("executionId", JobInfo::getExecutionId)
                        .withProperty("instanceId", JobInfo::getInstanceId)
                        .withProperty("exitStatus", JobInfo::getExitStatus)
                        .withProperty("startTime", ji -> DateUtils.format(ji.getStartTime(), "YYMMdd HH:mm:ss", Locale.US))
                        .withProperty("endTime", ji -> ji != null ? DateUtils.format(ji.getStartTime(), "YYMMdd HH:mm:ss", Locale.US) : "")
                        .withProperty("duration", ji -> ji != null ? DurationFormatUtils.formatDuration(ji.getDuration(), "HH:mm:ss") : "")


        );
        grid.addComponentColumn(item -> createStepComponent(grid, item));
        grid.addComponentColumn(item -> createButtons(grid, item));

        //grid.addColumn(item->item.getSteps().toArray().toString()).setHeader("Steps");
        grid.setItems(batchManager.jobDetails());
        jobView.add(grid);
        return jobView;
    }
    /*
    private Component createJobInfo(Grid<JobInfo> grid, JobInfo item) {
        StringBuilder sb = new StringBuilder("<ul>");
        sb.append("<li>ex.st:").append(item.getExecutionStatus()).append("</ul>");


        Label htmlLabel = new Label(sb.toString(),);
        return htmlLabel;
    }
    */

    private Component createButtons(Grid<JobInfo> grid, JobInfo item) {
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
            infoDialog.add(new Label("Here we will show job history"));
            infoDialog.setWidth("500px");
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

    private Component createStepComponent(Grid<JobInfo> grid, JobInfo jobInfo) {
        VerticalLayout vl = new VerticalLayout();
        vl.setAlignItems(FlexComponent.Alignment.START);
        vl.setSpacing(false);
        for (StepInfo step : jobInfo.getSteps()) {
            Label statusLabel = new Label(step.getExecutionStatus());

            statusLabel.getStyle().set("background-color", batchStatusToColor(step.getExecutionStatus()))
                    .set("margin-left", "5px")
                    .set("padding-left", "3px")
                    .set("padding-right", "3px")
                    .set("padding-top", "2px")
                    .set("padding-bottom", "2px")
                    .set("font-size", "0.6em")
                    .set("color", "#eeeeee")
                    .set("border-radius", "var(--lumo-border-radius-m)");
            Div div = new Div(new Label(step.getName()), statusLabel);
            if (step.isRunning()) {
                 ProgressBar pb = new ProgressBar();
                pb.setIndeterminate(true);
                pb.setWidth("30px");
                pb.setHeight("14px");
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
                    icon.setSize("1.75em");
                    Anchor reportAnchor = new Anchor("../" + url, icon);
                    reportAnchor.setTarget("reports");
                    reportAnchor.getStyle().set("font-size", "0.6em").set("margin-left","5px");
                    div.add(reportAnchor);
                }
            }
            vl.add(div);
        }
        return vl;
    }

    @Override
    public void onJobChange(JobInfo jobInfo) {
        log.info("onJobChange {} ", jobInfo);

        this.ui.access(() -> {
            grid.getDataProvider().refreshItem(jobInfo);
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
