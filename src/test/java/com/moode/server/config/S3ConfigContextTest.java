package com.moode.server.config;

import com.moode.server.domain.HazardClip;
import com.moode.server.domain.StorageType;
import com.moode.server.service.ClipUrlIssuer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 운영 프로필은 clip.storage.enabled=true 라 S3Presigner 빈이 만들어진다.
 * 자격증명이 없는 환경에서도 기동이 깨지지 않고, 발급 실패는 URL=null 로 흡수되는지 확인한다.
 * (클립 재생이 안 되더라도 위험 이벤트 목록/상세/알림은 계속 동작해야 한다)
 */
@SpringBootTest(properties = {
        "clip.storage.enabled=true",
        "clip.storage.region=ap-northeast-2"
})
@ActiveProfiles("test")
class S3ConfigContextTest {

    @Autowired
    private ClipUrlIssuer clipUrlIssuer;

    @Test
    @DisplayName("S3 설정이 켜져 있어도 자격증명 없이 기동되고 발급 실패는 null 로 흡수된다")
    void contextLoadsAndIssuingDegradesGracefully() {
        HazardClip clip = HazardClip.builder()
                .storageType(StorageType.S3)
                .bucket("safe-helmet-clips")
                .objectKey("clips/2026/08/25/H-001_083744.mp4")
                .durationSeconds(new BigDecimal("16.30"))
                .frameCount(163)
                .build();

        // 자격증명이 있으면 URL 이, 없으면 null 이 온다. 어느 쪽이든 예외는 나지 않아야 한다.
        assertThat(clipUrlIssuer.issue(clip)).isNotNull();
    }
}
