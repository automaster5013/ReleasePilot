package kr.releasepilot.controlplane.identity;
import kr.releasepilot.controlplane.catalog.*; import org.junit.jupiter.api.Test; import org.springframework.beans.factory.annotation.Autowired; import org.springframework.boot.test.context.SpringBootTest; import org.springframework.security.authentication.UsernamePasswordAuthenticationToken; import org.springframework.security.core.authority.SimpleGrantedAuthority; import org.springframework.transaction.annotation.Transactional;
import java.time.Instant; import java.util.*; import static org.assertj.core.api.Assertions.assertThat;
@SpringBootTest @Transactional class ProjectAccessTests {
 @Autowired ProjectRepository projects;@Autowired UserAccountRepository users;@Autowired ProjectMembershipRepository memberships;@Autowired ProjectAccess access;
 @Test void membershipScopesViewerAndOperatorBypassesScope(){
  var project=projects.save(Project.create("access-test","Access Test",null,Instant.now()));
  var member=users.save(UserAccount.create("member","hash","Member",Set.of(Role.VIEWER),Instant.now()));
  var outsider=users.save(UserAccount.create("outsider","hash","Outsider",Set.of(Role.VIEWER),Instant.now()));
  memberships.save(ProjectMembership.create(project.getId(),member.getId(),Role.VIEWER,Instant.now()));
  assertThat(access.canView(project.getId(),authentication(member,Role.VIEWER))).isTrue();
  assertThat(access.canView(project.getId(),authentication(outsider,Role.VIEWER))).isFalse();
  assertThat(access.canView(project.getId(),authentication(outsider,Role.OPERATOR))).isTrue();
 }
 @Test void approverIncludesDeveloperCapabilityButViewerDoesNot(){
  var project=projects.save(Project.create("role-test","Role Test",null,Instant.now()));
  var approver=users.save(UserAccount.create("approver","hash","Approver",Set.of(Role.APPROVER),Instant.now()));
  var viewer=users.save(UserAccount.create("viewer","hash","Viewer",Set.of(Role.VIEWER),Instant.now()));
  memberships.save(ProjectMembership.create(project.getId(),approver.getId(),Role.APPROVER,Instant.now()));memberships.save(ProjectMembership.create(project.getId(),viewer.getId(),Role.VIEWER,Instant.now()));
  assertThat(access.hasRole(project.getId(),authentication(approver,Role.APPROVER),Role.DEVELOPER)).isTrue();
  assertThat(access.hasRole(project.getId(),authentication(viewer,Role.VIEWER),Role.DEVELOPER)).isFalse();
 }
 private UsernamePasswordAuthenticationToken authentication(UserAccount account,Role role){var p=new UserAccountPrincipal(account.getId(),account.getUsername(),"hash",account.getDisplayName(),true,List.of(new SimpleGrantedAuthority("ROLE_"+role.name())));return UsernamePasswordAuthenticationToken.authenticated(p,p.getPassword(),p.getAuthorities());}
}
