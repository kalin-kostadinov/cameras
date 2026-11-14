package bg.kostadinov.cameras.security;

import bg.kostadinov.cameras.access.AccessService;
import bg.kostadinov.cameras.access.Permission;
import bg.kostadinov.cameras.audit.AuditAction;
import bg.kostadinov.cameras.audit.AuditService;
import bg.kostadinov.cameras.camera.Camera;
import bg.kostadinov.cameras.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.Set;

/**
 * Target of Caddy's {@code forward_auth}. Caddy calls this for every request to /live/* and
 * /playback/*, passing the browser's cookies plus X-Forwarded-Uri / X-Forwarded-Method and our
 * shared secret. 2xx lets the request through to MediaMTX; anything else is returned to the client.
 */
@RestController
public class AuthzController {

    private static final Set<String> ALLOWED_METHODS = Set.of("GET", "HEAD");

    private final AccessService access;
    private final AuditService audit;
    private final MediaPathParser parser;
    private final byte[] secret;

    public AuthzController(AccessService access, AuditService audit, AppProperties props) {
        this.access = access;
        this.audit = audit;
        this.parser = new MediaPathParser(props.liveBasePath(), props.playbackBasePath());
        this.secret = props.authzSecret().getBytes(StandardCharsets.UTF_8);
    }

    @GetMapping("/internal/authz")
    public ResponseEntity<Void> authorize(
            @RequestHeader(value = "X-Authz-Secret", required = false) String providedSecret,
            @RequestHeader(value = "X-Forwarded-Uri", required = false) String forwardedUri,
            @RequestHeader(value = "X-Forwarded-Method", required = false) String forwardedMethod,
            @RequestHeader(value = "X-Forwarded-For", required = false) String forwardedFor,
            Authentication authentication,
            HttpServletRequest request) {

        if (providedSecret == null
                || !MessageDigest.isEqual(secret, providedSecret.getBytes(StandardCharsets.UTF_8))) {
            return status(HttpStatus.NOT_FOUND);
        }
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return status(HttpStatus.UNAUTHORIZED);
        }
        if (forwardedMethod != null && !ALLOWED_METHODS.contains(forwardedMethod.toUpperCase())) {
            return status(HttpStatus.METHOD_NOT_ALLOWED);
        }
        Optional<MediaRequest> media = parser.parse(forwardedUri);
        if (media.isEmpty()) {
            return status(HttpStatus.FORBIDDEN);
        }
        Optional<Camera> camera = access.cameraForPath(media.get().path());
        String username = authentication.getName();
        if (camera.isEmpty() || !access.isAllowed(username, camera.get(), media.get().permission())) {
            return status(HttpStatus.FORBIDDEN);
        }
        AuditAction action = media.get().permission() == Permission.LIVE ? AuditAction.LIVE : AuditAction.PLAYBACK;
        audit.recordThrottled(username, camera.get().getSlug(), action, clientIp(forwardedFor, request));
        return ResponseEntity.ok().build();
    }

    private static String clientIp(String forwardedFor, HttpServletRequest request) {
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String first = forwardedFor.split(",")[0].trim();
            return first.length() > 64 ? first.substring(0, 64) : first;
        }
        return request.getRemoteAddr();
    }

    private static ResponseEntity<Void> status(HttpStatus status) {
        return ResponseEntity.status(status).build();
    }
}
