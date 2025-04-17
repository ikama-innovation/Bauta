package se.ikama.bauta.ui;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.StreamResource;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.theme.lumo.Lumo;

import lombok.extern.slf4j.Slf4j;
import se.ikama.bauta.core.BautaManager;
import se.ikama.bauta.security.SecurityUtils;

@Route("login")
@PageTitle("Login")
@AnonymousAllowed
@Slf4j
public class LoginView extends Div implements BeforeEnterObserver {

    
    @Value("${bauta.application.title}")
	private String applicationTitle;

    private Paragraph loggedOutMessage = new Paragraph();
    private Span serverInfo = new Span();
    private Paragraph welcomeMessage = new Paragraph();
    Anchor loginLink;

    @Autowired
	BautaManager bautaManager;



    @Value("${bauta.security.idp.authLoginPage:/oauth2/authorization/keycloak}")
    private final String authLoginPage = "/oauth2/authorization/keycloak";

    private void setTheme(boolean dark) {
        var js = "document.documentElement.setAttribute('theme', $0)";
        getElement().executeJs(js, dark ? Lumo.DARK : Lumo.LIGHT);
    }

    @Override
	protected void onAttach(AttachEvent attachEvent) {
		setTheme(true);
        serverInfo.setText(bautaManager.getShortServerInfo());
        welcomeMessage.setText("Welcome to " + applicationTitle);
        loginLink.setHref(authLoginPage);

    }


    public LoginView() {
        setClassName("login-view");
        var loginOverlay = new VerticalLayout();
        loginOverlay.setMargin(false);
        loginOverlay.setPadding(false);
        loginOverlay.setSpacing(false);
        loginOverlay.setMaxWidth("300px");
        loginOverlay.setClassName("login-overlay");
        Image img = new Image(new StreamResource("bauta_splash.jpg",
				() -> getClass().getResourceAsStream("/static/images/bauta_splash.jpg")), "Bauta splash");
        
		// Header
        var header = new Div();
        header.setClassName("login-header");
        header.add(img);
        loginOverlay.add(header);
        // Body
        var body = new Div();
        body.setClassName("login-body");
        serverInfo.setClassName("server-info");
        body.add(serverInfo);
        loggedOutMessage = new Paragraph("You have been logged out.");
        loggedOutMessage.setVisible(false);
        body.add(loggedOutMessage);
        body.add(welcomeMessage);
        

        // Navigate to IDP login page
        loginLink = new Anchor(authLoginPage, "Login with IDP");
        
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
                loggedOutMessage.setVisible(true);
        }
        else {
            log.debug("Not logged out");
            loggedOutMessage.setVisible(false);
        }
            
    }
}