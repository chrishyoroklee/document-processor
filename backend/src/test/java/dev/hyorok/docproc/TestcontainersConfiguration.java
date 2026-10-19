package dev.hyorok.docproc;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

// Import this into a test and Spring starts a throwaway Postgres container.
// @ServiceConnection replaces spring.datasource.url/username/password with the
// container's values, so tests never touch your docker-compose database.
// The container is shared by every test class that imports this configuration.
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        // Same major version as docker-compose.yml.
        return new PostgreSQLContainer(DockerImageName.parse("postgres:16"));
    }
}
