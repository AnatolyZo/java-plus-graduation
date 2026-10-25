package ru.practicum.explorewithme.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.support.RetryTemplate;
import ru.practicum.explorewithme.StatsClient;
import ru.practicum.explorewithme.exception.StatsServerUnavailable;

import java.net.URI;

@Configuration
public class StatsClientConfig {

    @Autowired
    private DiscoveryClient discoveryClient;

    @Autowired
    private RetryTemplate retryTemplate;

    @Value("${stats.server.name}")
    private String statsServerName;

    @Bean
    public StatsClient statsClient(RestTemplateBuilder builder) {
        String statsClientUri = makeUri().toString();
        return new StatsClient(statsClientUri, builder);
    }

    private ServiceInstance getInstance() {
        try {
            return discoveryClient
                    .getInstances(statsServerName)
                    .getFirst();
        } catch (Exception exception) {
            throw new StatsServerUnavailable(
                    "Ошибка обнаружения адреса сервиса статистики с id: " + statsServerName,
                    exception
            );
        }
    }

    private URI makeUri() {
        ServiceInstance instance = retryTemplate.execute(cxt -> getInstance());
        return URI.create("http://" + instance.getHost() + ":" + instance.getPort());
    }
}
