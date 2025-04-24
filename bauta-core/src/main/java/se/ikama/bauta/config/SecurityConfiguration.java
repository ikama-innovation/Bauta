package se.ikama.bauta.config;

import com.vaadin.flow.spring.security.VaadinWebSecurity;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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

    @Value("${server.servlet.context-path:/}")
    private String contextPath;

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

        final var logoutSuccessUrl = StringUtils.equals("/", contextPath) ? "/ui/login?logout=true" :  contextPath + "/ui/login?logout=true";
        log.debug("Logout success URL: {}", logoutSuccessUrl);
        
        http.logout(logout -> logout.logoutSuccessUrl(logoutSuccessUrl).clearAuthentication(true)
                .invalidateHttpSession(true).deleteCookies("JSESSIONID"));
        http.csrf(csrf -> csrf.disable());
        super.configure(http);
        setOAuth2LoginPage(http, idpAuthLoginPage, "{baseUrl}/ui/login?logout=true");

        // PKCE support
        if (idpPkceEnabled) {
            log.info("Enabling PKCE");
            var base_uri = OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI;
            var resolver = new DefaultOAuth2AuthorizationRequestResolver(repo, base_uri);
            resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());

            http.oauth2Login(login -> {
                login.authorizationEndpoint(authorizationEndpointConfig -> authorizationEndpointConfig
                        .authorizationRequestResolver(resolver));
                login.userInfoEndpoint(info -> info.oidcUserService(customOidcUserService()));
            });
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
    public OAuth2UserService<OidcUserRequest, OidcUser> customOidcUserService() {
        return userRequest -> {
            // Load the default user
            OidcUser oidcUser = new OidcUserService().loadUser(userRequest);

            // Get access token
            OAuth2AccessToken accessToken = userRequest.getAccessToken();
            String accessTokenValue = accessToken.getTokenValue();
            // Get ID token
            var idToken = userRequest.getIdToken();
            String idTokenValue = idToken.getTokenValue();
            log.debug("accessTokenValue: {}", accessTokenValue);
            log.debug("idTokenValue: {}", idTokenValue);

            // Get the dynamic JWK Set URI from the configured provider
            //String jwkSetUri = userRequest.getClientRegistration()
            //        .getProviderDetails()
            //        .getJwkSetUri();
            String issuerUri = userRequest.getClientRegistration()
                    .getProviderDetails()
                    .getIssuerUri();

            String nameAttributeKey = userRequest.getClientRegistration()
                    .getProviderDetails()
                    .getUserInfoEndpoint()
                    .getUserNameAttributeName();

            // Decode the JWT access token dynamically using the JWK URI
            log.debug("Passing this issuerUri to jwtdecoder: {}", issuerUri);
            JwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
            Jwt accessTokenJwt = decoder.decode(accessTokenValue);
            Jwt idTokenJwt = decoder.decode(idTokenValue);
            // Extract claims and map to authorities
            Map<String, Object> accessTokenClaims = accessTokenJwt.getClaims();
            Map<String, Object> idTokenClaims = idTokenJwt.getClaims();
            log.debug("Decoded accessToken: {}", accessTokenClaims);
            log.debug("Decoded idToken: {}", idTokenClaims);
            Collection<GrantedAuthority> mappedAuthorities = mapClaimsToAuthorities(accessTokenClaims, idTokenClaims);
            log.debug("Mapped authorities: {}", mappedAuthorities);
            // Return a new OidcUser with your custom authorities
            return new DefaultOidcUser(mappedAuthorities, oidcUser.getIdToken(), oidcUser.getUserInfo(), nameAttributeKey);
        };
    }

    private Collection<GrantedAuthority> mapClaimsToAuthorities(Map<String, Object> accessTokenClaims,
            Map<String, Object> idTokenClaims) {
        Set<String> unmappedRoles = new HashSet<String>();
        extractRolesFromClaims(accessTokenClaims, unmappedRoles);
        extractRolesFromClaims(idTokenClaims, unmappedRoles);

        Collection<GrantedAuthority> mappedAuthorities = mapClaimsToAuthorities(unmappedRoles);
        return mappedAuthorities;
    }

    /*
     * @Bean
     * public GrantedAuthoritiesMapper grantedAuthoritiesMapper() {
     * return authorities -> {
     * Set<String> unmappedRoles = new HashSet<String>();
     * for (var authority : authorities) {
     * log.debug("Authority: {}:{}", authority.getClass(), authority);
     * if (OAuth2UserAuthority.class.isAssignableFrom(authority.getClass())) {
     * var oidcUserAuthority = (OAuth2UserAuthority) authority;
     * var principal = oidcUserAuthority.getAttributes().get(oidcUserAuthority.
     * getUserNameAttributeName());
     * log.debug("Principal: {}", principal);
     * var authorizedClient =
     * authorizedClientService.loadAuthorizedClient("keycloak",
     * principal.toString());
     * if (authorizedClient != null) {
     * String accessToken = authorizedClient.getAccessToken().getTokenValue();
     * log.debug("Access token: {}", accessToken);
     * }
     * log.debug("User attributes: {}", oidcUserAuthority.getAttributes());
     * if (authority instanceof OidcUserAuthority) {
     * log.debug("ID Token: {}",
     * ((OidcUserAuthority)oidcUserAuthority).getIdToken().getClaims());
     * log.debug("User Info: {}",
     * ((OidcUserAuthority)oidcUserAuthority).getUserInfo().getClaims());
     * }
     * extractRolesFromClaims(oidcUserAuthority.getAttributes(), unmappedRoles);
     * } else if (authority instanceof SimpleGrantedAuthority) {
     * SimpleGrantedAuthority simpleGrantedAuthority = (SimpleGrantedAuthority)
     * authority;
     * unmappedRoles.add(simpleGrantedAuthority.getAuthority());
     * log.debug("SimpleGrantedAuthority: {}",
     * simpleGrantedAuthority.getAuthority());
     * }
     * }
     * Collection<GrantedAuthority> mappedAuthorities =
     * mapClaimsToAuthorities(unmappedRoles);
     * return mappedAuthorities;
     * };
     * }
     */

    /**
     * Extracts roles from the claims. Supports Keycloak and Ping Federate style of
     * providing roles.
     * 
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
        Set<GrantedAuthority> out = new HashSet<>();
        for (String role : roles) {
            if (role.equalsIgnoreCase(idpRoleAdmin)) 
                out.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
            if (role.equalsIgnoreCase(idpRoleBatchView)) 
                out.add(new SimpleGrantedAuthority("ROLE_BATCH_VIEW"));
            if (role.equalsIgnoreCase(idpRoleBatchExecute)) 
                out.add(new SimpleGrantedAuthority("ROLE_BATCH_EXECUTE"));
        }
        log.debug("Mapped roles: " + out);
        return out;
    }

}
