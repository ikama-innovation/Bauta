package se.ikama.bauta.config;

import com.vaadin.flow.spring.security.VaadinWebSecurity;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
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
     * Is PKCE enabled 
     */
    @Value("${bauta.security.idp.pkceEnabled:false}")
    private boolean idpPkceEnabled;
    
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

    @Autowired
    ClientRegistrationRepository repo;


    @Override
    protected void configure(HttpSecurity http) throws Exception {
        log.info("IDP roles: admin={}, batch-view={}, batch-execute={}", idpRoleAdmin, idpRoleBatchView,
                idpRoleBatchExecute);
        log.info("IDP auth login page: {}", idpAuthLoginPage);
        http.authorizeHttpRequests(
                authorize -> authorize.requestMatchers(new AntPathRequestMatcher("/static/**/*")).permitAll());
        http.authorizeHttpRequests(
                authorize -> authorize.requestMatchers(new AntPathRequestMatcher("/reports/**/*")).permitAll());

        http.logout(logout -> logout.logoutSuccessUrl("{baseUrl}/ui/login").clearAuthentication(true)
                .invalidateHttpSession(true).deleteCookies("JSESSIONID"));
        http.csrf(csrf -> csrf.disable());
        super.configure(http);
        setOAuth2LoginPage(http, idpAuthLoginPage, "{baseUrl}/ui/login");
        
        // PKCE support
        if (idpPkceEnabled) {
            log.info("Enabling PKCE");
            var base_uri = OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI;
            var resolver = new DefaultOAuth2AuthorizationRequestResolver(repo, base_uri);
            resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());

            http.oauth2Login(login -> login.authorizationEndpoint(authorizationEndpointConfig -> authorizationEndpointConfig.authorizationRequestResolver(resolver)));
        }

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
    public GrantedAuthoritiesMapper grantedAuthoritiesMapper() {
        return authorities -> {
            Set<String> unmappedRoles = new HashSet<String>();
            for (var authority : authorities) {
                log.debug("Authority: {}:{}", authority.getClass(), authority);
                if (OAuth2UserAuthority.class.isAssignableFrom(authority.getClass())) {
                    var oidcUserAuthority = (OAuth2UserAuthority) authority;
                    log.debug("User attributes: {}", oidcUserAuthority.getAttributes());
                    extractRolesFromClaims(oidcUserAuthority.getAttributes(), unmappedRoles);      
                } else if (authority instanceof SimpleGrantedAuthority) {
                    SimpleGrantedAuthority simpleGrantedAuthority = (SimpleGrantedAuthority) authority;
                    unmappedRoles.add(simpleGrantedAuthority.getAuthority());
                    log.debug("SimpleGrantedAuthority: {}", simpleGrantedAuthority.getAuthority());
                }
            }
            Collection<GrantedAuthority> mappedAuthorities = mapClaimsToAuthorities(unmappedRoles);
            return mappedAuthorities;
        };
    }

    /**
     * Extracts roles from the claims. Supports Keycloak and Ping Federate style of providing roles.
     * @param claims
     * @param roles
     */
    private void extractRolesFromClaims(Map<String, Object> claims, Set<String> roles) {
        if (claims.containsKey("realm_access")) {
            Map<String, Object> realmAccess = (Map<String, Object>) claims.get("realm_access");
            if (realmAccess.containsKey("roles")) {
                @SuppressWarnings("unchecked")
                Collection<String> r = (Collection<String>) realmAccess.get("roles");
                roles.addAll(r);
            }
        }
        // Ping federate style
        if (claims.containsKey("roles")) {
            @SuppressWarnings("unchecked")
            var r = (Collection<String>) claims.get("roles");
            roles.addAll(r);
        }
    }

    Collection<GrantedAuthority> mapClaimsToAuthorities(Collection<String> roles) {
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
