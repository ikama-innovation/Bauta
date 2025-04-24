package se.ikama.bauta;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.batch.BatchAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.component.page.Viewport;
import com.vaadin.flow.shared.communication.PushMode;
import com.vaadin.flow.shared.ui.Transport;
import com.vaadin.flow.theme.Theme;
import com.vaadin.flow.theme.lumo.Lumo;

@ComponentScan(basePackages = "se.ikama.bauta")
@SpringBootApplication(exclude = BatchAutoConfiguration.class)
@Theme(value = "bauta", variant = Lumo.DARK)
@Push(value = PushMode.AUTOMATIC, transport = Transport.LONG_POLLING)
@Viewport("width=device-width, minimum-scale=1, initial-scale=1,user-scalable=yes, viewport-fit=cover")
public class BautaApplication implements AppShellConfigurator {
    public static void main(String[] args) {
        SpringApplication.run(BautaApplication.class, args);
    }
}