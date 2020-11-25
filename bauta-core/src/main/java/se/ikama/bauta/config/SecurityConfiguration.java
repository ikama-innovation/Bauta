package se.ikama.bauta.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import se.ikama.bauta.security.SecurityConfig;
import se.ikama.bauta.security.SecurityUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;

@EnableWebSecurity
@Configuration()
@Slf4j
public class SecurityConfiguration extends WebSecurityConfigurerAdapter {

    private static final String LOGIN_PROCESSING_URL = "/ui/login";
    private static final String LOGIN_FAILURE_URL = "/ui/login?error=login_failed";
    private static final String LOGIN_URL = "/ui/login";
    private static final String LOGOUT_SUCCESS_URL = "/ui/login";
    private static final String LOGIN_SUCCESS_URL = "/ui/";

    @Value("${bauta.security.enabled:false}")
    private boolean securityEnabled;

    @Value("${bauta.security.configFilePath:}")
    private String securityConfigFilePath;

    /**
     * Require login to access internal pages and configure login form.
     */
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        if (!securityEnabled) {
            log.info("Security is disabled");
            http.authorizeRequests().anyRequest().permitAll();
            return;
        }
        // Not using Spring CSRF here to be able to use plain HTML for the login page
        http.csrf().disable()

                // Register our CustomRequestCache, that saves unauthorized access attempts, so
                // the user is redirected after login.
                .requestCache().requestCache(new CustomRequestCache())

                // Restrict access to our application.
                .and().authorizeRequests()

                // Allow all flow internal requests.
                .requestMatchers(SecurityUtils::isFrameworkInternalRequest).permitAll()

                // Allow all requests by logged in users.
                .anyRequest().authenticated()

                // Configure the login page.
                .and().formLogin().loginPage(LOGIN_URL).permitAll().loginProcessingUrl(LOGIN_PROCESSING_URL).failureUrl(LOGIN_FAILURE_URL).defaultSuccessUrl(LOGIN_SUCCESS_URL)

                // Configure logout
                .and().logout().logoutSuccessUrl(LOGOUT_SUCCESS_URL);
    }

    @Override
    protected void configure(AuthenticationManagerBuilder builder) throws Exception {
        if (!securityEnabled) return;
        builder.userDetailsService(userDetailsService());

    }


    @Bean
    @Override
    public UserDetailsService userDetailsService() {
        if (securityConfigFilePath != null) {
            File file = new File(securityConfigFilePath);
            if (!file.exists()) {
                throw new RuntimeException("bauta.security.configFilePath points to a file that does not exist: " + securityConfigFilePath);
            }
            else {
                try {
                    ObjectMapper om = new ObjectMapper();
                    SecurityConfig securityConfig = om.readValue(file, SecurityConfig.class);
                    InMemoryUserDetailsManager udm = new InMemoryUserDetailsManager();
                    for (se.ikama.bauta.security.User user : securityConfig.getUsers()) {
                        udm.createUser(User.withUsername(user.getUsername()).password(user.getPassword()).roles(user.getRoles()).build());
                    }
                    return udm;
                }
                catch(Exception e) {
                    throw new RuntimeException("Failed to read security config file", e);
                }
            }
        }
        else {
            return new InMemoryUserDetailsManager();
        }
    }

    /**
     * Allows access to static resources, bypassing Spring security.
     */
    @Override
    public void configure(WebSecurity web) {
        if (!securityEnabled) {
            web.ignoring().anyRequest();
            return;
        };
        web.ignoring().antMatchers(
                // Vaadin Flow static resources
                "/ui/VAADIN/**",
                "/VAADIN/**",

                // the standard favicon URI
                "/favicon.ico",

                // the robots exclusion standard
                "/robots.txt",

                // web application manifest
                "/manifest.webmanifest", "/sw.js", "/offline-page.html",

                // icons and images
                "/icons/**", "/images/**",

                // (development mode) static resources
                "**/frontend/**",

                // (development mode) webjars
                "/webjars/**",

                // (development mode) H2 debugging console
                "/reports/**",
                "/static/**",

                // (production mode) static resources
                "/frontend-es5/**", "/frontend-es6/**");
    }

}

class CustomRequestCache extends HttpSessionRequestCache {
    /**
     * {@inheritDoc}
     * <p>
     * If the method is considered an internal request from the framework, we skip
     * saving it.
     *
     * @see SecurityUtils#isFrameworkInternalRequest(HttpServletRequest)
     */
    @Override
    public void saveRequest(HttpServletRequest request, HttpServletResponse response) {
        if (!SecurityUtils.isFrameworkInternalRequest(request)) {
            super.saveRequest(request, response);
        }
    }

}

