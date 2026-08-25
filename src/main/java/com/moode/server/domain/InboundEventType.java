package com.moode.server.domain;

import java.util.Arrays;

/**
 * AI 서버가 /internal/v1/hazard-events 로 보내는 메시지 종류.
 *
 * <p>HAZARD_CONFIRMED 는 위험 확정 즉시, CLIP_READY 는 클립이 완성된 뒤(보통 10~20초 후)
 * 같은 eventUid 로 도착한다.
 */
public enum InboundEventType {
    HAZARD_CONFIRMED,
    CLIP_READY;

    public static InboundEventType from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("eventType 은 필수입니다.");
        }
        String normalized = raw.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(type -> type.name().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 eventType 입니다. " + raw));
    }
}
