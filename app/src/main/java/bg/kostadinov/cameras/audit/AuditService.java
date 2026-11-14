package bg.kostadinov.cameras.audit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Records viewing activity. A live stream or playback produces many HTTP requests
 * (playlist + segments), so the same user/camera/action is recorded at most once per window.
 */
@Service
public class AuditService {

    static final Duration THROTTLE_WINDOW = Duration.ofMinutes(10);

    private final AuditEventRepository events;
    private final Clock clock;
    private final Map<String, Instant> lastRecorded = new ConcurrentHashMap<>();

    @Autowired
    public AuditService(AuditEventRepository events) {
        this(events, Clock.systemUTC());
    }

    AuditService(AuditEventRepository events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public void record(String username, String cameraSlug, AuditAction action, String ip) {
        events.save(new AuditEvent(username, cameraSlug, action, ip));
    }

    /** Records the view unless the same user/camera/action was recorded within the throttle window. */
    @Transactional
    public void recordThrottled(String username, String cameraSlug, AuditAction action, String ip) {
        Instant now = clock.instant();
        String key = username + '|' + cameraSlug + '|' + action;
        boolean[] fresh = {false};
        lastRecorded.compute(key, (k, previous) -> {
            if (previous == null || previous.plus(THROTTLE_WINDOW).isBefore(now)) {
                fresh[0] = true;
                return now;
            }
            return previous;
        });
        if (fresh[0]) {
            record(username, cameraSlug, action, ip);
        }
    }

    @Transactional(readOnly = true)
    public List<AuditEvent> latest(int limit) {
        return events.findAllByOrderByAtDesc(PageRequest.of(0, limit));
    }

    @Scheduled(fixedRate = 3_600_000)
    void evictOldThrottleEntries() {
        Instant cutoff = clock.instant().minus(THROTTLE_WINDOW);
        lastRecorded.values().removeIf(at -> at.isBefore(cutoff));
    }
}
