package bg.kostadinov.cameras.admin;

import bg.kostadinov.cameras.access.AccessGroup;
import bg.kostadinov.cameras.access.AccessGroupRepository;
import bg.kostadinov.cameras.access.GroupCamera;
import bg.kostadinov.cameras.audit.AuditService;
import bg.kostadinov.cameras.camera.Camera;
import bg.kostadinov.cameras.camera.CameraRepository;
import bg.kostadinov.cameras.camera.Location;
import bg.kostadinov.cameras.user.AppUser;
import bg.kostadinov.cameras.user.AppUserRepository;
import bg.kostadinov.cameras.user.Role;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import bg.kostadinov.cameras.config.AppProperties;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Admin screens: users, groups (members + camera grants), cameras, audit log. ADMIN role only. */
@Controller
@RequestMapping("/admin")
@Transactional
public class AdminController {

    private static final Pattern USERNAME = Pattern.compile("[a-zA-Z0-9._-]{3,64}");
    private static final Pattern SLUG = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");
    private static final int MIN_PASSWORD = 10;

    private final AppUserRepository users;
    private final AccessGroupRepository groups;
    private final CameraRepository cameras;
    private final AuditService audit;
    private final PasswordEncoder passwordEncoder;
    private final ZoneId zone;

    public AdminController(AppUserRepository users, AccessGroupRepository groups, CameraRepository cameras,
                           AuditService audit, PasswordEncoder passwordEncoder, AppProperties props) {
        this.users = users;
        this.groups = groups;
        this.cameras = cameras;
        this.audit = audit;
        this.passwordEncoder = passwordEncoder;
        this.zone = props.zoneId();
    }

    @GetMapping
    public String index() {
        return "redirect:/admin/users";
    }

    // ---------------------------------------------------------------- users

    @GetMapping("/users")
    @Transactional(readOnly = true)
    public String users(Model model) {
        model.addAttribute("users", users.findAllByOrderByUsernameAsc());
        model.addAttribute("roles", Role.values());
        return "admin/users";
    }

    @PostMapping("/users")
    public String createUser(@RequestParam String username, @RequestParam String displayName,
                             @RequestParam String password, @RequestParam Role role, RedirectAttributes flash) {
        username = username.trim();
        if (!USERNAME.matcher(username).matches()) {
            return error(flash, "Username: 3–64 characters, letters, digits, . _ -", "/admin/users");
        }
        if (users.existsByUsername(username)) {
            return error(flash, "Username already exists", "/admin/users");
        }
        if (password.length() < MIN_PASSWORD) {
            return error(flash, "Password must be at least " + MIN_PASSWORD + " characters", "/admin/users");
        }
        AppUser user = users.save(new AppUser(username, passwordEncoder.encode(password),
                displayName.isBlank() ? username : displayName.trim(), role));
        flash.addFlashAttribute("message", "User created");
        return "redirect:/admin/users/" + user.getId();
    }

    @GetMapping("/users/{id}")
    @Transactional(readOnly = true)
    public String editUser(@PathVariable Long id, Model model) {
        AppUser user = users.findById(id).orElseThrow(this::notFound);
        model.addAttribute("user", user);
        model.addAttribute("roles", Role.values());
        model.addAttribute("userGroups", groups.findByMemberId(id));
        return "admin/user-edit";
    }

    @PostMapping("/users/{id}")
    public String updateUser(@PathVariable Long id, @RequestParam String displayName, @RequestParam Role role,
                             @RequestParam(defaultValue = "false") boolean enabled,
                             @RequestParam(defaultValue = "") String newPassword,
                             Authentication auth, RedirectAttributes flash) {
        AppUser user = users.findById(id).orElseThrow(this::notFound);
        String back = "/admin/users/" + id;
        boolean self = user.getUsername().equals(auth.getName());
        if (self && (role != Role.ADMIN || !enabled)) {
            return error(flash, "You cannot remove your own admin role or disable yourself", back);
        }
        if (!newPassword.isEmpty()) {
            if (newPassword.length() < MIN_PASSWORD) {
                return error(flash, "Password must be at least " + MIN_PASSWORD + " characters", back);
            }
            user.setPasswordHash(passwordEncoder.encode(newPassword));
        }
        user.setDisplayName(displayName.isBlank() ? user.getUsername() : displayName.trim());
        user.setRole(role);
        user.setEnabled(enabled);
        flash.addFlashAttribute("message", "Saved");
        return "redirect:" + back;
    }

