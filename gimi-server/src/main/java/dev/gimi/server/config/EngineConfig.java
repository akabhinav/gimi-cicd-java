package dev.gimi.server.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.gimi.engine.GimiEngine;
import dev.gimi.engine.history.ExecutionStore;
import dev.gimi.engine.history.PostgresExecutionStore;
import dev.gimi.engine.queue.JobQueue;
import dev.gimi.engine.queue.RedisJobQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.net.URI;

/**
 * Spring configuration that wires up the gimi engine beans including data sources,
 * execution store, job queue, and the engine facade.
 */
@Configuration
public class EngineConfig {

    private static final Logger log = LoggerFactory.getLogger(EngineConfig.class);

    private final ServerConfig serverConfig;

    public EngineConfig(ServerConfig serverConfig) {
        this.serverConfig = serverConfig;
    }

    /**
     * HikariCP DataSource for PostgreSQL.
     */
    @Bean
    @Primary
    public HikariDataSource hikariDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(serverConfig.getPostgresUrl());
        config.setUsername(serverConfig.getPostgresUsername());
        config.setPassword(serverConfig.getPostgresPassword());
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setPoolName("gimi-pg-pool");
        config.setConnectionTimeout(5000);
        config.setIdleTimeout(300000);
        config.setMaxLifetime(600000);
        log.info("Configuring PostgreSQL datasource: {}", serverConfig.getPostgresUrl());
        return new HikariDataSource(config);
    }

    /**
     * PostgreSQL-backed execution store.
     */
    @Bean
    public ExecutionStore executionStore(HikariDataSource dataSource) {
        return new PostgresExecutionStore(dataSource);
    }

    /**
     * Jedis connection pool for Redis.
     */
    @Bean
    public JedisPool jedisPool() {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(16);
        poolConfig.setMaxIdle(8);
        poolConfig.setMinIdle(2);

        URI redisUri = URI.create(serverConfig.getRedisUrl());
        log.info("Configuring Redis connection pool: {}", serverConfig.getRedisUrl());
        return new JedisPool(poolConfig, redisUri);
    }

    /**
     * Redis-backed distributed job queue.
     */
    @Bean
    public JobQueue jobQueue(JedisPool jedisPool) {
        return new RedisJobQueue(jedisPool);
    }

    /**
     * The main GimiEngine facade.
     */
    @Bean
    public GimiEngine gimiEngine(ExecutionStore executionStore) {
        return new GimiEngine(executionStore);
    }
}
