package com.moode.server.domain;

/**
 * 이벤트를 만들어낸 경로. ERD 의 source.
 * 영상 기반 외부 위험요소는 항상 VISION_AI 다.
 */
public enum HazardSource {
    VISION_AI,
    IMU_HEALTH,
    MANUAL
}
