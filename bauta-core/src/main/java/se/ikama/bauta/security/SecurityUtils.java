package se.ikama.bauta.security;

import com.vaadin.flow.server.HandlerHelper;
import com.vaadin.flow.shared.ApplicationConstants;

import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.ContextLoader;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public final class SecurityUtils {
    
    /**
     * Tests if the request is an internal framework request. The test consists of
     * checking if the request parameter is present and if its value is consistent
     * with any of the request types know.
     *
     * @param request
     *            {@link HttpServletRequest}
     * @return true if is an internal framework request. False otherwise.
     */
    public static boolean isFrameworkInternalRequest(HttpServletRequest request) {
        final String parameterValue = request.getParameter(ApplicationConstants.REQUEST_TYPE_PARAMETER);
        return parameterValue != null
                && Stream.of(HandlerHelper.RequestType.values()).anyMatch(r -> r.getIdentifier().equals(parameterValue));
    }

    public static boolean isAccessGranted(Class<?> securedClass) {
        // Allow if no roles are required.
        Secured secured = AnnotationUtils.findAnnotation(securedClass, Secured.class);
        if (secured == null) {
            return true; //
        }

        // lookup needed role in user roles
        List<String> allowedRoles = Arrays.asList(secured.value());
        Authentication userAuthentication = SecurityContextHolder.getContext().getAuthentication();
        if (userAuthentication == null) return false;
        else
            return userAuthentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch(allowedRoles::contains);
    }

    public static boolean isUserInRole(String role) {
        String roleWithPrefix = "ROLE_"+role;
        Authentication userAuthentication = SecurityContextHolder.getContext().getAuthentication();

        if (userAuthentication == null) return false;
        else
            return userAuthentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch(roleWithPrefix::equals);
    }

    public static boolean isUserLoggedIn() {
        return currentUser() != null;
    }


    public static String currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            log.info("No authentication found");
            return null;
        }
        if (authentication instanceof AnonymousAuthenticationToken) {
            log.info("User is anonymous");
            return null;
        }
        return authentication.getName();
    }

    public static Collection<String> currentUserRoles() {
		try {
			Set<String> roles = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
				     .map(r -> r.getAuthority()).collect(Collectors.toSet());
			return roles;
			
		}
		catch(Exception e) {
			return null;
		}
	}
    
    public static boolean isDevMode(Environment env) {
        
        return env.acceptsProfiles(Profiles.of("dev"));
    }

    public static boolean isProdMode() {
        WebApplicationContext context = ContextLoader.getCurrentWebApplicationContext();
        if (context == null) {
            return false;
        }
        Environment env = context.getEnvironment();
        return env.acceptsProfiles(Profiles.of("prod"));
    }

    public static String getServerInfo() {
        WebApplicationContext context = ContextLoader.getCurrentWebApplicationContext();
        if (context == null) {
            return "No application context";
        }
        ApplicationContext appContext = WebApplicationContextUtils
                .getRequiredWebApplicationContext(context.getServletContext());
        return appContext.getId();
    }
    
}
