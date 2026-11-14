package bg.kostadinov.cameras.user;

import bg.kostadinov.cameras.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Creates the first admin from APP_ADMIN_USERNAME / APP_ADMIN_PASSWORD when no users exist. */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties props;

    public AdminBootstrap(AppUserRepository users, PasswordEncoder passwordEncoder, AppProperties props) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        String password = props.admin().password();
        if (password == null || password.length() < 10) {
            log.warn("No users exist and APP_ADMIN_PASSWORD is missing or shorter than 10 characters; "
                    + "set it and restart to create the first admin.");
            return;
        }
        String username = props.admin().username();
        users.save(new AppUser(username, passwordEncoder.encode(password), "Administrator", Role.ADMIN));
        log.info("Created initial admin user '{}'", username);
    }
}
