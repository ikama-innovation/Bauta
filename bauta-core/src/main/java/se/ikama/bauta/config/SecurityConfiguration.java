package se.ikama.bauta.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaadin.flow.spring.security.VaadinWebSecurity;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import se.ikama.bauta.security.SecurityConfig;
import se.ikama.bauta.ui.LoginView;
import java.io.File;

@EnableWebSecurity
@Configuration
@Slf4j
public class SecurityConfiguration extends VaadinWebSecurity {

    @Value("${bauta.security.enabled:false}")
    private boolean securityEnabled;

    @Value("${bauta.security.configFilePath:}")
    private String securityConfigFilePath;

    @Override
    protected void configure(HttpSecurity http) throws Exception {

        http.authorizeHttpRequests(
                authorize -> authorize.requestMatchers(new AntPathRequestMatcher("/static/**/*")).permitAll());
        http.authorizeHttpRequests(
                    authorize -> authorize.requestMatchers(new AntPathRequestMatcher("/reports/**/*")).permitAll());
    
        http.logout(logout -> logout
                .logoutRequestMatcher(new AntPathRequestMatcher("/ui/logout"))
                .logoutSuccessUrl("/ui/login")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID"));
        super.configure(http);
        setLoginView(http, LoginView.class);

    }

    @Bean
    public UserDetailsService userDetailsService() {
        if (securityEnabled && StringUtils.isNotEmpty(securityConfigFilePath)) {
            log.info("Security enabled, reading users from file: " + securityConfigFilePath);
            File file = new File(securityConfigFilePath);
            if (!file.exists()) {
                throw new RuntimeException("bauta.security.configFilePath points to a file that does not exist: "
                        + securityConfigFilePath);
            } else {
                try {
                    ObjectMapper om = new ObjectMapper();
                    SecurityConfig securityConfig = om.readValue(file, SecurityConfig.class);
                    InMemoryUserDetailsManager udm = new InMemoryUserDetailsManager();
                    for (se.ikama.bauta.security.User user : securityConfig.getUsers()) {
                        udm.createUser(User.withUsername(user.getUsername()).password(user.getPassword())
                                .roles(user.getRoles()).build());
                    }
                    return udm;
                } catch (Exception e) {
                    throw new RuntimeException("Failed to read security config file", e);
                }
            }
        } else {
            log.info("Security disabled, using default users");
            return new InMemoryUserDetailsManager();
        }
    }
}
