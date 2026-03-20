package dev.gimi.server.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.gimi.engine.GimiEngine;
import dev.gimi.engine.history.ExecutionStore;
import dev.gimi.engine.history.PostgresExecutionStore;
import dev.gimi.engine.log.LogStreamer;
import dev.gimi.engine.log.RedisLogStreamer;
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
import java.time.Duration;

/**
 * Spring configuration that wires up the gimi engine beans including data sources,
 * execution store, job queue, and the engine facade.
 *
 * <p>Pool sizes are tuned for high-throughput production workloads (10,000+ concurrent pipelines).
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
     * Pool sized for 10K concurrent pipeline executions.
     */
    @Bean
    @Primary
    public HikariDataSource hikariDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(serverConfig.getPostgresUrl());
        config.setUsername(serverConfig.getPostgresUsername());
        config.setPassword(serverConfig.getPostgresPassword());
        config.setMaximumPoolSize(100);
        config.setMinimumIdle(20);
        config.setPoolName("gimi-pg-pool");
        config.setConnectionTimeout(10_000);
        config.setIdleTimeout(300_000);
        config.setMaxLifetime(900_000);
        config.setLeakDetectionThreshold(30_000);
        config.setValidationTimeout(3_000);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "256");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        log.info("Configuring PostgreSQL datasource: {} (pool: max={}, minIdle={})",
                serverConfig.getPostgresUrl(), 100, 20);
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
     * Pool sized for 10K concurrent pipeline executions with headroom for
     * queue ops, log streaming, heartbeats, and distributed locks.
     */
    @Bean
    public JedisPool jedisPool() {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(200);
        poolConfig.setMaxIdle(50);
        poolConfig.setMinIdle(20);
        poolConfig.setMaxWait(Duration.ofSeconds(5));
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestWhileIdle(true);
        poolConfig.setTimeBetweenEvictionRuns(Duration.ofSeconds(30));
        poolConfig.setMinEvictableIdleDuration(Duration.ofMinutes(5));
        poolConfig.setBlockWhenExhausted(true);
        poolConfig.setJmxEnabled(true);
        poolConfig.setJmxNamePrefix("gimi-redis");

        URI redisUri = URI.create(serverConfig.getRedisUrl());
        log.info("Configuring Redis connection pool: {} (pool: max={}, minIdle={})",
                serverConfig.getRedisUrl(), 200, 20);
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
     * Redis-backed log streamer with multiplexed pub/sub.
     */
    @Bean
    public LogStreamer logStreamer(JedisPool jedisPool) {
        return new RedisLogStreamer(jedisPool);
    }

    /**
     * The main GimiEngine facade.
     */
    @Bean
    public GimiEngine gimiEngine(ExecutionStore executionStore) {
        return new GimiEngine(executionStore);
    }
}
