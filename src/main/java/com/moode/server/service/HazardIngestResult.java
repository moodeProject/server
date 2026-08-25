package com.moode.server.service;

/**
 * 수신 처리 결과. 컨트롤러가 이 값으로 HTTP 상태코드를 정한다.
 *
 * <p>AI 서버는 상태코드로 재시도 여부를 판단한다.
 * 201/200 -> 완료, 400/401/404 -> 재시도 없이 dead-letter, 429/5xx -> 지수 백오프 재시도.
 */
public record HazardIngestResult(Status status, Long eventId) {

    public enum Status {
        RECEIVED,
        DUPLICATE_IGNORED
    }

    public static HazardIngestResult received(Long eventId) {
        return new HazardIngestResult(Status.RECEIVED, eventId);
    }

    public static HazardIngestResult duplicateIgnored() {
        return new HazardIngestResult(Status.DUPLICATE_IGNORED, null);
    }

    public boolean isCreated() {
        return status == Status.RECEIVED;
    }
}
