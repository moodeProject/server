package com.moode.server.domain;

/**
 * GET /api/workers/alerts 의 type 필터. 센서 기록을 추락 이력과 건강 이상 이력으로 나눠 볼 때 쓴다.
 * 비워 두면 예전처럼 둘 다 돌려준다.
 */
public enum SensorAlertType {
    /** fallState 가 경고 단계 이상인 기록만 (자세 STABLE·추락 NORMAL 인 건강 이상 기록은 제외) */
    FALL,
    /** healthState 가 조치 필요 단계인 기록만 */
    HEALTH
}
