package bg.kostadinov.cameras.playback;

import bg.kostadinov.cameras.access.AccessService;
import bg.kostadinov.cameras.access.Permission;
import bg.kostadinov.cameras.camera.Camera;
import bg.kostadinov.cameras.config.AppProperties;
import bg.kostadinov.cameras.mediamtx.MediaMtxClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Controller
public class PlaybackController {

    private final AccessService access;
    private final MediaMtxClient mediaMtx;
    private final ZoneId zone;

    public PlaybackController(AccessService access, MediaMtxClient mediaMtx, AppProperties props) {
        this.access = access;
        this.mediaMtx = mediaMtx;
        this.zone = props.zoneId();
    }

    @GetMapping("/cameras/{slug}/playback")
    public String playback(@PathVariable String slug,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                           Authentication auth, Model model) {
        Camera camera = access.cameraForSlug(slug)
                .filter(c -> access.isAllowed(auth.getName(), c, Permission.PLAYBACK))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        LocalDate today = LocalDate.now(zone);
        LocalDate day = date == null || date.isAfter(today) ? today : date;
        List<TimelineBlock> blocks = Timeline.build(
                mediaMtx.listRecordings(camera.getSlug(), Timeline.dayStart(day, zone), Timeline.dayEnd(day, zone)),
                day, zone);

        model.addAttribute("camera", camera);
        model.addAttribute("day", day);
        model.addAttribute("prevDay", day.minusDays(1));
        model.addAttribute("nextDay", day.isBefore(today) ? day.plusDays(1) : null);
        model.addAttribute("blocks", blocks);
        model.addAttribute("zone", zone.getId());
        return "playback";
    }
}
