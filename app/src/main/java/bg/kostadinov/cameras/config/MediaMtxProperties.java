package bg.kostadinov.cameras.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Internal (server-side) URLs of the MediaMTX control API and playback server. */
@Validated
@ConfigurationProperties("mediamtx")
public record MediaMtxProperties(@NotBlank String apiUrl, @NotBlank String playbackUrl) {
}
