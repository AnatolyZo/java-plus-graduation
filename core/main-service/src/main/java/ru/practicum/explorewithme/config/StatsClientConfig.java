package ru.practicum.explorewithme.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.support.RetryTemplate;
import ru.practicum.explorewithme.StatsClient;

@Configuration
public class StatsClientConfig {
    @Autowired
    private RetryTemplate retryTemplate;

    @Bean
    public StatsClient statsClient(@Value("${stats.server.name}") String statsServerName,
                                   RestTemplateBuilder builder,
                                   DiscoveryClient discoveryClient,
                                   RetryTemplate retryTemplate) {
        return new StatsClient(statsServerName, builder, discoveryClient, retryTemplate);
    }
}
