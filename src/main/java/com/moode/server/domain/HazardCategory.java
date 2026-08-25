package com.moode.server.domain;

/**
 * 위험 이벤트의 대분류.
 * EXTERNAL 은 영상 기반 외부 위험요소(AI 서버 YOLO 감지), 나머지는 센서 기반 판단 영역.
 */
public enum HazardCategory {
    EXTERNAL,
    HEALTH,
    FALL
}
