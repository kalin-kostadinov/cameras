package bg.kostadinov.cameras.mediamtx;

import java.time.Duration;
import java.time.Instant;

/** A continuous stretch of recording as reported by the MediaMTX playback server's /list. */
public record RecordingSegment(Instant start, Duration duration) {

    public Instant end() {
        return start.plus(duration);
    }
}
