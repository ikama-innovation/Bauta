package se.ikama.bauta.ui;

import com.vaadin.flow.component.html.Label;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;

public class UIUtils {
    public static void showErrorMessage(String message) {
        Label label = new Label(message);
        Notification notification = new Notification(label);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        notification.setPosition(Notification.Position.BOTTOM_START);
        notification.setDuration(10000);
        notification.open();
    }
    public static void showInfoMessage(String message) {
        Label label = new Label(message);
        Notification notification = new Notification(label);
        notification.addThemeVariants(NotificationVariant.LUMO_PRIMARY);
        notification.setPosition(Notification.Position.BOTTOM_START);
        notification.setDuration(10000);
        notification.open();
    }
}
