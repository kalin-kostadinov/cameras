package bg.kostadinov.cameras.access;

import bg.kostadinov.cameras.camera.Camera;
import bg.kostadinov.cameras.camera.CameraRepository;
import bg.kostadinov.cameras.camera.Location;
import bg.kostadinov.cameras.user.AppUser;
import bg.kostadinov.cameras.user.AppUserRepository;
import bg.kostadinov.cameras.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccessServiceTest {

    private final AppUserRepository users = mock(AppUserRepository.class);
    private final CameraRepository cameras = mock(CameraRepository.class);
    private final GroupCameraRepository grants = mock(GroupCameraRepository.class);
    private final AccessService service = new AccessService(users, cameras, grants);

    private final Camera door = camera(1L, "home-door");

    @Test
    void adminSeesEverything() {
        when(users.findByUsername("kalin")).thenReturn(Optional.of(new AppUser("kalin", "x", "Kalin", Role.ADMIN)));
        assertThat(service.isAllowed("kalin", door, Permission.LIVE)).isTrue();
        assertThat(service.isAllowed("kalin", door, Permission.PLAYBACK)).isTrue();
        verify(grants, never()).hasLive(anyString(), any());
    }

    @Test
    void viewerNeedsGrant() {
        when(users.findByUsername("ivan")).thenReturn(Optional.of(new AppUser("ivan", "x", "Ivan", Role.VIEWER)));
        when(grants.hasLive("ivan", 1L)).thenReturn(true);
        when(grants.hasPlayback("ivan", 1L)).thenReturn(false);
        assertThat(service.isAllowed("ivan", door, Permission.LIVE)).isTrue();
        assertThat(service.isAllowed("ivan", door, Permission.PLAYBACK)).isFalse();
    }

    @Test
    void disabledUserSeesNothing() {
        AppUser admin = new AppUser("kalin", "x", "Kalin", Role.ADMIN);
        admin.setEnabled(false);
        when(users.findByUsername("kalin")).thenReturn(Optional.of(admin));
        assertThat(service.isAllowed("kalin", door, Permission.LIVE)).isFalse();
        assertThat(service.visibleCameras("kalin")).isEmpty();
    }

    @Test
    void unknownUserSeesNothing() {
        when(users.findByUsername("ghost")).thenReturn(Optional.empty());
        assertThat(service.isAllowed("ghost", door, Permission.LIVE)).isFalse();
    }

    @Test
    void disabledCameraIsNeverAllowed() {
        when(users.findByUsername("kalin")).thenReturn(Optional.of(new AppUser("kalin", "x", "Kalin", Role.ADMIN)));
        door.setEnabled(false);
        assertThat(service.isAllowed("kalin", door, Permission.LIVE)).isFalse();
        when(cameras.findByPath("home-door")).thenReturn(Optional.of(door));
        assertThat(service.cameraForPath("home-door")).isEmpty();
    }

    @Test
    void viewerDashboardComesFromGrants() {
        when(users.findByUsername("ivan")).thenReturn(Optional.of(new AppUser("ivan", "x", "Ivan", Role.VIEWER)));
        when(grants.findLiveCameras("ivan")).thenReturn(List.of(door));
        assertThat(service.visibleCameras("ivan")).containsExactly(door);
    }

    private static Camera camera(Long id, String slug) {
        Camera c = new Camera(slug, slug, Location.HOME);
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }
}
