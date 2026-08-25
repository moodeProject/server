package com.moode.server.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * S3 pre-signed URL 발급기.
 *
 * <p>clip.storage.enabled=true 일 때만 빈이 만들어진다. 자격증명이 없는 환경(로컬/테스트)에서
 * 기동이 깨지지 않게 하기 위함이며, 이 빈이 없으면 S3 클립은 재생 URL 없이 내려간다.
 * 자격증명은 표준 체인(환경변수 / EC2 인스턴스 프로파일 등)을 그대로 쓴다.
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "clip.storage", name = "enabled", havingValue = "true")
public class S3Config {

    private final ClipStorageProperties clipStorageProperties;

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .region(Region.of(clipStorageProperties.getRegion()))
                .build();
    }
}
