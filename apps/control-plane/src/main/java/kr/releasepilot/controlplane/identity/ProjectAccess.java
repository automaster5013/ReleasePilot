package kr.releasepilot.controlplane.identity;
import org.springframework.security.core.Authentication; import org.springframework.stereotype.Component; import java.util.UUID;
@Component("projectAccess") public class ProjectAccess {
 private final ProjectMembershipRepository memberships; public ProjectAccess(ProjectMembershipRepository memberships){this.memberships=memberships;}
 public boolean canView(UUID projectId,Authentication authentication){if(authentication==null||!authentication.isAuthenticated())return false;if(authentication.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_OPERATOR")))return true;if(!(authentication.getPrincipal() instanceof UserAccountPrincipal principal))return false;return memberships.existsByProjectIdAndUserId(projectId,principal.id());}
 public boolean hasRole(UUID projectId,Authentication authentication,Role required){if(authentication!=null&&authentication.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_OPERATOR")))return true;if(!(authentication!=null&&authentication.getPrincipal() instanceof UserAccountPrincipal principal))return false;return memberships.findByProjectIdAndUserId(projectId,principal.id()).map(m->m.getRole()==required||(required==Role.DEVELOPER&&m.getRole()==Role.APPROVER)).orElse(false);}
}
