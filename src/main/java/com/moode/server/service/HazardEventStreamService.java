package com.moode.server.service;

import com.moode.server.domain.HazardEvent;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 대시보드 실시간 목록용 SSE 브로드캐스트.
 *
 * <p>수신(/internal/v1/hazard-events)에서 저장 직후 반드시 push 해야 한다.
 * 저장만 하고 push 를 빠뜨리면 대시보드가 새로고침 전까지 갱신되지 않고,
 * 프론트 쪽에서는 원인을 찾기 어렵다.
 *
 * <p>구현은 어떤 경우에도 예외를 밖으로 던지지 않는다. 구독자 하나가 끊겼다는 이유로
 * 이벤트 수신이 실패하면 AI 서버가 재시도를 시작하기 때문.
 */
public interface HazardEventStreamService {

    SseEmitter subscribe();

    /** HAZARD_CONFIRMED 수신 시. event: hazard, data 는 목록 항목과 같은 DTO */
    void publishHazard(HazardEvent event);

    /** CLIP_READY 수신 시. event: clip-ready, data 는 {"eventId": PK} */
    void publishClipReady(Long eventId);
}
