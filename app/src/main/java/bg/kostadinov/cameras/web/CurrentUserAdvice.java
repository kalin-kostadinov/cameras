package bg.kostadinov.cameras.web;

import bg.kostadinov.cameras.config.AppProperties;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Values every template needs. */
@ControllerAdvice
public class CurrentUserAdvice {

    private final AppProperties props;

    public CurrentUserAdvice(AppProperties props) {
        this.props = props;
    }

    @ModelAttribute("liveBase")
    public String liveBase() {
        return trimSlash(props.liveBasePath());
    }

    @ModelAttribute("playbackBase")
    public String playbackBase() {
        return trimSlash(props.playbackBasePath());
    }

    private static String trimSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
