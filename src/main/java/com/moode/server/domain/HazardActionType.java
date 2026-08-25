package com.moode.server.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * 대시보드에서 관리자가 취하는 조치.
 */
@Getter
@RequiredArgsConstructor
public enum HazardActionType {

    /** 경고 알림 전송 - 해당 작업자에게 푸시 알림 발송 */
    SEND_WARNING(HazardStatus.IN_PROGRESS),

    /** 구역 접근 금지 - 해당 구역의 전 작업자에게 접근 금지 알림 */
    BLOCK_ZONE(HazardStatus.IN_PROGRESS),

    /** 확인 완료 - 이벤트 상태를 RESOLVED 로 변경 */
    RESOLVE(HazardStatus.RESOLVED);

    /** 이 조치를 실행하면 이벤트가 갖게 되는 상태 */
    private final HazardStatus resultingStatus;

    public static HazardActionType from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("actionType 은 필수입니다.");
        }
        String normalized = raw.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(type -> type.name().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 actionType 입니다. " + raw));
    }
}
