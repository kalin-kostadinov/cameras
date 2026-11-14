package bg.kostadinov.cameras.security;

import bg.kostadinov.cameras.access.AccessService;
import bg.kostadinov.cameras.access.Permission;
import bg.kostadinov.cameras.audit.AuditAction;
import bg.kostadinov.cameras.audit.AuditService;
import bg.kostadinov.cameras.camera.Camera;
import bg.kostadinov.cameras.camera.Location;
import bg.kostadinov.cameras.config.AppProperties;
import bg.kostadinov.cameras.user.AppUserDetailsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthzController.class)
@Import({SecurityConfig.class, AuthzControllerTest.Config.class})
@TestPropertySource(properties = {
        "app.timezone=Europe/Sofia",
        "app.authz-secret=test-secret-test-secret-123",
        "app.remember-me-key=test-remember-me-key-123456",
        "app.live-base-path=/live",
        "app.playback-base-path=/playback",
        "app.admin.username=admin"
})
class AuthzControllerTest {

    private static final String SECRET = "test-secret-test-secret-123";

    @TestConfiguration
    @EnableConfigurationProperties(AppProperties.class)
    static class Config {
    }

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AccessService access;

    @MockitoBean
    AuditService audit;

    @MockitoBean
    AppUserDetailsService userDetailsService;

    private final Camera entrance = new Camera("home-entrance", "Entrance", Location.HOME);

    @BeforeEach
    void setUp() {
        when(access.cameraForPath("home-entrance")).thenReturn(Optional.of(entrance));
        when(access.cameraForPath("home-entrance-sub")).thenReturn(Optional.of(entrance));
        when(access.cameraForPath("unknown")).thenReturn(Optional.empty());
    }

    private MockHttpServletRequestBuilder authz(String uri) {
        return get("/internal/authz")
                .header("X-Authz-Secret", SECRET)
                .header("X-Forwarded-Uri", uri)
                .header("X-Forwarded-Method", "GET")
                .header("X-Forwarded-For", "203.0.113.7");
    }

    @Test
    void missingSecretIsNotFound() throws Exception {
        mvc.perform(get("/internal/authz").header("X-Forwarded-Uri", "/live/home-entrance/index.m3u8"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser("neighbour")
    void wrongSecretIsNotFound() throws Exception {
        mvc.perform(authz("/live/home-entrance/index.m3u8").header("X-Authz-Secret", "nope"))
                .andExpect(status().isNotFound());
    }

    @Test
    void anonymousIsUnauthorized() throws Exception {
        mvc.perform(authz("/live/home-entrance/index.m3u8")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser("neighbour")
    void allowedLiveIsOkAndAudited() throws Exception {
        when(access.isAllowed("neighbour", entrance, Permission.LIVE)).thenReturn(true);
        mvc.perform(authz("/live/home-entrance-sub/index.m3u8")).andExpect(status().isOk());
        verify(audit).recordThrottled("neighbour", "home-entrance", AuditAction.LIVE, "203.0.113.7");
    }

    @Test
    @WithMockUser("neighbour")
    void deniedLiveIsForbidden() throws Exception {
        when(access.isAllowed("neighbour", entrance, Permission.LIVE)).thenReturn(false);
        mvc.perform(authz("/live/home-entrance/index.m3u8")).andExpect(status().isForbidden());
        verify(audit, never()).recordThrottled(anyString(), anyString(), any(), any());
    }

    @Test
    @WithMockUser("neighbour")
    void playbackNeedsPlaybackPermission() throws Exception {
        when(access.isAllowed("neighbour", entrance, Permission.LIVE)).thenReturn(true);
        when(access.isAllowed("neighbour", entrance, Permission.PLAYBACK)).thenReturn(false);
        mvc.perform(authz("/playback/get?path=home-entrance&duration=60")).andExpect(status().isForbidden());

        when(access.isAllowed("neighbour", entrance, Permission.PLAYBACK)).thenReturn(true);
        mvc.perform(authz("/playback/get?path=home-entrance&duration=60")).andExpect(status().isOk());
        verify(audit).recordThrottled(eq("neighbour"), eq("home-entrance"), eq(AuditAction.PLAYBACK), any());
    }

    @Test
    @WithMockUser("neighbour")
    void unknownCameraIsForbidden() throws Exception {
        mvc.perform(authz("/live/unknown/index.m3u8")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser("neighbour")
    void malformedUriIsForbidden() throws Exception {
        mvc.perform(authz("/live/../admin")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser("neighbour")
    void postIsRejected() throws Exception {
        when(access.isAllowed("neighbour", entrance, Permission.LIVE)).thenReturn(true);
        mvc.perform(authz("/live/home-entrance/index.m3u8").header("X-Forwarded-Method", "POST"))
                .andExpect(status().isMethodNotAllowed());
    }
}
