package dev.gimi.worker.config;

import dev.gimi.engine.executor.StepExecutorRegistry;
import dev.gimi.engine.log.RedisLogStreamer;
import dev.gimi.engine.queue.RedisJobQueue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPool;

import java.net.URI;

/**
 * Creates shared infrastructure beans required by the worker:
 * Redis connection pool, job queue, log streamer, and step executor registry.
 */
@Configuration
public class WorkerBeanConfig {

    /**
     * Creates a Jedis connection pool from the configured Redis URL.
     *
     * @param config the worker configuration containing the Redis URL
     * @return a configured {@link JedisPool}
     */
    @Bean
    public JedisPool jedisPool(WorkerConfig config) {
        return new JedisPool(URI.create(config.getRedisUrl()));
    }

    /**
     * Creates a Redis-backed job queue for dequeuing and managing jobs.
     *
     * @param jedisPool the Jedis connection pool
     * @return a {@link RedisJobQueue}
     */
    @Bean
    public RedisJobQueue redisJobQueue(JedisPool jedisPool) {
        return new RedisJobQueue(jedisPool);
    }

    /**
     * Creates a Redis-backed log streamer for publishing execution logs.
     *
     * @param jedisPool the Jedis connection pool
     * @return a {@link RedisLogStreamer}
     */
    @Bean
    public RedisLogStreamer redisLogStreamer(JedisPool jedisPool) {
        return new RedisLogStreamer(jedisPool);
    }

    /**
     * Creates the default step executor registry containing the shell and Docker executors.
     *
     * @return a {@link StepExecutorRegistry}
     */
    @Bean
    public StepExecutorRegistry stepExecutorRegistry() {
        return StepExecutorRegistry.createDefault();
    }
}
