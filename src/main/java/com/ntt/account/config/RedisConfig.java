package com.ntt.account.config;

import com.ntt.account.model.dto.AccountRuleDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

  @Bean
  public ReactiveRedisOperations<String, AccountRuleDefinition> accountRuleRedisOperations(
      ReactiveRedisConnectionFactory factory) {
    Jackson2JsonRedisSerializer<AccountRuleDefinition> serializer =
        new Jackson2JsonRedisSerializer<>(AccountRuleDefinition.class);

    RedisSerializationContext<String, AccountRuleDefinition> context =
        RedisSerializationContext.<String, AccountRuleDefinition>newSerializationContext(
                new StringRedisSerializer())
            .value(serializer)
            .build();

    return new ReactiveRedisTemplate<>(factory, context);
  }
}
