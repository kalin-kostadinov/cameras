package bg.kostadinov.cameras.playback;

import bg.kostadinov.cameras.mediamtx.RecordingSegment;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TimelineTest {

    private static final ZoneId SOFIA = ZoneId.of("Europe/Sofia");
    private static final LocalDate DAY = LocalDate.of(2026, 11, 14); // UTC+2

    @Test
    void mergesBackToBackSegmentsAndPositionsThem() {
        // 10:00–11:00 and 11:00–12:00 local = 08:00–10:00 UTC
        List<RecordingSegment> segments = List.of(
                new RecordingSegment(Instant.parse("2026-11-14T09:00:01Z"), Duration.ofMinutes(60)),
                new RecordingSegment(Instant.parse("2026-11-14T08:00:00Z"), Duration.ofMinutes(60)));
        List<TimelineBlock> blocks = Timeline.build(segments, DAY, SOFIA);
        assertThat(blocks).hasSize(1);
        TimelineBlock b = blocks.get(0);
        assertThat(b.label()).isEqualTo("10:00 – 12:00");
        assertThat(b.leftPercent()).isCloseTo(10 * 100.0 / 24, within(0.01));
        assertThat(b.widthPercent()).isCloseTo(2 * 100.0 / 24, within(0.05));
    }

    @Test
    void keepsGapsAndClampsToTheDay() {
        List<RecordingSegment> segments = List.of(
                // starts the previous evening local time
                new RecordingSegment(Instant.parse("2026-11-13T21:30:00Z"), Duration.ofHours(1)),
                new RecordingSegment(Instant.parse("2026-11-14T12:00:00Z"), Duration.ofMinutes(30)));
        List<TimelineBlock> blocks = Timeline.build(segments, DAY, SOFIA);
        assertThat(blocks).hasSize(2);
        assertThat(blocks.get(0).start()).isEqualTo(Instant.parse("2026-11-13T22:00:00Z"));
        assertThat(blocks.get(0).seconds()).isEqualTo(1800);
        assertThat(blocks.get(0).leftPercent()).isZero();
        assertThat(blocks.get(1).label()).isEqualTo("14:00 – 14:30");
    }

    @Test
    void emptyWhenNoSegments() {
        assertThat(Timeline.build(List.of(), DAY, SOFIA)).isEmpty();
    }
}
