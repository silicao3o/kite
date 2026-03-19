package com.lite_k8s.update;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GhcrClientConfig {

    @Bean
    public GhcrClient ghcrClient(ImageWatchProperties properties) {
        return new GhcrClient(properties.getGhcrToken());
    }
}
