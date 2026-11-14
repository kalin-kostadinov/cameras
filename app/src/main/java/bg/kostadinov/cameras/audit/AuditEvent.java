package bg.kostadinov.cameras.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Who looked at what, when. Stores names (not FKs) so history survives user/camera deletion. */
@Entity
@Table(name = "audit_event")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String username;

    @Column(name = "camera_slug", length = 64)
    private String cameraSlug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AuditAction action;

    @Column(name = "occurred_at", nullable = false)
    private Instant at = Instant.now();

    @Column(length = 64)
    private String ip;

    protected AuditEvent() {
    }

    public AuditEvent(String username, String cameraSlug, AuditAction action, String ip) {
        this.username = username;
        this.cameraSlug = cameraSlug;
        this.action = action;
        this.ip = ip;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getCameraSlug() { return cameraSlug; }
    public AuditAction getAction() { return action; }
    public Instant getAt() { return at; }
    public String getIp() { return ip; }
}
