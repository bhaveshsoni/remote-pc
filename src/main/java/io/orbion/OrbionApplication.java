package io.orbion;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
public class OrbionApplication {

    public static void main(String[] args) {
        // headless(false) is required for SystemTray, Robot and the Swing dashboard
        new SpringApplicationBuilder(OrbionApplication.class)
                .headless(false)
                .run(args);

    }
}
