package se.ikama.bauta.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Label;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.UnorderedList;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.selection.SelectionEvent;
import com.vaadin.flow.data.selection.SelectionListener;
import com.vaadin.flow.server.StreamResource;
import com.vaadin.flow.spring.annotation.UIScope;
import org.jline.utils.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import se.ikama.bauta.core.BautaManager;
import se.ikama.bauta.scheduling.JobGroup;
import se.ikama.bauta.scheduling.JobGroupDao;
import se.ikama.bauta.security.SecurityUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
@UIScope
public class GroupView extends VerticalLayout implements SelectionListener<Grid<JobGroup>, JobGroup> {
    Logger log = LoggerFactory.getLogger(this.getClass().getName());

    @Autowired
    BautaManager manager;

    @Autowired
    JobGroupDao groupDao;

    Grid<JobGroup> groupGrid;
    List<JobGroup> groups = new ArrayList<>();
    Button removeButton, editButton, createGroupButton;
    Anchor exportLink;
    private Set<JobGroup> selectedGroups;

    public GroupView(){
        groupGrid = new Grid<>();
        groupGrid.addThemeVariants(GridVariant.LUMO_COMPACT);
        Grid.Column<JobGroup> groupNameColumn = groupGrid.addColumn(JobGroup::getName).setHeader("Groupname")
                .setSortable(true)
                .setWidth("25%");
        Grid.Column<JobGroup> groupRegexColumn = groupGrid.addColumn(JobGroup::getRegex).setHeader("Regex")
                .setSortable(true)
                .setWidth("30%");
        Grid.Column<JobGroup> groupJobNamesColumn = groupGrid.addColumn(JobGroup::getJobNames).setHeader("Matching jobs")
                .setSortable(true)
                .setWidth("45%");
        groupGrid.setSelectionMode(Grid.SelectionMode.MULTI);
        groupGrid.addSelectionListener(this);
        add(groupGrid);

        HorizontalLayout buttons = new HorizontalLayout();
        createGroupButton = new Button("Create", clickEvent -> addJobGroup());
        createGroupButton.setIcon(VaadinIcon.PLUS_CIRCLE.create());
        buttons.add(createGroupButton);

        editButton = new Button("Edit", clickEvent -> editJobGroup());
        editButton.setIcon(VaadinIcon.EDIT.create());
        editButton.setEnabled(false);
        buttons.add(editButton);

        removeButton = new Button("Remove", clickEvent -> deleteGroup());
        removeButton.setIcon(VaadinIcon.MINUS_CIRCLE.create());
        removeButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
        removeButton.setEnabled(false);
        buttons.add(removeButton);

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
                JobGroup[] imported = objectMapper.readValue(is, JobGroup[].class);
                save(imported);
                Notification.show("Imported " + imported.length + " groups");
            } catch (Exception e) {
                Log.error("Failed to import triggers", e);
                Notification.show("Failed to import triggers: " + e.getMessage());
            }
        });

        buttons.add(upload);
        StreamResource streamResource = new StreamResource("groups.json", () -> currentConfigAsStream());
        streamResource.setContentType("application/json");

        exportLink = new Anchor(streamResource, "");
        exportLink.getElement().setAttribute("download", true);
        exportLink.add(new Button("Export", new Icon(VaadinIcon.DOWNLOAD_ALT)));
        buttons.add(exportLink);

        add(buttons);



        add(buttons);
    }

    private void addJobGroup() {
        Dialog groupDialog = createGroupDialog();
        groupDialog.open();
    }

    private void editJobGroup(){
        selectedGroups = groupGrid.getSelectionModel().getSelectedItems();
        log.info("edit groups: {}",selectedGroups);
        JobGroup[] groupArr = selectedGroups.toArray(JobGroup[]::new);
        Dialog editDialog = createEditGroupDialog(groupArr[0]);
        editDialog.open();
    }

    private void deleteGroup(){
        selectedGroups = groupGrid.getSelectionModel().getSelectedItems();
        selectedGroups.forEach(jobGroup -> {
            groupDao.delete(jobGroup);
        });
        update();
    }

    private Dialog createGroupDialog() {
        Dialog dialog = new Dialog();
        dialog.setWidth("600px");
        Label label = new Label();

        List<JobGroup> jobGroups = groupDao.getAllJobGroups();
        List<String> jobNameList = new ArrayList<>();
        jobGroups.forEach(g -> {
            jobNameList.add(g.getName());
        });
        VerticalLayout layout = new VerticalLayout();
        layout.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        layout.setPadding(true);
        layout.setSpacing(true);

        Binder<JobGroup> binder = new Binder<>(JobGroup.class);

        TextField nameTextField = new TextField();
        nameTextField.setLabel("Group name");
        nameTextField.setRequired(true);
        nameTextField.setRequiredIndicatorVisible(true);
        nameTextField.setWidthFull();
        binder.forField(nameTextField)
                .withValidator(t -> !jobNameList.contains(t), "Group already exists")
                .bind(JobGroup::getName, JobGroup::setName);

        TextField regexTextField = new TextField();
        regexTextField.setLabel("Regex");
        regexTextField.setRequired(true);
        regexTextField.setRequiredIndicatorVisible(true);
        regexTextField.setWidthFull();
        binder.forField(regexTextField).bind(JobGroup::getRegex, JobGroup::setRegex);


        HorizontalLayout buttons = new HorizontalLayout();
        buttons.setWidthFull();
        buttons.setJustifyContentMode(FlexComponent.JustifyContentMode.END);

        Button confirmButton = new Button("Confirm", clickEvent -> {
            if (nameTextField.getValue().length() > 0 && regexTextField.getValue().length() > 0){
                var newGroup = groupDao.createJobGroup(nameTextField.getValue(), regexTextField.getValue());
                if (newGroup != null){
                    groupDao.save(newGroup);
                    update();
                    dialog.close();
                }else{
                    label.setText("Not a valid regexp!");
                }
            }else{
                label.setText("Both fields are required!");
            }
        });
        confirmButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancelButton = new Button("Cancel", clickEvent -> {
            dialog.close();
        });
        cancelButton.addThemeVariants(ButtonVariant.LUMO_ERROR);

        buttons.add(label, confirmButton, cancelButton);
        layout.add(nameTextField, regexTextField, buttons);
        dialog.add(layout);
        return dialog;
    }

    private Dialog createEditGroupDialog(JobGroup group){
        Dialog dialog = new Dialog();
        dialog.setWidth("600px");
        Label label = new Label();

        List<JobGroup> jobGroups = groupDao.getAllJobGroups();
        List<String> jobNameList = new ArrayList<>();
        jobGroups.forEach(g -> {
            jobNameList.add(g.getName());
        });
        VerticalLayout layout = new VerticalLayout();
        layout.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        layout.setPadding(true);
        layout.setSpacing(true);

        Binder<JobGroup> binder = new Binder<>(JobGroup.class);

        TextField nameTextField = new TextField();
        nameTextField.setLabel("Group name");
        nameTextField.setValue(group.getName());
        nameTextField.setRequired(true);
        nameTextField.setRequiredIndicatorVisible(true);
        nameTextField.setWidthFull();
        binder.forField(nameTextField)
                .withValidator(t -> !jobNameList.contains(t), "Group already exists")
                .withValidator(t -> t.length() > 0, "Group cannot be null")
                .bind(JobGroup::getName, JobGroup::setName);

        TextField regexTextField = new TextField();
        regexTextField.setLabel("Regex");
        regexTextField.setValue(group.getRegex());
        regexTextField.setRequired(true);
        regexTextField.setRequiredIndicatorVisible(true);
        regexTextField.setWidthFull();
        binder.forField(regexTextField)
                .withValidator(t -> t.length() > 0, "Regex cannot be null")
                .bind(JobGroup::getRegex, JobGroup::setRegex);

        HorizontalLayout buttons = new HorizontalLayout();
        buttons.setWidthFull();
        buttons.setJustifyContentMode(FlexComponent.JustifyContentMode.END);

        Button confirmButton = new Button("Update", clickEvent -> {
            var newGroup = groupDao.createJobGroup(nameTextField.getValue(), regexTextField.getValue());
            if (newGroup != null){
                group.setName(newGroup.getName());
                group.setRegex(newGroup.getRegex());
                group.setJobNames(newGroup.getJobNames());
                if (group.getName() != null && group.getRegex() != null){
                    groupDao.updateGroup(group);
                    update();
                    dialog.close();
                }
            }else{
                label.setText("Not a valid regexp!");
            }
        });

        UnorderedList unorderedList = new UnorderedList();

        Button previewButton = new Button("Preview", clickEvent -> {
            unorderedList.removeAll();
            label.setText("");
            var tempGroup = new JobGroup();
            tempGroup.setName(nameTextField.getValue());
            tempGroup.setRegex(regexTextField.getValue());
            List<String> jobNames = preview(tempGroup);
            if (jobNames != null){
                if (jobNames.size() > 0){
                    jobNames.forEach(name -> {
                        ListItem item = new ListItem();
                        item.add(name);
                        unorderedList.add(item);
                    });
                }else{
                    label.setText("The regex did not match any jobs.");
                }
            }else{
                label.setText("Invalid regex.");
            }
        });

        confirmButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancelButton = new Button("Cancel", clickEvent -> {
           dialog.close();
        });
        cancelButton.addThemeVariants(ButtonVariant.LUMO_ERROR);

        buttons.add(label, unorderedList, previewButton, confirmButton, cancelButton);
        layout.add(nameTextField, regexTextField, buttons);
        dialog.add(layout);
        return dialog;
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

    @Override
    public void selectionChange(SelectionEvent<Grid<JobGroup>, JobGroup> selectionEvent) {
        updateButtonState();
    }

    private void update() {
        groups = groupDao.getAllJobGroups();
        groupGrid.setItems(groups);
        updateButtonState();
    }

    private void updateButtonState() {
        if (SecurityUtils.isUserInRole("ADMIN")) {
            selectedGroups = groupGrid.getSelectionModel().getSelectedItems();
            createGroupButton.setEnabled(true);
            editButton.setEnabled(selectedGroups.size() == 1);
            removeButton.setEnabled(selectedGroups.size() > 0);
            exportLink.setEnabled(selectedGroups.size() > 0);
        } else {
            createGroupButton.setEnabled(false);
            editButton.setEnabled(false);
            removeButton.setEnabled(false);
            exportLink.setEnabled(false);
        }
    }

    public void save(JobGroup[] groups){
        for (JobGroup jg : groups) {
            if (this.groups.contains(jg)){
                jg.setName(jg.getName()+"(2)");
            }
            jg.setId(null);
            groupDao.save(jg);
        }
        update();
    }

    private List<String> preview(JobGroup group){
        var tempGroup = groupDao.createJobGroup(group.getName(), group.getRegex());
        if (tempGroup == null){
            return null;
        }else if (tempGroup.getJobNames().size() == 0){
            return new ArrayList<>();
        }else{
            return tempGroup.getJobNames();
        }
    }


    private InputStream currentConfigAsStream() {
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        try {
            String json = mapper.writeValueAsString(groupGrid.getSelectionModel().getSelectedItems());
            return new ByteArrayInputStream(json.getBytes("UTF-8"));
        } catch (Exception e) {
            throw new RuntimeException("Failed to stream scheduling configuration", e);
        }

    }


}
