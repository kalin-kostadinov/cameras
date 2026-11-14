package bg.kostadinov.cameras.access;

import bg.kostadinov.cameras.camera.Camera;
import bg.kostadinov.cameras.camera.CameraRepository;
import bg.kostadinov.cameras.user.AppUser;
import bg.kostadinov.cameras.user.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Single place that decides who may see which camera. Always reads the user from the
 * database so that disabling a user or changing a role takes effect immediately.
 */
@Service
@Transactional(readOnly = true)
public class AccessService {

    private final AppUserRepository users;
    private final CameraRepository cameras;
    private final GroupCameraRepository grants;

    public AccessService(AppUserRepository users, CameraRepository cameras, GroupCameraRepository grants) {
        this.users = users;
        this.cameras = cameras;
        this.grants = grants;
    }

    /** Resolves an enabled camera by its main or sub-stream MediaMTX path. */
    public Optional<Camera> cameraForPath(String path) {
        return cameras.findByPath(path).filter(Camera::isEnabled);
    }

    public Optional<Camera> cameraForSlug(String slug) {
        return cameras.findBySlug(slug).filter(Camera::isEnabled);
    }

    public boolean isAllowed(String username, Camera camera, Permission permission) {
        if (username == null || camera == null || !camera.isEnabled()) {
            return false;
        }
        Optional<AppUser> user = users.findByUsername(username).filter(AppUser::isEnabled);
        if (user.isEmpty()) {
            return false;
        }
        if (user.get().isAdmin()) {
            return true;
        }
        return switch (permission) {
            case LIVE -> grants.hasLive(username, camera.getId());
            case PLAYBACK -> grants.hasPlayback(username, camera.getId());
        };
    }

    /** Cameras shown on the user's dashboard, in display order. */
    public List<Camera> visibleCameras(String username) {
        Optional<AppUser> user = users.findByUsername(username).filter(AppUser::isEnabled);
        if (user.isEmpty()) {
            return List.of();
        }
        if (user.get().isAdmin()) {
            return cameras.findByEnabledTrueOrderBySortOrderAscNameAsc();
        }
        return grants.findLiveCameras(username);
    }
}
