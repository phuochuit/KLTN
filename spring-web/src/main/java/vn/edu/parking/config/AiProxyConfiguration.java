package vn.edu.parking.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class AiProxyConfiguration {
    @Bean
    RestClient parkingAiRestClient(
            @Value("${parking.ai.base-url:http://127.0.0.1:8001}") String baseUrl,
            @Value("${parking.ai.service-token:}") String serviceToken,
            @Value("${parking.ai.connect-timeout:5s}") Duration connectTimeout,
            @Value("${parking.ai.read-timeout:5m}") Duration readTimeout) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(requestFactory)
            .defaultHeader("X-Parking-Service-Token", serviceToken)
            .build();
    }
}
