package se.ikama.bauta.ui;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.StreamResource;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.theme.lumo.Lumo;

import lombok.extern.slf4j.Slf4j;
import se.ikama.bauta.security.SecurityUtils;

@Route("login")
@PageTitle("Login")
@AnonymousAllowed
@Slf4j
public class LoginView extends Div implements BeforeEnterObserver {

    boolean loggedOut = false;

    Paragraph loggedOutMessage;
    

    // URL that Spring Security uses to connect to Google services
    private static final String OAUTH_URL = "/oauth2/authorization/keycloak";

    private void setTheme(boolean dark) {
        var js = "document.documentElement.setAttribute('theme', $0)";

        getElement().executeJs(js, dark ? Lumo.DARK : Lumo.LIGHT);
    }

    @Override
	protected void onAttach(AttachEvent attachEvent) {
		setTheme(true);
    }


    public LoginView() {
        setClassName("login-view");
        var loginOverlay = new VerticalLayout();
        loginOverlay.setMargin(false);
        loginOverlay.setPadding(false);
        loginOverlay.setMaxWidth("300px");
        loginOverlay.setClassName("login-overlay");
        Image img = new Image(new StreamResource("myimage.png",
				() -> getClass().getResourceAsStream("/static/images/bauta-logo-light.png")), "Bauta logo");
        
		// Header
        var header = new Div();
        header.setClassName("login-header");
        header.add(img);
        loginOverlay.add(header);
        // Body
        var body = new VerticalLayout();
        body.setAlignItems(Alignment.CENTER);
        loggedOutMessage = new Paragraph("You have been logged out.");
        loggedOutMessage.setVisible(false);
        body.add(loggedOutMessage);
        
        body.add(new Paragraph("Welcome to Bauta!"));

        // Navigate to OATH URL
        Anchor loginLink = new Anchor(OAUTH_URL, "Login with IDP");
        
        loginLink.getElement().getThemeList().add("primary"); // or "secondary"
        loginLink.getElement().getClassList().add("vaadin-button"); // make it visually a button
        loginLink.setRouterIgnore(true);
        
        body.add(loginLink);
        loginOverlay.add(body);

        add(loginOverlay);
        
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (SecurityUtils.isUserLoggedIn()) {
            log.debug("User is logged in. Redirecting to /");
            event.forwardTo("/");
            return;
        }
        var logoutPresent = event.getLocation().getQueryParameters().getSingleParameter("logout");
        if (logoutPresent.isPresent()) {
            log.debug("Logged out");
                loggedOutMessage.setVisible(loggedOut = true);
        }
        else {
            log.debug("Not logged out");
            loggedOutMessage.setVisible(loggedOut = false);
        }
            
    }
}