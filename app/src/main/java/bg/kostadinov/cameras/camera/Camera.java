package bg.kostadinov.cameras.camera;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A camera as known to MediaMTX. {@code slug} is the recorded main-stream path,
 * {@code subStreamPath} the optional low-bitrate path used in the live grid.
 */
@Entity
@Table(name = "camera")
public class Camera {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String slug;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Location location;

    @Column(name = "sub_stream_path", unique = true, length = 64)
    private String subStreamPath;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean enabled = true;

    protected Camera() {
    }

    public Camera(String slug, String name, Location location) {
        this.slug = slug;
        this.name = name;
        this.location = location;
    }

    /** Path to use for small live tiles: the sub-stream when configured, else the main stream. */
    public String gridPath() {
        return subStreamPath != null && !subStreamPath.isBlank() ? subStreamPath : slug;
    }

    public Long getId() { return id; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Location getLocation() { return location; }
    public void setLocation(Location location) { this.location = location; }
    public String getSubStreamPath() { return subStreamPath; }
    public void setSubStreamPath(String subStreamPath) { this.subStreamPath = subStreamPath; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
