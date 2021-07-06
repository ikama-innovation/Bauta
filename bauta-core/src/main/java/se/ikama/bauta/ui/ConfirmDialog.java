package se.ikama.bauta.ui;

import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

public class ConfirmDialog <T> {
    ConfirmDialogListener<T> callback;
    T payload;
    Dialog dialog;
    HorizontalLayout layout;

    ConfirmDialog(String text, T payload, ConfirmDialogListener<T> callback){
        this.callback = callback;
        this.payload = payload;
        this.dialog = new Dialog((new Text(text)));
        this.layout = new HorizontalLayout();
        this.dialog.setCloseOnOutsideClick(false);

        Button confirmButton = new Button("Confirm", onClick -> {
            execute();
            dialog.close();
        });

        Button cancelButton = new Button("Cancel", onClick -> {
            dialog.close();
        });

        confirmButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        cancelButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
        layout.setSpacing(true);
        layout.setPadding(true);
        layout.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        layout.add(confirmButton);
        layout.add(cancelButton);
        dialog.add(layout);
    }

    public void execute() {
        callback.confirm(payload);
    }

    public void open() {
        dialog.open();
    }

    public interface ConfirmDialogListener<T> {
        void confirm(T t);
    }
}
