package se.ikama.bauta.ui;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;

import javax.annotation.PostConstruct;

import org.apache.commons.lang3.StringUtils;
import org.jline.utils.Log;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Label;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.UnorderedList;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.data.selection.SelectionEvent;
import com.vaadin.flow.data.selection.SelectionListener;
import com.vaadin.flow.server.StreamResource;
import com.vaadin.flow.spring.annotation.UIScope;

import se.ikama.bauta.core.BautaManager;
import se.ikama.bauta.scheduling.JobTrigger;
import se.ikama.bauta.scheduling.JobTriggerDao;
import se.ikama.bauta.scheduling.JobTriggerLog;
import se.ikama.bauta.security.SecurityUtils;

@Component
@UIScope
public class SchedulingView extends VerticalLayout implements SelectionListener<Grid<JobTrigger>, JobTrigger> {
	@Autowired
	JobTriggerDao jobTriggerDao;

	@Autowired
	JobRegistry jobRegistry;

	@Autowired
	BautaManager bautaManager;

	Grid<JobTrigger> triggerGrid;
	List<JobTrigger> triggers = new ArrayList<>();

	Grid<JobTriggerLog> logGrid;
	List<JobTriggerLog> logs = new ArrayList<>();

	private Set<JobTrigger> selectedJobTriggers;

	Button removeButton, editButton, addCronButton, addJobCompletionButton, exportButton, importButton;
	Label lAdminInfo;
	Anchor exportLink;

