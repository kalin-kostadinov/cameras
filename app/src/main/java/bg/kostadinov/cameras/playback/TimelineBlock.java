package bg.kostadinov.cameras.playback;

import java.time.Instant;

/**
 * A recorded stretch within one day, positioned on a 24h bar.
 *
 * @param start       clamped to the day
 * @param seconds     length in seconds (clamped to the day)
 * @param leftPercent position of the start on the bar, 0–100
 * @param widthPercent width on the bar, 0–100
 * @param label       human-readable "HH:mm – HH:mm" in the configured time zone
 */
public record TimelineBlock(Instant start, long seconds, double leftPercent, double widthPercent, String label) {
}
