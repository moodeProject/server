package com.moode.server.dto.response;

import com.moode.server.common.Times;
import com.moode.server.domain.HazardClip;
import com.moode.server.service.ClipPlaybackUrl;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 상세의 clip 블록. 클립이 아직 도착하지 않았으면 이 블록 자체가 null 이다.
 *
 * <p>playbackUrl 은 DB 에 저장된 값이 아니라 이 응답을 만들 때 발급한 15분짜리 임시 URL 이다.
 * 프론트가 캐시하면 만료돼서 재생이 실패한다. bucket / objectKey 는 내부 저장 위치라 내보내지 않는다.
 */
@Getter
@Builder
public class HazardClipResponse {

    private String playbackUrl;
    private OffsetDateTime expiresAt;
    private BigDecimal durationSeconds;

    // 재생바의 "감지 시점" 마커 위치(초)
    private BigDecimal markedOffsetSeconds;

    public static HazardClipResponse of(HazardClip clip, ClipPlaybackUrl playbackUrl) {
        return HazardClipResponse.builder()
                .playbackUrl(playbackUrl.url())
                .expiresAt(Times.toOffset(playbackUrl.expiresAt()))
                .durationSeconds(clip.getDurationSeconds())
                .markedOffsetSeconds(clip.getMarkedOffsetSeconds())
                .build();
    }
}
