package kr.releasepilot.controlplane.identity.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import kr.releasepilot.controlplane.identity.UserAccountPrincipal;
import kr.releasepilot.controlplane.identity.SessionRateLimiter;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/session")
public class SessionController {

    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final boolean demoEnabled;
    private final String demoUsername;
    private final boolean oidcEnabled;
    private final String oidcRegistrationId;
    private final SessionRateLimiter rateLimiter;

    public SessionController(AuthenticationManager authenticationManager, UserDetailsService userDetailsService,
                             @Value("${releasepilot.demo.enabled:false}") boolean demoEnabled,
                             @Value("${releasepilot.demo.username:releasepilot-demo}") String demoUsername,
                             @Value("${releasepilot.oidc.enabled:false}") boolean oidcEnabled,
                             @Value("${releasepilot.oidc.registration-id:releasepilot}") String oidcRegistrationId,
                             ObjectProvider<ClientRegistrationRepository> clientRegistrations,
                             SessionRateLimiter rateLimiter) {
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.demoEnabled = demoEnabled;
        this.demoUsername = demoUsername;
        this.oidcEnabled = oidcEnabled && clientRegistrations.getIfAvailable() != null;
        this.oidcRegistrationId = oidcRegistrationId;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/providers")
    public AuthenticationProviders providers() {
        return new AuthenticationProviders(oidcEnabled,
                oidcEnabled ? "/oauth2/authorization/" + oidcRegistrationId : null);
    }

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken csrfToken) {
        return new CsrfResponse(csrfToken.getHeaderName(), csrfToken.getToken());
    }

    @PostMapping("/login")
    public SessionResponse login(
            @Valid @RequestBody LoginRequest body,
            HttpServletRequest request,
            CsrfToken csrfToken
    ) {
        rateLimiter.checkLogin(request, body.username());
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(body.username(), body.password())
        );
        rateLimiter.resetLoginAccount(body.username());
        return establish(authentication, request, csrfToken, false, 1800);
    }

    @PostMapping("/demo")
    public SessionResponse demo(HttpServletRequest request, CsrfToken csrfToken) {
        if (!demoEnabled) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND);
        rateLimiter.checkDemo(request);
        var principal = userDetailsService.loadUserByUsername(demoUsername);
        var authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
        return establish(authentication, request, csrfToken, true, 3600);
    }

    private SessionResponse establish(Authentication authentication, HttpServletRequest request,
                                      CsrfToken csrfToken, boolean demo, int timeoutSeconds) {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        HttpSession session = request.getSession(true);
        request.changeSessionId();
        session.setMaxInactiveInterval(timeoutSeconds);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        session.setAttribute("RELEASEPILOT_DEMO", demo);
        return response(authentication, csrfToken, session);
    }

    @GetMapping
    public SessionResponse current(Authentication authentication, HttpSession session, CsrfToken csrfToken) {
        return response(authentication, csrfToken, session);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        new SecurityContextLogoutHandler().logout(request, response, authentication);
    }

    private SessionResponse response(Authentication authentication, CsrfToken csrfToken, HttpSession session) {
        UserAccountPrincipal principal = (UserAccountPrincipal) authentication.getPrincipal();
        List<String> roles = principal.getAuthorities().stream()
                .map(authority -> authority.getAuthority().replaceFirst("^ROLE_", ""))
                .sorted()
                .toList();
        Instant expiresAt = Instant.ofEpochMilli(
                session.getLastAccessedTime() + session.getMaxInactiveInterval() * 1000L
        );
        return new SessionResponse(
                new SessionUser(principal.id(), principal.displayName(), roles,
                        Boolean.TRUE.equals(session.getAttribute("RELEASEPILOT_DEMO"))),
                csrfToken.getToken(),
                expiresAt
        );
    }

    public record LoginRequest(
            @NotBlank @Size(max = 100) String username,
            @NotBlank @Size(max = 200) String password
    ) {
    }

    public record SessionResponse(SessionUser user, String csrfToken, Instant expiresAt) {
    }

    public record SessionUser(UUID id, String displayName, List<String> roles, boolean demo) {
    }

    public record CsrfResponse(String headerName, String token) {
    }

    public record AuthenticationProviders(boolean oidc, String loginUrl) {
    }
}
