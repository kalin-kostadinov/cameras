package bg.kostadinov.cameras.camera;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CameraRepository extends JpaRepository<Camera, Long> {

    Optional<Camera> findBySlug(String slug);

    /** Resolves a MediaMTX path name (main or sub-stream) to its camera. */
    @Query("select c from Camera c where c.slug = :path or c.subStreamPath = :path")
    Optional<Camera> findByPath(String path);

    List<Camera> findAllByOrderBySortOrderAscNameAsc();

    List<Camera> findByEnabledTrueOrderBySortOrderAscNameAsc();
}
