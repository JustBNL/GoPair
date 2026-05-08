package com.gopair.roomservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(connectionFactory);
        return template;
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        template.afterPropertiesSet();
        return template;
    }

    @Bean(name = "roomPreReserveScript")
    public DefaultRedisScript<Long> roomPreReserveScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/room_pre_reserve.lua"));
        script.setResultType(Long.class);
        return script;
    }

    @Bean(name = "roomRollbackReserveScript")
    public DefaultRedisScript<Long> roomRollbackReserveScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/room_rollback_reserve.lua"));
        script.setResultType(Long.class);
        return script;
    }

    @Bean(name = "roomMarkRemovalScript")
    public DefaultRedisScript<Long> roomMarkRemovalScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/room_mark_removal.lua"));
        script.setResultType(Long.class);
        return script;
    }

    @Bean(name = "roomRollbackRemovalScript")
    public DefaultRedisScript<Long> roomRollbackRemovalScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/room_rollback_removal.lua"));
        script.setResultType(Long.class);
        return script;
    }
} 