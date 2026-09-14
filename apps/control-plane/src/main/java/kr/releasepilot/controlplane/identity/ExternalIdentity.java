package kr.releasepilot.controlplane.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "external_identities")
public class ExternalIdentity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Column(nullable = false, length = 500)
    private String issuer;

    @Column(nullable = false, length = 255)
    private String subject;

    @Column(length = 320)
    private String email;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_login_at", nullable = false)
    private Instant lastLoginAt;

    protected ExternalIdentity() {
    }

    public static ExternalIdentity create(UserAccount user, String issuer, String subject, String email, Instant now) {
        ExternalIdentity identity = new ExternalIdentity();
        identity.id = UUID.randomUUID();
        identity.user = user;
        identity.issuer = issuer;
        identity.subject = subject;
        identity.email = email;
        identity.createdAt = now;
        identity.lastLoginAt = now;
        return identity;
    }

    public UserAccount getUser() { return user; }

    public void recordLogin(String email, Instant now) {
        this.email = email;
        this.lastLoginAt = now;
    }
}
