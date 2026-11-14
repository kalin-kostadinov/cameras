package bg.kostadinov.cameras.access;

import bg.kostadinov.cameras.camera.Camera;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Grants a group live and/or playback access to one camera. */
@Entity
@Table(name = "group_camera")
public class GroupCamera {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id")
    private AccessGroup group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "camera_id")
    private Camera camera;

    @Column(name = "can_live", nullable = false)
    private boolean canLive = true;

    @Column(name = "can_playback", nullable = false)
    private boolean canPlayback;

    protected GroupCamera() {
    }

    public GroupCamera(AccessGroup group, Camera camera, boolean canLive, boolean canPlayback) {
        this.group = group;
        this.camera = camera;
        this.canLive = canLive;
        this.canPlayback = canPlayback;
    }

    public Long getId() { return id; }
    public AccessGroup getGroup() { return group; }
    public Camera getCamera() { return camera; }
    public boolean isCanLive() { return canLive; }
    public void setCanLive(boolean canLive) { this.canLive = canLive; }
    public boolean isCanPlayback() { return canPlayback; }
    public void setCanPlayback(boolean canPlayback) { this.canPlayback = canPlayback; }
}
