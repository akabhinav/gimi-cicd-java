package dev.gimi.server.config;

import dev.gimi.engine.history.ExecutionStore;
import dev.gimi.engine.queue.JobQueue;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Observability configuration providing custom metrics and health indicators
 * for monitoring the gimi server.
 */
@Configuration
public class ObservabilityConfig {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityConfig.class);

    /**
     * Registers custom pipeline metrics with the MeterRegistry.
     */
    @Bean
    public PipelineMetrics pipelineMetrics(MeterRegistry registry, JobQueue jobQueue) {
        return new PipelineMetrics(registry, jobQueue);
    }

    /**
     * Health indicator for Redis connectivity.
     */
    @Bean
    public HealthIndicator redisHealthIndicator(JedisPool jedisPool) {
        return () -> {
            try (Jedis jedis = jedisPool.getResource()) {
                String pong = jedis.ping();
                if ("PONG".equals(pong)) {
                    return Health.up()
                            .withDetail("response", pong)
                            .build();
                }
                return Health.down()
                        .withDetail("response", pong)
                        .build();
            } catch (Exception e) {
                return Health.down()
                        .withDetail("error", e.getMessage())
                        .build();
            }
        };
    }

    /**
     * Health indicator for PostgreSQL connectivity.
     */
    @Bean
    public HealthIndicator postgresHealthIndicator(DataSource dataSource) {
        return () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT 1");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Health.up()
                            .withDetail("database", "postgresql")
                            .build();
                }
                return Health.down().build();
            } catch (Exception e) {
                return Health.down()
                        .withDetail("error", e.getMessage())
                        .build();
            }
        };
    }

    /**
     * Health indicator for active workers.
     */
    @Bean
    public HealthIndicator workersHealthIndicator(DataSource dataSource) {
        return () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT COUNT(*) FROM workers WHERE status IN ('ONLINE', 'BUSY')");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long count = rs.getLong(1);
                    return Health.up()
                            .withDetail("activeWorkers", count)
                            .build();
                }
                return Health.unknown().build();
            } catch (Exception e) {
                return Health.down()
                        .withDetail("error", e.getMessage())
                        .build();
            }
        };
    }

    /**
     * Encapsulates custom pipeline metrics.
     */
    public static class PipelineMetrics {

        private final Counter pipelineRunsTotal;
        private final Timer pipelineRunDuration;
        private final MeterRegistry registry;

        public PipelineMetrics(MeterRegistry registry, JobQueue jobQueue) {
            this.registry = registry;

            this.pipelineRunsTotal = Counter.builder("pipeline_runs_total")
                    .description("Total number of pipeline runs triggered")
                    .register(registry);

            this.pipelineRunDuration = Timer.builder("pipeline_run_duration")
                    .description("Duration of pipeline runs")
                    .register(registry);

            Gauge.builder("queue_size", jobQueue, JobQueue::queueSize)
                    .description("Number of jobs currently in the queue")
                    .register(registry);
        }

        public Counter getPipelineRunsTotal() {
            return pipelineRunsTotal;
        }

        public Timer getPipelineRunDuration() {
            return pipelineRunDuration;
        }

        public void registerActiveWorkersGauge(DataSource dataSource) {
            Gauge.builder("active_workers", () -> {
                        try (Connection conn = dataSource.getConnection();
                             PreparedStatement ps = conn.prepareStatement(
                                     "SELECT COUNT(*) FROM workers WHERE status IN ('ONLINE', 'BUSY')");
                             ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                return rs.getLong(1);
                            }
                        } catch (Exception e) {
                            log.debug("Failed to query active workers count: {}", e.getMessage());
                        }
                        return 0L;
                    })
                    .description("Number of active worker nodes")
                    .register(registry);
        }
    }
}