	public SchedulingView() {
		triggerGrid = new Grid<>();
		triggerGrid.addThemeVariants(GridVariant.LUMO_COMPACT);
		Grid.Column<JobTrigger> jobNameColumn = triggerGrid.addColumn(JobTrigger::getJobName).setHeader("Job Name")
				.setSortable(true);
		Grid.Column<JobTrigger> jobTypeColumn = triggerGrid
				.addComponentColumn(item -> createTypeComponent(item.getTriggerType(), false)).setHeader("Type");
		triggerGrid.addComponentColumn(item -> createCronComponent(item.getCron())).setHeader("CRON");
		Grid.Column<JobTrigger> triggeringJobColumn = triggerGrid.addColumn(JobTrigger::getTriggeringJobName)
				.setHeader("Triggered by").setSortable(true);
		Grid.Column<JobTrigger> idColumn = triggerGrid.addColumn(JobTrigger::getId).setHeader("ID").setSortable(true);
		idColumn.setVisible(false);
		Grid.Column<JobTrigger> jobParamsColumn = triggerGrid.addColumn(JobTrigger::getJobParameters)
				.setHeader("Job Parameters").setSortable(true);
		triggerGrid.getColumns().forEach(c -> c.setClassNameGenerator(item -> "scheduler_cell"));

		triggerGrid.setSelectionMode(Grid.SelectionMode.MULTI);
		triggerGrid.addSelectionListener(this);
		add(triggerGrid);
		HorizontalLayout buttons = new HorizontalLayout();
		removeButton = new Button("Remove", clickEvent -> {
			remove();
		});
		removeButton.setIcon(VaadinIcon.MINUS_CIRCLE.create());
		removeButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
		removeButton.setEnabled(false);
		buttons.add(removeButton);
		editButton = new Button("Edit", clickEvent -> {
			edit();
		});
		editButton.setIcon(VaadinIcon.EDIT.create());
		editButton.setEnabled(false);
		buttons.add(editButton);

		addCronButton = new Button("Add CRON trigger ..", clickEvent -> {
			addCron(null);
		});
		addCronButton.setIcon(VaadinIcon.CALENDAR_CLOCK.create());
		// addCronButton.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
		buttons.add(addCronButton);
		addJobCompletionButton = new Button("Add Job complete trigger ..", clickEvent -> {
			addJobCompletion(null);
		});
		addJobCompletionButton.setIcon(VaadinIcon.FLAG_CHECKERED.create());
		// addJobCompletionButton.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
		buttons.add(addJobCompletionButton);
		MemoryBuffer buffer = new MemoryBuffer();
		Upload upload = new Upload(buffer);
		Button uploadButton = new Button("Import");
		uploadButton.setIcon(VaadinIcon.UPLOAD_ALT.create());
		upload.setUploadButton(uploadButton);
		upload.setDropAllowed(false);
		upload.setMaxFiles(1);

		upload.addSucceededListener(event -> {
			try (InputStream is = buffer.getInputStream()) {
				ObjectMapper objectMapper = new ObjectMapper();
				JobTrigger[] imported = objectMapper.readValue(is, JobTrigger[].class);
				save(imported);
				Notification.show("Imported " + imported.length + " triggers");
			} catch (Exception e) {
				Log.error("Failed to import triggers", e);
				Notification.show("Failed to import triggers: " + e.getMessage());
			}
		});

		buttons.add(upload);
		StreamResource streamResource = new StreamResource("triggers.json", () -> currentConfigAsStream());
		streamResource.setContentType("application/json");

		exportLink = new Anchor(streamResource, "");
		exportLink.getElement().setAttribute("download", true);
		exportLink.add(new Button("Export", new Icon(VaadinIcon.DOWNLOAD_ALT)));
		buttons.add(exportLink);

		add(buttons);
		lAdminInfo = new Label("You need to be ADMIN in order to edit triggers");
		lAdminInfo.getStyle().set("color", "var(--lumo-primary-text-color)");
		lAdminInfo.addComponentAsFirst(VaadinIcon.EXCLAMATION_CIRCLE.create());
		add(lAdminInfo);

		logGrid = new Grid<>();
		logGrid.addClassName("loggrid");
		logGrid.addThemeVariants(GridVariant.LUMO_WRAP_CELL_CONTENT, GridVariant.LUMO_COMPACT);
		Grid.Column<JobTriggerLog> logTstampColumn = logGrid
				.addComponentColumn(item -> createTimestampColumn(item.getTstamp())).setHeader("Timestamp")
				.setAutoWidth(true);
		Grid.Column<JobTriggerLog> logStatus = logGrid.addComponentColumn(item -> createStatusColumn(item.getStatus()))
				.setHeader("Status").setAutoWidth(true);

		Grid.Column<JobTriggerLog> logJobNameColumn = logGrid
				.addComponentColumn(item -> createLogColumn(item.getJobName())).setHeader("JobName").setAutoWidth(true);
		Grid.Column<JobTriggerLog> logErrorMsg = logGrid.addComponentColumn(item -> createLogColumn(item.getErrorMsg()))
				.setHeader("Error").setAutoWidth(true);

		Grid.Column<JobTriggerLog> logTypeColumn = logGrid
				.addComponentColumn(item -> createTypeComponent(item.getTriggerType(), true)).setHeader("Type")
				.setAutoWidth(true);
		logGrid.addComponentColumn(item -> createCronComponent(item.getCron())).setHeader("CRON").setAutoWidth(true);
		;
		Grid.Column<JobTriggerLog> logTriggeringJobColumn = logGrid
				.addComponentColumn(item -> createLogColumn(item.getTriggeringJobName())).setHeader("Triggered by")
				.setAutoWidth(true);
		logGrid.setSelectionMode(Grid.SelectionMode.NONE);
		logGrid.setMaxHeight("300px");
		logGrid.getColumns().forEach(c -> c.setClassNameGenerator(item -> "log_cell"));

		add(logGrid);
		/*
		 * Div div = new Div(); div.add("one<br>two");
		 * div.getStyle().set("font-size","0.5em");
		 * div.getStyle().set("font-family","monospace"); add(div);
		 */

	}

	@Override
	public void setVisible(boolean b) {
		super.setVisible(b);
		update();
	}

	@Override
	protected void onAttach(AttachEvent attachEvent) {
		update();
	}

	private com.vaadin.flow.component.Component createTimestampColumn(Date timestamp) {
		DateFormat tstampFormat = new SimpleDateFormat("YYMMdd HH:mm:ss");
		Label label = new Label(tstampFormat.format(timestamp));
		label.getStyle().set("font-size", "0.8em");
		return label;
	}

	private com.vaadin.flow.component.Component createLogColumn(String text) {
		Label label = new Label(text);

		return label;
	}

