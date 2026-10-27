package com.moode.server.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * 감지되는 위험요소 종류. 감지 경로가 둘이다.
 * PUDDLE/POTHOLE/FORKLIFT 는 영상(AI 서버)에서, STUMBLE/FALL/UNCONSCIOUS 는 헬멧 IMU 에서 온다.
 * AI 서버는 소문자 원본값(puddle / pothole / forklift)만 보내고 등급 판정은 하지 않는다.
 * ({@link #from} 은 AI 서버 수신 전용이라 IMU 쪽 값은 서버가 직접 지정한다)
 *
 * <p>화면에 그대로 쓸 한글 문구도 여기서 함께 관리한다. 프론트가 enum -> 한글 매핑 테이블을
 * 따로 들고 있지 않아도 되고, 문구가 바뀌어도 프론트 배포 없이 반영된다.
 *
 * <p>여기 적힌 severity 는 "회사 안전규정이 지정되지 않았을 때의 기본값"이다.
 * 실제 판정은 application.yml 의 {@code hazard.severity.*} 를 우선하며,
 * 그 해석은 {@link com.moode.server.config.HazardSeverityPolicy} 한 곳에서만 이뤄진다.
 */
@Getter
@RequiredArgsConstructor
public enum HazardType {

    PUDDLE(HazardSeverity.WARNING, "물웅덩이", "미끄럼 및 낙상",
            "바닥에 물웅덩이 형성. 미끄럼으로 인한 낙상 및 추락 위험.",
            "작업자에게 위험 구역 경고가 필요합니다."),

    POTHOLE(HazardSeverity.WARNING, "바닥 파임", "전도 및 발목 부상",
            "바닥 파임 감지. 걸려 넘어짐으로 인한 전도 위험.",
            "작업자에게 위험 구역 경고가 필요합니다."),

    FORKLIFT(HazardSeverity.DANGER, "지게차 접근", "충돌 및 협착",
            "작업 통로에 지게차 접근 감지. 충돌 위험.",
            "작업자에게 즉시 경고하고 구역 접근 통제가 필요합니다."),

    // 아래 셋은 영상이 아니라 헬멧 IMU 에서 온다. category=FALL, source=IMU_HEALTH 로 저장되고
    // 감지 주체만 다를 뿐 목록/상세/조치는 영상 이벤트와 완전히 같은 경로를 탄다.
    STUMBLE(HazardSeverity.WARNING, "휘청거림", "균형 상실 및 추락 위험",
            "헬멧 센서에서 휘청거림 감지. 균형 상실로 인한 추락 위험.",
            "작업자 상태를 확인하고 휴식을 권고하세요."),

    FALL(HazardSeverity.DANGER, "추락/쓰러짐", "추락 및 의식 소실",
            "헬멧 센서에서 추락 또는 자세 붕괴 감지.",
            "작업자 상태를 즉시 확인하고 필요 시 구조를 요청하세요."),

    UNCONSCIOUS(HazardSeverity.DANGER, "의식불명", "의식 소실 및 응급 상황",
            "낙상 후 본인 확인 안내에도 움직임이 없어 헬멧이 의식불명으로 판정.",
            "즉시 현장으로 이동하고 119 등 응급 구조를 요청하세요."),

    HEALTH_ABNORMAL(HazardSeverity.DANGER, "건강 이상 감지", "건강 지표 이상",
            "헬멧 생체신호 센서에서 건강 위험이 감지되었습니다.",
            "작업을 중단시키고 작업자의 건강 상태를 즉시 확인하세요.");

    private final HazardSeverity defaultSeverity;

    /** 화면에 그대로 표시할 한글 라벨 */
    private final String label;

    /** 위험 요약 (상세의 riskDescription) */
    private final String riskDescription;

    /** 상황 설명 (목록의 description, 상세의 detail) */
    private final String description;

    /** 권장 조치 (상세의 recommendedAction) */
    private final String recommendedAction;

    /**
     * 응답에서는 AI 서버가 보내는 원본과 같은 소문자로 내보낸다. (puddle / pothole / forklift)
     * DB 저장은 @Enumerated(STRING) 이라 대문자 그대로다.
     */
    @JsonValue
    public String toJsonValue() {
        return name().toLowerCase();
    }

    /**
     * AI 서버가 보낸 원본 문자열을 enum 으로 변환한다. 대소문자/공백은 무시.
     * 알 수 없는 값은 IllegalArgumentException -> GlobalExceptionHandler 에서 400 으로 응답된다.
     */
    public static HazardType from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("hazardType 은 필수입니다.");
        }
        String normalized = raw.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(type -> type.name().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 hazardType 입니다. " + raw));
    }
}
