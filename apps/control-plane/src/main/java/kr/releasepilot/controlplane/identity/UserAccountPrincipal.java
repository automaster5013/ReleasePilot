package kr.releasepilot.controlplane.identity;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.UUID;

public record UserAccountPrincipal(
        UUID id,
        String username,
        String password,
        String displayName,
        boolean enabled,
        Collection<? extends GrantedAuthority> authorities
) implements UserDetails {

    public static UserAccountPrincipal from(UserAccount account) {
        var authorities = account.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .toList();
        return new UserAccountPrincipal(
                account.getId(),
                account.getUsername(),
                account.getPasswordHash(),
                account.getDisplayName(),
                account.isEnabled(),
                authorities
        );
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean isAccountNonExpired() {
        return enabled;
    }

    @Override
    public boolean isAccountNonLocked() {
        return enabled;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return enabled;
    }
}