	private com.vaadin.flow.component.Component createStatusColumn(String text) {
		Div label = new Div();
		label.add(text);

		if ("FAILED".equals(text)) {
			label.getStyle().set("color", "var(--lumo-error-color)");
		} else {
			label.getStyle().set("color", "var(--lumo-success-color)");
		}
		return label;
	}

	private com.vaadin.flow.component.Component createCronComponent(String cron) {
		Label label = new Label(cron);
		label.getStyle().set("font-family", "monospace");

		return label;
	}

	private com.vaadin.flow.component.Component createTypeComponent(JobTrigger.TriggerType triggerType, boolean log) {
		if (triggerType.equals(JobTrigger.TriggerType.CRON)) {
			Icon icon = VaadinIcon.CALENDAR_CLOCK.create();
			if (log)
				icon.setSize("0.8em");
			icon.getElement().setProperty("title", "CRON trigger");
			return icon;
		} else {
			Icon icon = VaadinIcon.FLAG_CHECKERED.create();
			if (log)
				icon.setSize("0.8em");
			icon.getElement().setProperty("title", "Job completion trigger");
			return icon;
		}
	}

	@PostConstruct
	public void init() {
		update();
	}

	@Override
	public void selectionChange(SelectionEvent<Grid<JobTrigger>, JobTrigger> selectionEvent) {
		updateButtonState();
	}

	private void updateButtonState() {
		if (SecurityUtils.isUserInRole("ADMIN")) {
			selectedJobTriggers = triggerGrid.getSelectionModel().getSelectedItems();
			removeButton.setEnabled(selectedJobTriggers.size() > 0);
			editButton.setEnabled(selectedJobTriggers.size() == 1);
			addCronButton.setEnabled(true);
			addJobCompletionButton.setEnabled(true);
			lAdminInfo.setVisible(false);
		} else {
			lAdminInfo.setVisible(true);
			removeButton.setEnabled(false);
			editButton.setEnabled(false);
			addCronButton.setEnabled(false);
			addJobCompletionButton.setEnabled(false);
		}
	}

	private InputStream currentConfigAsStream() {
		ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
		try {
			String json = mapper.writeValueAsString(this.triggers);
			return new ByteArrayInputStream(json.getBytes("UTF-8"));
		} catch (Exception e) {
			throw new RuntimeException("Failed to stream scheduling configuration", e);
		}

	}

	private void remove() {
		if (selectedJobTriggers.size() > 0) {
			for (JobTrigger jt : selectedJobTriggers) {
				jobTriggerDao.delete(jt);
			}
		}
		update();
		bautaManager.initializeScheduling(true);
	}

	private void edit() {
		if (selectedJobTriggers.size() == 1) {
			JobTrigger jt = selectedJobTriggers.iterator().next();
			if (jt.getTriggerType() == JobTrigger.TriggerType.CRON) {
				addCron(jt);
			} else {
				addJobCompletion(jt);
			}
		}
	}

	private void addCron(JobTrigger jobTrigger) {
		Dialog cronDialog = createAddCronDialog(jobTrigger);
		cronDialog.open();
	}

	private void addJobCompletion(JobTrigger jobTrigger) {
		Dialog jobCompletionDialog = createAddJobCompletionDialog(jobTrigger);
		jobCompletionDialog.open();
	}
	
	private void save(JobTrigger[] jobTriggers) {
		jobTriggerDao.deleteAll();
		for (JobTrigger jt : jobTriggers) {
			jt.setId(null);
			jobTriggerDao.saveOrUpdate(jt);			
		}
		update();
		bautaManager.initializeScheduling(true);
	}
	private void saveOrUpdate(JobTrigger jobTrigger) {
		jobTriggerDao.saveOrUpdate(jobTrigger);
		update();
		bautaManager.initializeScheduling(true);
	}

