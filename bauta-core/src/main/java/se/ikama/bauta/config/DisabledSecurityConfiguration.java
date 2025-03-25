package se.ikama.bauta.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import com.vaadin.flow.spring.security.VaadinWebSecurity;

import lombok.extern.slf4j.Slf4j;

@Profile("dev")
@Configuration
@EnableMethodSecurity(securedEnabled = false, prePostEnabled = false)
@Slf4j
public class DisabledSecurityConfiguration extends VaadinWebSecurity {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrd -> csrd.disable());
        
        http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
    
}
