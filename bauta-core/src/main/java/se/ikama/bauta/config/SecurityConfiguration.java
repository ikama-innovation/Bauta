package se.ikama.bauta.config;

import com.vaadin.flow.spring.security.VaadinWebSecurity;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Profile("!dev")
@EnableWebSecurity
@Configuration
@Slf4j
public class SecurityConfiguration extends VaadinWebSecurity {


    @Value("${bauta.security.configFilePath:}")
    private String securityConfigFilePath;

    /**
     * The IDP role that is considered an admin role. Will be mapped to the internal
     * ROLE_ADMIN role".
     */
    @Value("${bauta.security.idp.role.admin:bauta-admin}")
    private String idpRoleAdmin;

    /**
     * The IDP role that enables users to view the result of batch jobs. Will be
     * mapped to the internal ROLE_BATCH_VIEW role".
     */
    @Value("${bauta.security.idp.role.batch-view:bauta-batch-view}")
    private String idpRoleBatchView;

    /**
     * The IDP role that enables users to execute batch jobs. Will be mapped to the
     * internal ROLE_BATCH_EXECUTE role".
     */
    @Value("${bauta.security.idp.role.batch-execute:bauta-batch-execute}")
    private String idpRoleBatchExecute;

    @Value("${bauta.security.idp.authLoginPage:/oauth2/authorization/keycloak}")
    private String idpAuthLoginPage;


    @Override
    protected void configure(HttpSecurity http) throws Exception {
        log.info("IDP roles: admin={}, batch-view={}, batch-execute={}", idpRoleAdmin, idpRoleBatchView,
                idpRoleBatchExecute);
        log.info("IDP auth login page: {}", idpAuthLoginPage);
        http.authorizeHttpRequests(
                authorize -> authorize.requestMatchers(new AntPathRequestMatcher("/static/**/*")).permitAll());
        http.authorizeHttpRequests(
                authorize -> authorize.requestMatchers(new AntPathRequestMatcher("/reports/**/*")).permitAll());

        super.configure(http);
        setOAuth2LoginPage(http, idpAuthLoginPage);

        /*
         * http.logout(logout -> logout
         * .logoutRequestMatcher(new AntPathRequestMatcher("/ui/logout"))
         * .logoutSuccessUrl("/ui/login")
         * .invalidateHttpSession(true)
         * .deleteCookies("JSESSIONID"));
         */

        // setLoginView(http, LoginView.class);

    }

    @Bean
    public GrantedAuthoritiesMapper userAuthoritiesMapperForKeycloak() {
        return authorities -> {
            Set<GrantedAuthority> mappedAuthorities = new HashSet<>();
            for (var authority : authorities) {

                if (authority instanceof OidcUserAuthority) {
                    var oidcUserAuthority = (OidcUserAuthority) authority;
                    log.debug("OICD Authority: {}", oidcUserAuthority.getAuthority());
                    var userInfo = oidcUserAuthority.getUserInfo();
                    log.debug("User info: {}", userInfo.getClaims());
                    var idToken = oidcUserAuthority.getIdToken();
                    log.debug("Id token: {}", idToken.getClaims());

                    if (userInfo.hasClaim("realm_access")) {
                        var realmAccess = userInfo.getClaimAsMap("realm_access");
                        var roles = (Collection<String>) realmAccess.get("roles");
                        mappedAuthorities.addAll(generateAuthoritiesFromClaim(roles));
                    }
                } else if (authority instanceof SimpleGrantedAuthority) {
                    SimpleGrantedAuthority simpleGrantedAuthority = (SimpleGrantedAuthority) authority;
                    log.debug("SimpleGrantedAuthority: {}", simpleGrantedAuthority.getAuthority());

                } else {
                    log.debug("Mapping OAuth2 user");
                    var oauth2UserAuthority = (OAuth2UserAuthority) authority;
                    Map<String, Object> userAttributes = oauth2UserAuthority.getAttributes();
                    // Keyckloak style
                    if (userAttributes.containsKey("realm_access")) {
                        var realmAccess = (Map<String, Object>) userAttributes.get("realm_access");
                        var roles = (Collection<String>) realmAccess.get("roles");
                        mappedAuthorities.addAll(generateAuthoritiesFromClaim(roles));
                    }
                    // Ping federate style
                    if (userAttributes.containsKey("roles")) {
                        var roles = (Collection<String>) userAttributes.get("roles");
                        mappedAuthorities.addAll(generateAuthoritiesFromClaim(roles));
                    }
                }
            }

            return mappedAuthorities;
        };
    }

    Collection<GrantedAuthority> generateAuthoritiesFromClaim(Collection<String> roles) {
        log.debug("Mapping roles: " + roles);
        Map<String, String> roleMap = Map.of(
                idpRoleAdmin, "ROLE_ADMIN",
                idpRoleBatchView, "ROLE_BATCH_VIEW",
                idpRoleBatchExecute, "ROLE_BATCH_EXECUTE");
        Collection<GrantedAuthority> out = roles.stream()
                .map(role -> new SimpleGrantedAuthority(roleMap.get(role) != null ? roleMap.get(role) : role))
                .collect(Collectors.toList());
        log.debug("Mapped roles: " + out);
        return out;
    }
     
}
