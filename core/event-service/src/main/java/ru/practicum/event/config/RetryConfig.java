package ru.practicum.event.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.MaxAttemptsRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

@Configuration
public class RetryConfig {

    @Bean
    public RetryTemplate retryTemplate() {
        RetryTemplate template = new RetryTemplate();

        FixedBackOffPolicy backOff = new FixedBackOffPolicy();
        backOff.setBackOffPeriod(3000L); // 3 секунды
        template.setBackOffPolicy(backOff);

        MaxAttemptsRetryPolicy policy = new MaxAttemptsRetryPolicy();
        policy.setMaxAttempts(3);
        template.setRetryPolicy(policy);

        return template;
    }

}