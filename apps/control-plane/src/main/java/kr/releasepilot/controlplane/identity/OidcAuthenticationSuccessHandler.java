package kr.releasepilot.controlplane.identity;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OidcAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final OidcIdentityService identityService;
    private final HttpSessionSecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    public OidcAuthenticationSuccessHandler(OidcIdentityService identityService) {
        this.identityService = identityService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        OidcUser oidcUser = (OidcUser) authentication.getPrincipal();
        UserAccountPrincipal principal;
        try {
            principal = identityService.resolve(
                    oidcUser.getClaimAsString("iss"), oidcUser.getSubject(), oidcUser.getEmail(),
                    Boolean.TRUE.equals(oidcUser.getEmailVerified()), oidcUser.getFullName());
        } catch (AuthenticationException exception) {
            SecurityContextHolder.clearContext();
            response.sendRedirect("/?loginError=oidc");
            return;
        }
        var internalAuthentication = org.springframework.security.authentication.UsernamePasswordAuthenticationToken
                .authenticated(principal, null, principal.getAuthorities());
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(internalAuthentication);
        SecurityContextHolder.setContext(context);
        request.getSession(true);
        request.changeSessionId();
        request.getSession().setMaxInactiveInterval(1800);
        request.getSession().setAttribute("RELEASEPILOT_DEMO", false);
        request.getSession().setAttribute("RELEASEPILOT_EMAIL", oidcUser.getEmail());
        contextRepository.saveContext(context, request, response);
        response.sendRedirect("/");
    }
}
