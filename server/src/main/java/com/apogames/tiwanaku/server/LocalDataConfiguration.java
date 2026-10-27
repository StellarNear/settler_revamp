package com.apogames.tiwanaku.server;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.pool.HikariPool;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.io.IOException;
import java.sql.SQLException;

@Configuration
@Profile("!test")
public class LocalDataConfiguration {
    @Bean
    org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy safeMigration(DataDirectoryLock directory) {
        return flyway -> StoreBackups.migrate(flyway, directory.directory());
    }

    @Bean(destroyMethod = "close")
    DataDirectoryLock dataDirectoryLock() throws IOException {
        return DataDirectoryLock.acquireFromEnvironment();
    }

    @Bean(destroyMethod = "close")
    HikariDataSource dataSource(DataDirectoryLock dataDirectoryLock) {
        HikariConfig configuration = new HikariConfig();
        configuration.setJdbcUrl(dataDirectoryLock.jdbcUrl());
        configuration.setDriverClassName("org.h2.Driver");
        configuration.setUsername("sa");
        configuration.setPassword("");
        configuration.setMaximumPoolSize(4);
        configuration.setMinimumIdle(1);
        configuration.setPoolName("TiwanakuLocalDatabase");
        try {
            return new HikariDataSource(configuration);
        } catch (HikariPool.PoolInitializationException exception) {
            if (exception.getCause() instanceof SQLException sql && "28000".equals(sql.getSQLState())) {
                throw new IllegalStateException(
                        "Cannot open the local database with the current credentials. "
                                + "If it was created by the earlier rewrite, stop Java and run "
                                + "java -jar server\\target\\tiwanaku.jar --migrate-legacy-store. "
                                + "That explicit migration backs up recognized old databases before changes. "
                                + "Do not delete the data directory: " + dataDirectoryLock.directory(),
                        exception);
            }
            throw exception;
        }
    }
}
