package bg.kostadinov.cameras.security;

import bg.kostadinov.cameras.access.Permission;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class MediaPathParserTest {

    private final MediaPathParser parser = new MediaPathParser("/live", "/playback");

    @Test
    void livePlaylist() {
        assertThat(parser.parse("/live/home-entrance-sub/index.m3u8"))
                .contains(new MediaRequest("home-entrance-sub", Permission.LIVE));
    }

    @Test
    void liveSegmentWithQuery() {
        assertThat(parser.parse("/live/home-door/main_stream.m3u8?_HLS_msn=5&_HLS_part=1"))
                .contains(new MediaRequest("home-door", Permission.LIVE));
    }

    @Test
    void playbackList() {
        assertThat(parser.parse("/playback/list?path=studio-door&start=2026-11-14T00:00:00Z"))
                .contains(new MediaRequest("studio-door", Permission.PLAYBACK));
    }

    @Test
    void playbackGetWithEncodedStart() {
        assertThat(parser.parse("/playback/get?path=home-parking&start=2026-11-14T10%3A00%3A00.000Z&duration=1800&format=fmp4"))
                .contains(new MediaRequest("home-parking", Permission.PLAYBACK));
    }

    @Test
    void playbackPathMayBeEncoded() {
        assertThat(parser.parse("/playback/get?path=home%2Dparking&duration=10"))
                .contains(new MediaRequest("home-parking", Permission.PLAYBACK));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "/",
            "/admin",
            "/live",
            "/live/",
            "/live//index.m3u8",
            "/live/../admin",
            "/live/home-door/../../x",
            "/live/HOME-DOOR/index.m3u8",
            "/live/home_door/index.m3u8",
            "/live/%68ome-door/index.m3u8",
            "/live/-door/index.m3u8",
            "/liveX/home-door/index.m3u8",
            "/playback/get",
            "/playback/get?path=",
            "/playback/get?path=home-door&path=studio-door",
            "/playback/get?path=home-door%2F..%2Fx",
            "/playback/get?path=Home",
            "/playback/other?path=home-door",
            "/playback/list/x?path=home-door",
            "/playbackget?path=home-door",
            "/internal/authz"
    })
    void rejectsEverythingElse(String uri) {
        assertThat(parser.parse(uri)).isEmpty();
    }

    @Test
    void rejectsOverlongUri() {
        assertThat(parser.parse("/live/home-door/" + "a".repeat(3000))).isEmpty();
    }

    @Test
    void customPrefixes() {
        MediaPathParser custom = new MediaPathParser("/hls/", "/rec");
        assertThat(custom.parse("/hls/home-door/index.m3u8")).contains(new MediaRequest("home-door", Permission.LIVE));
        assertThat(custom.parse("/rec/get?path=home-door")).contains(new MediaRequest("home-door", Permission.PLAYBACK));
        assertThat(custom.parse("/live/home-door/index.m3u8")).isEmpty();
    }
}