	private Dialog createAddCronDialog(JobTrigger existingTrigger) {
		Dialog dialog = new Dialog();
		dialog.setWidth("500px");
		dialog.setCloseOnOutsideClick(false);

		VerticalLayout layout = new VerticalLayout();
		layout.setPadding(false);
		layout.setSpacing(false);

		ArrayList<String> jobNames = new ArrayList<>(jobRegistry.getJobNames());
		Collections.sort(jobNames);

		ComboBox<String> jobComboBox = new ComboBox<String>("Job");
		jobComboBox.setItems(jobNames);
		jobComboBox.setRequired(true);
		jobComboBox.setRequiredIndicatorVisible(true);
		jobComboBox.setWidthFull();
		jobComboBox.addValueChangeListener(comboBoxStringComponentValueChangeEvent -> {
			if (jobComboBox.isEmpty()) {
				jobComboBox.setInvalid(true);
			} else {
				jobComboBox.setInvalid(false);
			}
		});
		layout.add(jobComboBox);

		TextField cron = new TextField("CRON");
		cron.setLabel("CRON");
		cron.setRequired(true);

		cron.setValue("0 0 0 * * *");
		cron.setRequiredIndicatorVisible(true);
		cron.addValueChangeListener(textFieldStringComponentValueChangeEvent -> {
			String cronError = validateCron(cron.getValue());
			cron.setInvalid(cronError != null);
			cron.setErrorMessage(cronError);
		});
		cron.setWidthFull();

		layout.add(cron);
		UnorderedList cronHelp = new UnorderedList();
		cronHelp.add(new ListItem("'0 30 2 * * *': Every night at 02:30"));
		cronHelp.add(new ListItem("'0 30 14 * * MON-FRI': Every Monday to Friday at 14:30"));
		cronHelp.add(new ListItem("'0 0 * * * *': Every hour"));
		cronHelp.add(new ListItem("'0 0 8-10 * * *': 8, 9 and 10 o'clock of every day."));
		cronHelp.add(new ListItem("'0 0/30 8-10 * * *': 8:00, 8:30, 9:00, 9:30, 10:00 and 10:30 every day."));
		Anchor cronLink = new Anchor("https://www.freeformatter.com/cron-expression-generator-quartz.html", "More");
		cronLink.setTarget("cron-help");
		Details cronDetails = new Details(VaadinIcon.QUESTION_CIRCLE.create(), new Div(
				new Text("{seconds} {minutes} {hours} {day of month} {month} {day of week}"), cronHelp, cronLink));
		layout.add(cronDetails);

		TextField jobParameters = new TextField();
		jobParameters.setLabel("Job Parameters");
		jobParameters.setPlaceholder("KEY1=VALUE1,KEY2=VALUE2");
		jobParameters.setWidthFull();
		layout.add(jobParameters);

		if (existingTrigger != null) {
			jobComboBox.setValue(existingTrigger.getJobName());
			cron.setValue(existingTrigger.getCron());
			jobParameters.setValue(existingTrigger.getJobParameters());
		}

		HorizontalLayout buttons = new HorizontalLayout();
		buttons.setWidthFull();
		buttons.setJustifyContentMode(JustifyContentMode.END);
		buttons.getStyle().set("margin-top", "20px");
		Button okButton = new Button("OK", clickEvent -> {
			if (jobComboBox.isEmpty()) {
				jobComboBox.setErrorMessage("Select a job to trigger");
				jobComboBox.setInvalid(true);
				return;
			}
			String validationError = validateCron(cron.getValue());
			if (validationError != null) {
				cron.setErrorMessage(validationError);
				cron.setInvalid(true);
				return;
			}
			validationError = validateKeyValues(jobParameters.getValue());
			if (validationError != null) {
				jobParameters.setErrorMessage(validationError);
				jobParameters.setInvalid(true);
				return;
			}

			JobTrigger trigger = existingTrigger != null ? existingTrigger : new JobTrigger();
			trigger.setTriggerType(JobTrigger.TriggerType.CRON);
			trigger.setJobName(jobComboBox.getValue());
			trigger.setCron(cron.getValue());
			trigger.setJobParameters(jobParameters.getValue());
			saveOrUpdate(trigger);
			dialog.close();

		});
		Button cancelButton = new Button("Cancel", clickEvent -> {
			dialog.close();
		});
		buttons.add(okButton);
		buttons.add(cancelButton);
		layout.add(buttons);
		dialog.add(layout);
		return dialog;
	}