    @PostMapping("/users/{id}/delete")
    public String deleteUser(@PathVariable Long id, Authentication auth, RedirectAttributes flash) {
        AppUser user = users.findById(id).orElseThrow(this::notFound);
        if (user.getUsername().equals(auth.getName())) {
            return error(flash, "You cannot delete yourself", "/admin/users/" + id);
        }
        for (AccessGroup g : groups.findByMemberId(id)) {
            g.getMembers().remove(user);
        }
        users.delete(user);
        flash.addFlashAttribute("message", "User deleted");
        return "redirect:/admin/users";
    }

    // ---------------------------------------------------------------- groups

    @GetMapping("/groups")
    @Transactional(readOnly = true)
    public String groups(Model model) {
        model.addAttribute("groups", groups.findAllByOrderByNameAsc());
        return "admin/groups";
    }

    @PostMapping("/groups")
    public String createGroup(@RequestParam String name, RedirectAttributes flash) {
        name = name.trim();
        if (name.isEmpty() || name.length() > 64) {
            return error(flash, "Name: 1–64 characters", "/admin/groups");
        }
        if (groups.existsByName(name)) {
            return error(flash, "Group already exists", "/admin/groups");
        }
        AccessGroup group = groups.save(new AccessGroup(name));
        return "redirect:/admin/groups/" + group.getId();
    }

    @GetMapping("/groups/{id}")
    @Transactional(readOnly = true)
    public String editGroup(@PathVariable Long id, Model model) {
        AccessGroup group = groups.findById(id).orElseThrow(this::notFound);
        Map<Long, GroupCamera> grants = new HashMap<>();
        for (GroupCamera gc : group.getCameras()) {
            grants.put(gc.getCamera().getId(), gc);
        }
        Set<Long> memberIds = new HashSet<>();
        group.getMembers().forEach(m -> memberIds.add(m.getId()));
        model.addAttribute("group", group);
        model.addAttribute("cameras", cameras.findAllByOrderBySortOrderAscNameAsc());
        model.addAttribute("grants", grants);
        model.addAttribute("users", users.findAllByOrderByUsernameAsc());
        model.addAttribute("memberIds", memberIds);
        return "admin/group-edit";
    }

    @PostMapping("/groups/{id}")
    public String updateGroup(@PathVariable Long id, @RequestParam String name,
                              @RequestParam(name = "live", required = false) List<Long> liveIds,
                              @RequestParam(name = "playback", required = false) List<Long> playbackIds,
                              @RequestParam(name = "members", required = false) List<Long> memberIds,
                              RedirectAttributes flash) {
        AccessGroup group = groups.findById(id).orElseThrow(this::notFound);
        Set<Long> live = liveIds == null ? Set.of() : new HashSet<>(liveIds);
        Set<Long> pb = playbackIds == null ? Set.of() : new HashSet<>(playbackIds);

        if (!name.isBlank() && !name.trim().equals(group.getName())) {
            if (groups.existsByName(name.trim())) {
                return error(flash, "Group name already exists", "/admin/groups/" + id);
            }
            group.setName(name.trim());
        }

        // Camera grants: keep, update, add, or remove so that the table matches the checkboxes.
        Map<Long, GroupCamera> existing = new HashMap<>();
        group.getCameras().forEach(gc -> existing.put(gc.getCamera().getId(), gc));
        for (Camera camera : cameras.findAll()) {
            boolean wantLive = live.contains(camera.getId());
            boolean wantPlayback = pb.contains(camera.getId());
            GroupCamera gc = existing.get(camera.getId());
            if (!wantLive && !wantPlayback) {
                if (gc != null) {
                    group.getCameras().remove(gc);
                }
            } else if (gc == null) {
                group.getCameras().add(new GroupCamera(group, camera, wantLive, wantPlayback));
            } else {
                gc.setCanLive(wantLive);
                gc.setCanPlayback(wantPlayback);
            }
        }

        group.getMembers().clear();
        if (memberIds != null) {
            group.getMembers().addAll(users.findAllById(memberIds));
        }
        flash.addFlashAttribute("message", "Saved");
        return "redirect:/admin/groups/" + id;
    }

