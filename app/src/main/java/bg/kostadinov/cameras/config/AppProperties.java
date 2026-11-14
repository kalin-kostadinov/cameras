package bg.kostadinov.cameras.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.ZoneId;

@Validated
@ConfigurationProperties("app")
public record AppProperties(
        @NotBlank String timezone,
        @NotBlank @Size(min = 24, message = "AUTHZ_SECRET must be at least 24 characters") String authzSecret,
        @NotBlank @Size(min = 24, message = "REMEMBER_ME_KEY must be at least 24 characters") String rememberMeKey,
        @NotBlank String liveBasePath,
        @NotBlank String playbackBasePath,
        @Valid Admin admin) {

    public record Admin(@NotBlank String username, String password) {
    }

    public ZoneId zoneId() {
        return ZoneId.of(timezone);
    }
}