	private String validateCron(String cron) {
		if (cron.startsWith("* ")) {
			return "You don't want to trigger a job every second!";
		}
		try {
			CronTrigger cronTrigger = new CronTrigger(cron);
		} catch (IllegalArgumentException e) {
			return e.getMessage();
		}
		return null;
	}

	private String validateKeyValues(String s) {
		if (StringUtils.isEmpty(s))
			return null;
		else if (!s.matches(
				"([A-Za-z0-9_-]*=[A-Za-z0-9_\\-\\(\\):\\/\\\\\\*\\.]*)((,[ ]{0,1})([A-Za-z0-9_-]*=[A-Za-z0-9_\\-\\(\\):\\/\\\\\\*\\.]*))*")) {
			return "Illegal key-value string";
		} else
			return null;
	}

	private Dialog createAddJobCompletionDialog(JobTrigger existingTrigger) {
		Dialog dialog = new Dialog();
		dialog.setWidth("600px");
		dialog.setCloseOnOutsideClick(false);

		VerticalLayout layout = new VerticalLayout();
		layout.setPadding(false);
		layout.setSpacing(false);

		ArrayList<String> jobNames = new ArrayList<>(jobRegistry.getJobNames());
		Collections.sort(jobNames);

		ComboBox<String> jobComboBox = new ComboBox<String>("Job");
		jobComboBox.setItems(jobNames);
		jobComboBox.setRequired(true);
		jobComboBox.setRequiredIndicatorVisible(true);
		jobComboBox.setWidthFull();
		layout.add(jobComboBox);

		ComboBox<String> triggeredByComboBox = new ComboBox<String>("Triggered by");
		triggeredByComboBox.setItems(jobNames);
		triggeredByComboBox.setRequired(true);
		triggeredByComboBox.setRequiredIndicatorVisible(true);
		triggeredByComboBox.setWidthFull();
		layout.add(triggeredByComboBox);

		TextField jobParameters = new TextField();
		jobParameters.setLabel("Job Parameters");
		jobParameters.setPlaceholder("KEY1=VALUE1,KEY2=VALUE2");
		jobParameters.setWidthFull();
		layout.add(jobParameters);

		if (existingTrigger != null) {
			jobComboBox.setValue(existingTrigger.getJobName());
			triggeredByComboBox.setValue(existingTrigger.getTriggeringJobName());
			jobParameters.setValue(existingTrigger.getJobParameters());
		}

		HorizontalLayout buttons = new HorizontalLayout();
		buttons.setWidthFull();
		buttons.setJustifyContentMode(JustifyContentMode.END);
		buttons.getStyle().set("margin-top", "20px");

		Button okButton = new Button("OK", clickEvent -> {
			if (jobComboBox.isEmpty()) {
				jobComboBox.setErrorMessage("Select a job to trigger");
				jobComboBox.setInvalid(true);
				return;
			}
			if (triggeredByComboBox.isEmpty()) {
				triggeredByComboBox.setErrorMessage("Select a triggering job");
				triggeredByComboBox.setInvalid(true);
				return;
			}
			String validationError = validateKeyValues(jobParameters.getValue());
			if (validationError != null) {
				jobParameters.setErrorMessage(validationError);
				jobParameters.setInvalid(true);
				return;
			}

			JobTrigger trigger = existingTrigger != null ? existingTrigger : new JobTrigger();
			trigger.setTriggerType(JobTrigger.TriggerType.JOB_COMPLETION);
			trigger.setJobName(jobComboBox.getValue());
			trigger.setTriggeringJobName(triggeredByComboBox.getValue());
			trigger.setJobParameters(jobParameters.getValue());
			saveOrUpdate(trigger);
			dialog.close();
		});
		Button cancelButton = new Button("Cancel", clickEvent -> {
			dialog.close();
		});
		buttons.add(okButton);
		buttons.add(cancelButton);
		layout.add(buttons);
		dialog.add(layout);
		return dialog;
	}

	private void update() {
		triggers = jobTriggerDao.loadTriggers();
		triggerGrid.setItems(triggers);
		logs = jobTriggerDao.loadLog(100);
		logGrid.setItems(logs);
		updateButtonState();
	}
}
