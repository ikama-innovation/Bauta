package se.ikama.bauta.security;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinServiceInitListener;
import com.vaadin.flow.server.VaadinSession;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.LocaleUtils;
import org.springframework.stereotype.Component;
import se.ikama.bauta.ui.LoginView;

import javax.servlet.http.Cookie;
import java.util.Arrays;
import java.util.Locale;

@Slf4j
@Component
public class ConfigureUIServiceInitListener implements VaadinServiceInitListener {
    private final String LOCALE_COOKIE_NAME = "bauta-locale";

    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.getSource().addUIInitListener(uiEvent -> {
            final UI ui = uiEvent.getUI();
            ui.addBeforeEnterListener(this::beforeEnter);
        });

        // Locale/Language handling. If there is a cookie with the saved locale,
        // we override the default session locale and apply the saved locale.
        event.getSource().addSessionInitListener(sessionEvent -> {
            final VaadinRequest request = sessionEvent.getRequest();
            final VaadinSession session = sessionEvent.getSession();

            // change the cookie name to the name that you actually use ;)
            Cookie localeCookie = request.getCookies() != null ? Arrays.stream(request.getCookies()).filter(c -> c.getName().equals(LOCALE_COOKIE_NAME)).findFirst().orElse(null) : null;

            if (localeCookie != null) {

                log.debug("Found cookie {}", localeCookie.getValue());

                Locale l = LocaleUtils.toLocale(localeCookie.getValue());
                log.debug("Setting session locale: {}", l);
                session.setLocale(l);
            }
        });
    }

    /**
     * Reroutes the user if (s)he is not authorized to access the view.
     *
     * @param event before navigation event with event details
     */
    private void beforeEnter(BeforeEnterEvent event) {
        if (!SecurityUtils.isAccessGranted(event.getNavigationTarget())) {
            if (SecurityUtils.isUserLoggedIn()) {
                //event.rerouteTo(HomeView.class);
                event.rerouteTo("login", "no_roles");
            } else {
                event.rerouteTo(LoginView.class);
            }
        }
    }
}

