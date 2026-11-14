package bg.kostadinov.cameras.security;

import bg.kostadinov.cameras.access.Permission;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Parses the original request URI that Caddy forwards (X-Forwarded-Uri) into the MediaMTX
 * path being requested. This is the security boundary: anything unexpected is rejected.
 *
 * <ul>
 *   <li>{@code /live/{path}/...} → LIVE (MediaMTX HLS server)</li>
 *   <li>{@code /playback/list?path={path}...} and {@code /playback/get?path={path}...} → PLAYBACK</li>
 * </ul>
 */
public class MediaPathParser {

    /** Our MediaMTX path names: lowercase letters, digits and dashes, no slashes. */
    static final Pattern PATH_NAME = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");

    private static final Set<String> PLAYBACK_ENDPOINTS = Set.of("list", "get");

    private final String livePrefix;
    private final String playbackPrefix;

    public MediaPathParser(String liveBasePath, String playbackBasePath) {
        this.livePrefix = withTrailingSlash(liveBasePath);
        this.playbackPrefix = withTrailingSlash(playbackBasePath);
    }

    public Optional<MediaRequest> parse(String forwardedUri) {
        if (forwardedUri == null || forwardedUri.isBlank() || forwardedUri.length() > 2048) {
            return Optional.empty();
        }
        UriComponents uri;
        try {
            uri = UriComponentsBuilder.fromUriString(forwardedUri).build();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        String rawPath = uri.getPath();
        if (rawPath == null || rawPath.contains("%") || rawPath.contains("..") || rawPath.contains("//")
                || rawPath.contains("\\")) {
            // No encoded characters, traversal or empty segments in our URLs: reject rather than normalise.
            return Optional.empty();
        }
        if (rawPath.startsWith(livePrefix)) {
            return parseLive(rawPath.substring(livePrefix.length()));
        }
        if (rawPath.startsWith(playbackPrefix)) {
            return parsePlayback(rawPath.substring(playbackPrefix.length()), uri.getQueryParams());
        }
        return Optional.empty();
    }

    private Optional<MediaRequest> parseLive(String rest) {
        int slash = rest.indexOf('/');
        String name = slash < 0 ? rest : rest.substring(0, slash);
        if (!PATH_NAME.matcher(name).matches()) {
            return Optional.empty();
        }
        return Optional.of(new MediaRequest(name, Permission.LIVE));
    }

    private Optional<MediaRequest> parsePlayback(String endpoint, MultiValueMap<String, String> query) {
        if (!PLAYBACK_ENDPOINTS.contains(endpoint)) {
            return Optional.empty();
        }
        List<String> values = query.get("path");
        if (values == null || values.size() != 1 || values.get(0) == null) {
            // Missing or repeated "path" (parameter pollution) is rejected.
            return Optional.empty();
        }
        String name = URLDecoder.decode(values.get(0), StandardCharsets.UTF_8);
        if (!PATH_NAME.matcher(name).matches()) {
            return Optional.empty();
        }
        return Optional.of(new MediaRequest(name, Permission.PLAYBACK));
    }

    private static String withTrailingSlash(String base) {
        return base.endsWith("/") ? base : base + "/";
    }
}
