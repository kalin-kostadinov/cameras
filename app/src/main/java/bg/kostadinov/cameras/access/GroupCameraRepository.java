package bg.kostadinov.cameras.access;

import bg.kostadinov.cameras.camera.Camera;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface GroupCameraRepository extends JpaRepository<GroupCamera, Long> {

    @Query("""
            select count(gc) from GroupCamera gc join gc.group g join g.members m
            where m.username = :username and gc.camera.id = :cameraId and gc.canLive = true
            """)
    long countLiveGrants(String username, Long cameraId);

    @Query("""
            select count(gc) from GroupCamera gc join gc.group g join g.members m
            where m.username = :username and gc.camera.id = :cameraId and gc.canPlayback = true
            """)
    long countPlaybackGrants(String username, Long cameraId);

    default boolean hasLive(String username, Long cameraId) {
        return countLiveGrants(username, cameraId) > 0;
    }

    default boolean hasPlayback(String username, Long cameraId) {
        return countPlaybackGrants(username, cameraId) > 0;
    }

    @Query("""
            select distinct c from GroupCamera gc join gc.camera c join gc.group g join g.members m
            where m.username = :username and gc.canLive = true and c.enabled = true
            order by c.sortOrder, c.name
            """)
    List<Camera> findLiveCameras(String username);
}
