package dev.hyorok.docproc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

// @SpringBootApplication turns on three things:
//   - component scanning: finds every @Component/@Service/@RestController in this package and below
//   - auto-configuration: sets up Tomcat, Jackson, JPA, etc. based on what's on the classpath
//   - this class as a configuration source
@SpringBootApplication
// Finds @ConfigurationProperties records (like ClaudeProperties) and fills them from application.yml.
@ConfigurationPropertiesScan
public class DocprocApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocprocApplication.class, args);
    }
}
