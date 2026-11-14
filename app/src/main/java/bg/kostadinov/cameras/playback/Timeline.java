package bg.kostadinov.cameras.playback;

import bg.kostadinov.cameras.mediamtx.RecordingSegment;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Turns MediaMTX segments into blocks on a single day's 24-hour bar. */
public final class Timeline {

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
    /** Gaps shorter than this are treated as continuous (segment boundaries). */
    private static final Duration MERGE_GAP = Duration.ofSeconds(5);

    private Timeline() {
    }

    public static Instant dayStart(LocalDate date, ZoneId zone) {
        return date.atStartOfDay(zone).toInstant();
    }

    public static Instant dayEnd(LocalDate date, ZoneId zone) {
        return date.plusDays(1).atStartOfDay(zone).toInstant();
    }

    public static List<TimelineBlock> build(List<RecordingSegment> segments, LocalDate date, ZoneId zone) {
        Instant from = dayStart(date, zone);
        Instant to = dayEnd(date, zone);
        double daySeconds = Duration.between(from, to).toSeconds(); // 23h/25h on DST days

        List<RecordingSegment> sorted = new ArrayList<>(segments);
        sorted.sort(Comparator.comparing(RecordingSegment::start));

        List<Instant[]> merged = new ArrayList<>();
        for (RecordingSegment s : sorted) {
            Instant start = s.start().isBefore(from) ? from : s.start();
            Instant end = s.end().isAfter(to) ? to : s.end();
            if (!end.isAfter(start)) {
                continue;
            }
            if (!merged.isEmpty() && !start.isAfter(merged.get(merged.size() - 1)[1].plus(MERGE_GAP))) {
                Instant[] last = merged.get(merged.size() - 1);
                if (end.isAfter(last[1])) {
                    last[1] = end;
                }
            } else {
                merged.add(new Instant[]{start, end});
            }
        }

        List<TimelineBlock> blocks = new ArrayList<>();
        for (Instant[] m : merged) {
            long seconds = Duration.between(m[0], m[1]).toSeconds();
            double left = Duration.between(from, m[0]).toSeconds() * 100.0 / daySeconds;
            double width = Math.max(seconds * 100.0 / daySeconds, 0.2);
            String label = HM.format(ZonedDateTime.ofInstant(m[0], zone)) + " – "
                    + HM.format(ZonedDateTime.ofInstant(m[1], zone));
            blocks.add(new TimelineBlock(m[0], seconds, left, width, label));
        }
        return blocks;
    }
}
