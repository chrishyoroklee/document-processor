package dev.hyorok.docproc.extraction;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Everything under {@code claude:} in application.yml, bound into a record.
 * Spring matches YAML keys to components: {@code api-key} becomes {@code apiKey}.
 * Picked up by {@code @ConfigurationPropertiesScan} on the application class.
 */
@ConfigurationProperties(prefix = "claude")
public record ClaudeProperties(
        String apiKey,
        String baseUrl,
        String model,
        String effort) {
}
