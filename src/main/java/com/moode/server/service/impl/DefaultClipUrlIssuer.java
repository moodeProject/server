package com.moode.server.service.impl;

import com.moode.server.config.ClipStorageProperties;
import com.moode.server.domain.HazardClip;
import com.moode.server.domain.StorageType;
import com.moode.server.service.ClipPlaybackUrl;
import com.moode.server.service.ClipUrlIssuer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.LocalDateTime;

/**
 * 조회 시점에 재생 URL을 발급한다. DB에는 bucket + objectKey 만 있고 URL은 없다.
 *
 * <p>S3Presigner 빈이 없으면(자격증명 미설정 환경) S3 클립은 url=null 로 내려간다.
 * 기동을 실패시키지 않는 이유는, 클립 재생이 안 되더라도 위험 이벤트 목록/상세와 알림은
 * 계속 동작해야 하기 때문이다.
 */
@Slf4j
@Component
public class DefaultClipUrlIssuer implements ClipUrlIssuer {

    private final ObjectProvider<S3Presigner> s3PresignerProvider;
    private final ClipStorageProperties properties;

    public DefaultClipUrlIssuer(ObjectProvider<S3Presigner> s3PresignerProvider,
                                ClipStorageProperties properties) {
        this.s3PresignerProvider = s3PresignerProvider;
        this.properties = properties;
    }

    @Override
    public ClipPlaybackUrl issue(HazardClip clip) {
        if (clip.getStorageType() == StorageType.LOCAL) {
            return issueLocal(clip);
        }
        return issuePresigned(clip);
    }

    private ClipPlaybackUrl issuePresigned(HazardClip clip) {
        S3Presigner presigner = s3PresignerProvider.getIfAvailable();
        if (presigner == null) {
            log.warn("S3Presigner 가 없어 재생 URL 을 발급하지 못했습니다. clip.storage.enabled 설정을 확인하세요. clipId={}",
                    clip.getId());
            return new ClipPlaybackUrl(null, null);
        }
        if (clip.getBucket() == null || clip.getBucket().isBlank()) {
            log.warn("S3 클립인데 bucket 이 비어 있어 재생 URL 을 발급하지 못했습니다. clipId={}", clip.getId());
            return new ClipPlaybackUrl(null, null);
        }

        try {
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(properties.getPresignTtl())
                    .getObjectRequest(GetObjectRequest.builder()
                            .bucket(clip.getBucket())
                            .key(clip.getObjectKey())
                            .build())
                    .build();

            String url = presigner.presignGetObject(presignRequest).url().toString();
            return new ClipPlaybackUrl(url, LocalDateTime.now().plus(properties.getPresignTtl()));
        } catch (Exception e) {
            // 재생 URL 발급 실패로 상세 조회 전체가 500 이 되면 안 된다. 나머지 정보는 그대로 보여준다.
            log.error("pre-signed URL 발급 실패: clipId={}, {}", clip.getId(), e.getMessage(), e);
            return new ClipPlaybackUrl(null, null);
        }
    }

    private ClipPlaybackUrl issueLocal(HazardClip clip) {
        String base = properties.getLocalBaseUrl();
        if (base == null || base.isBlank()) {
            log.warn("clip.storage.local-base-url 이 없어 LOCAL 클립 재생 URL 을 만들지 못했습니다. clipId={}", clip.getId());
            return new ClipPlaybackUrl(null, null);
        }
        String normalized = base.endsWith("/") ? base : base + "/";
        // LOCAL 은 만료 개념이 없다.
        return new ClipPlaybackUrl(normalized + clip.getObjectKey(), null);
    }
}
