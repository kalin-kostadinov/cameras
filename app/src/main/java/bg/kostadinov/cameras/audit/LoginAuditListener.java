package bg.kostadinov.cameras.audit;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

/** Audits interactive (username + password) logins. Remember-me logins are not recorded. */
@Component
public class LoginAuditListener {

    private final AuditService audit;

    public LoginAuditListener(AuditService audit) {
        this.audit = audit;
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        if (!(event.getAuthentication() instanceof UsernamePasswordAuthenticationToken auth)) {
            return;
        }
        String ip = auth.getDetails() instanceof WebAuthenticationDetails details ? details.getRemoteAddress() : null;
        audit.record(auth.getName(), null, AuditAction.LOGIN, ip);
    }
}
