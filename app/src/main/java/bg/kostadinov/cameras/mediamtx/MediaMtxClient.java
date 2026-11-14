package bg.kostadinov.cameras.mediamtx;

import bg.kostadinov.cameras.config.MediaMtxProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Server-side calls to MediaMTX (control API and playback server). Never proxies video. */
@Component
public class MediaMtxClient {

    private static final Logger log = LoggerFactory.getLogger(MediaMtxClient.class);

    private final RestClient api;
    private final RestClient playback;

    public MediaMtxClient(MediaMtxProperties props, RestClient.Builder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.api = builder.clone().requestFactory(factory).baseUrl(props.apiUrl()).build();
        this.playback = builder.clone().requestFactory(factory).baseUrl(props.playbackUrl()).build();
    }

    /**
     * Readiness of every MediaMTX path (path name → stream is online).
     * Returns an empty map when MediaMTX is unreachable.
     */
    public Map<String, Boolean> pathStatuses() {
        Map<String, Boolean> result = new HashMap<>();
        try {
            int page = 0;
            int pageCount;
            do {
                final int currentPage = page;
                JsonNode body = api.get()
                        .uri(uri -> uri.path("/v3/paths/list").queryParam("page", "{p}")
                                .queryParam("itemsPerPage", 100).build(currentPage))
                        .retrieve()
                        .body(JsonNode.class);
                if (body == null) {
                    break;
                }
                for (JsonNode item : body.path("items")) {
                    result.put(item.path("name").asText(), item.path("ready").asBoolean(false));
                }
                pageCount = body.path("pageCount").asInt(1);
                page++;
            } while (page < pageCount);
        } catch (RestClientException e) {
            log.warn("MediaMTX API unreachable: {}", e.getMessage());
        }
        return result;
    }

    /** Recorded segments of a path that overlap [from, to). Empty when none or MediaMTX is unreachable. */
    public List<RecordingSegment> listRecordings(String path, Instant from, Instant to) {
        try {
            JsonNode body = playback.get()
                    .uri(uri -> uri.path("/list")
                            .queryParam("path", "{path}")
                            .queryParam("start", "{start}")
                            .queryParam("end", "{end}")
                            .build(path, from.toString(), to.toString()))
                    .retrieve()
                    .body(JsonNode.class);
            List<RecordingSegment> segments = new ArrayList<>();
            if (body != null) {
                for (JsonNode item : body) {
                    Instant start = Instant.parse(item.path("start").asText());
                    long millis = Math.round(item.path("duration").asDouble() * 1000);
                    segments.add(new RecordingSegment(start, Duration.ofMillis(millis)));
                }
            }
            return segments;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() != HttpStatus.NOT_FOUND) {
                log.warn("MediaMTX playback /list failed for {}: {}", path, e.getMessage());
            }
            return List.of(); // 404 = no recordings in range
        } catch (RestClientException | DateTimeException e) {
            log.warn("MediaMTX playback unreachable for {}: {}", path, e.getMessage());
            return List.of();
        }
    }
}
