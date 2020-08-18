package io.shty.shortener;

import org.testcontainers.containers.PostgreSQLContainer;

import java.util.logging.Level;
import java.util.logging.LogManager;

class PostgreSQLTestContainer extends PostgreSQLContainer<PostgreSQLTestContainer> {

    static {
        // Postgres JDBC driver uses JUL; disable it to avoid annoying, irrelevant, stderr logs during connection testing
        LogManager.getLogManager().getLogger("").setLevel(Level.OFF);
    }

    public final UrlRepositorySqlImpl urlRepositorySql;

    public PostgreSQLTestContainer() {
        super("postgres:12.4");
        start();
        urlRepositorySql = new UrlRepositorySqlImpl(
                getHost(),
                getMappedPort(POSTGRESQL_PORT),
                getDatabaseName(),
                getUsername(),
                getPassword()
        );
    }
}
