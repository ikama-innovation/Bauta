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
    

    ConfirmDialog(String text, T payload, ConfirmDialogListener<T> callback){
        this.callback = callback;
        this.payload = payload;
        this.dialog = new Dialog(new Text(text));
        this.dialog.setCloseOnOutsideClick(false);

        HorizontalLayout buttonLayout = new HorizontalLayout();
        Button confirmButton = new Button("Confirm", onClick -> {
            execute();
            dialog.close();
        });

        Button cancelButton = new Button("Cancel", onClick -> {
            dialog.close();
        });

        confirmButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        buttonLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        buttonLayout.add(confirmButton);
        buttonLayout.add(cancelButton);
        buttonLayout.getElement().getStyle().set("margin-top","40px");
        dialog.add(buttonLayout);
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
