package com.moode.server.config;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableFeignClients(basePackages = "com.moode.server.infrastructure")
public class FeignConfig {
}
