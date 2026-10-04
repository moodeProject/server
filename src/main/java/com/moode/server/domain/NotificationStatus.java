package com.moode.server.domain;

/**
 * notification.status. ERD에 별도의 '읽음' 컬럼이 없어, CONFIRMED 를 읽음으로 간주한다
 * (PATCH /api/notifications/{id}/read 는 SENT -> CONFIRMED 전환).
 */
public enum NotificationStatus {
    SENT,
    CONFIRMED,
    FAILED
}
