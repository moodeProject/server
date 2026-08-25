package com.moode.server.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 증거 클립 저장소 설정.
 *
 * <pre>
 * clip:
 *   storage:
 *     region: ap-northeast-2
 *     presign-ttl: 15m      # pre-signed URL 만료
 *     local-base-url: ...   # LOCAL 저장분을 내려줄 베이스 URL
 * </pre>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "clip.storage")
public class ClipStorageProperties {

    private String region = "ap-northeast-2";

    // pre-signed URL 만료. 짧게 잡을수록 URL 유출 시 노출 시간이 줄어든다.
    private Duration presignTtl = Duration.ofMinutes(15);

    // StorageType.LOCAL 로 저장된 클립을 내려줄 때 붙일 베이스 URL
    private String localBaseUrl = "";
}
