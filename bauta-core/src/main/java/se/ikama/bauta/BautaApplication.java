package se.ikama.bauta;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.batch.BatchAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.component.page.Viewport;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;
import com.vaadin.flow.shared.communication.PushMode;
import com.vaadin.flow.shared.ui.Transport;
import com.vaadin.flow.theme.Theme;
import com.vaadin.flow.theme.lumo.Lumo;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@ComponentScan(basePackages = "se.ikama.bauta")
@SpringBootApplication(exclude = BatchAutoConfiguration.class)
@Theme(value = "bauta", variant = Lumo.DARK)
@Push(value = PushMode.DISABLED)          
@Viewport("width=device-width, minimum-scale=1, initial-scale=1,user-scalable=yes, viewport-fit=cover")
public class BautaApplication implements AppShellConfigurator, VaadinServiceInitListener{
    @Value("${bauta.vaadin.push.transport:LONG_POLLING}")
    private String pushTransport;
    public static void main(String[] args) {
        SpringApplication.run(BautaApplication.class, args);
    }

    @Override
    public void serviceInit(ServiceInitEvent event) {
        log.info("ServiceInitEvent");
        event.getSource().addUIInitListener(uiEvent -> {
            uiEvent.getUI().addBeforeEnterListener(attachEvent -> {
                UI ui = attachEvent.getUI();
                if ("LONG_POLLING".equals(pushTransport)) {
                    log.info("Setting Vaadin push transport to: LONG_POLLING");
                    ui.getPushConfiguration().setTransport(Transport.LONG_POLLING);
                } else if ("WEBSOCKET".equals(pushTransport)) {
                    log.info("Setting Vaadin push transport to: WEBSOCKET");
                    ui.getPushConfiguration().setTransport(Transport.WEBSOCKET);
                    
                }
                else {
                    log.info("Setting Vaadin push transport to default: WEBSOCKET_XHR");
                    ui.getPushConfiguration().setTransport(Transport.WEBSOCKET_XHR);
                }
                ui.getPushConfiguration().setPushMode(PushMode.AUTOMATIC);
            });
        });
    }
}