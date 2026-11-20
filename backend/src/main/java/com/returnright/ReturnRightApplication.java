package com.returnright;

import com.returnright.core.RrProperties;
import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(RrProperties.class)
public class ReturnRightApplication {

    public static void main(String[] args) {
        // Store/compute in UTC; business-day rules use Asia/Kolkata explicitly (CLAUDE.md time rules).
        // Also avoids PG rejecting the legacy JVM zone alias "Asia/Calcutta" sent by the JDBC driver.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(ReturnRightApplication.class, args);
    }
}