    @PostMapping("/groups/{id}/delete")
    public String deleteGroup(@PathVariable Long id, RedirectAttributes flash) {
        groups.delete(groups.findById(id).orElseThrow(this::notFound));
        flash.addFlashAttribute("message", "Group deleted");
        return "redirect:/admin/groups";
    }

    // ---------------------------------------------------------------- cameras

    @GetMapping("/cameras")
    @Transactional(readOnly = true)
    public String cameras(Model model) {
        model.addAttribute("cameras", cameras.findAllByOrderBySortOrderAscNameAsc());
        model.addAttribute("locations", Location.values());
        return "admin/cameras";
    }

    @PostMapping("/cameras")
    public String createCamera(@RequestParam String slug, @RequestParam String name,
                               @RequestParam Location location,
                               @RequestParam(defaultValue = "") String subStreamPath,
                               @RequestParam(defaultValue = "0") int sortOrder, RedirectAttributes flash) {
        slug = slug.trim();
        String sub = subStreamPath.trim();
        String validation = validateCamera(slug, sub, null);
        if (validation != null) {
            return error(flash, validation, "/admin/cameras");
        }
        Camera camera = new Camera(slug, name.isBlank() ? slug : name.trim(), location);
        camera.setSubStreamPath(sub.isEmpty() ? null : sub);
        camera.setSortOrder(sortOrder);
        cameras.save(camera);
        flash.addFlashAttribute("message", "Camera added — add the same path to mediamtx.yml");
        return "redirect:/admin/cameras";
    }

    @GetMapping("/cameras/{id}")
    @Transactional(readOnly = true)
    public String editCamera(@PathVariable Long id, Model model) {
        model.addAttribute("camera", cameras.findById(id).orElseThrow(this::notFound));
        model.addAttribute("locations", Location.values());
        return "admin/camera-edit";
    }

    @PostMapping("/cameras/{id}")
    public String updateCamera(@PathVariable Long id, @RequestParam String name, @RequestParam Location location,
                               @RequestParam(defaultValue = "") String subStreamPath,
                               @RequestParam(defaultValue = "0") int sortOrder,
                               @RequestParam(defaultValue = "false") boolean enabled, RedirectAttributes flash) {
        Camera camera = cameras.findById(id).orElseThrow(this::notFound);
        String sub = subStreamPath.trim();
        String validation = validateCamera(camera.getSlug(), sub, camera.getId());
        if (validation != null) {
            return error(flash, validation, "/admin/cameras/" + id);
        }
        camera.setName(name.isBlank() ? camera.getSlug() : name.trim());
        camera.setLocation(location);
        camera.setSubStreamPath(sub.isEmpty() ? null : sub);
        camera.setSortOrder(sortOrder);
        camera.setEnabled(enabled);
        flash.addFlashAttribute("message", "Saved");
        return "redirect:/admin/cameras/" + id;
    }

    private String validateCamera(String slug, String sub, Long selfId) {
        if (!SLUG.matcher(slug).matches()) {
            return "Path: lowercase letters, digits and dashes";
        }
        if (!sub.isEmpty() && (!SLUG.matcher(sub).matches() || sub.equals(slug))) {
            return "Sub-stream path: lowercase letters, digits and dashes, different from the main path";
        }
        for (Camera other : cameras.findAll()) {
            if (other.getId().equals(selfId)) {
                continue;
            }
            if (slug.equals(other.getSlug()) || slug.equals(other.getSubStreamPath())
                    || (!sub.isEmpty() && (sub.equals(other.getSlug()) || sub.equals(other.getSubStreamPath())))) {
                return "Path already used by camera '" + other.getName() + "'";
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- audit

    public record AuditRow(String time, String username, String action, String camera, String ip) {
    }

    @GetMapping("/audit")
    @Transactional(readOnly = true)
    public String audit(Model model) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(zone);
        model.addAttribute("events", audit.latest(300).stream()
                .map(e -> new AuditRow(fmt.format(e.getAt()), e.getUsername(), e.getAction().name(),
                        e.getCameraSlug() == null ? "—" : e.getCameraSlug(), e.getIp()))
                .toList());
        return "admin/audit";
    }

    // ---------------------------------------------------------------- helpers

    private String error(RedirectAttributes flash, String message, String back) {
        flash.addFlashAttribute("error", message);
        return "redirect:" + back;
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
