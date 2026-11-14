package bg.kostadinov.cameras;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class CamerasApplication {

    public static void main(String[] args) {
        // Run in UTC regardless of the host: times are stored as UTC and converted to
        // app.timezone for display. Also avoids PostgreSQL rejecting legacy zone names
        // (e.g. "Europe/Kiev") sent by the JDBC driver from the JVM default.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(CamerasApplication.class, args);
    }
}
