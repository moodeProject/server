package com.moode.server.common;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 응답의 모든 시각은 ISO 8601 + 오프셋(2026-08-25T10:24:03.412+09:00)으로 나간다.
 * 엔티티는 서버 로컬시각 기준 LocalDateTime 이라 여기서 오프셋을 붙인다.
 */
public final class Times {

    private Times() {
    }

    public static OffsetDateTime toOffset(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }
}
