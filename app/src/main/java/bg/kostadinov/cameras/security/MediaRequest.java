package bg.kostadinov.cameras.security;

import bg.kostadinov.cameras.access.Permission;

/** A request for MediaMTX content: which path, and whether it is live (HLS) or playback. */
public record MediaRequest(String path, Permission permission) {
}
