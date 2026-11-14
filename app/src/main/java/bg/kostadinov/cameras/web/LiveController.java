package bg.kostadinov.cameras.web;

import bg.kostadinov.cameras.access.AccessService;
import bg.kostadinov.cameras.access.Permission;
import bg.kostadinov.cameras.camera.Camera;
import bg.kostadinov.cameras.mediamtx.MediaMtxClient;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class LiveController {

    private final AccessService access;
    private final MediaMtxClient mediaMtx;

    public LiveController(AccessService access, MediaMtxClient mediaMtx) {
        this.access = access;
        this.mediaMtx = mediaMtx;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/")
    public String grid(Authentication auth, Model model) {
        List<Camera> cameras = access.visibleCameras(auth.getName());
        model.addAttribute("cameras", cameras);
        model.addAttribute("online", onlineMap(cameras));
        return "index";
    }

    @GetMapping("/cameras/{slug}")
    public String camera(@PathVariable String slug, Authentication auth, Model model) {
        Camera camera = requireAllowed(slug, auth, Permission.LIVE);
        model.addAttribute("camera", camera);
        model.addAttribute("canPlayback", access.isAllowed(auth.getName(), camera, Permission.PLAYBACK));
        return "camera";
    }

    /** Online state of the user's cameras, polled by the grid page. */
    @GetMapping("/api/status")
    @ResponseBody
    public Map<String, Boolean> status(Authentication auth) {
        return onlineMap(access.visibleCameras(auth.getName()));
    }

    private Map<String, Boolean> onlineMap(List<Camera> cameras) {
        Map<String, Boolean> statuses = mediaMtx.pathStatuses();
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (Camera c : cameras) {
            result.put(c.getSlug(), statuses.getOrDefault(c.getSlug(), false));
        }
        return result;
    }

    Camera requireAllowed(String slug, Authentication auth, Permission permission) {
        Camera camera = access.cameraForSlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!access.isAllowed(auth.getName(), camera, permission)) {
            // 404 rather than 403: don't reveal which cameras exist.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return camera;
    }
}
