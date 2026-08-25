package com.moode.server.service.impl;

import com.moode.server.domain.HazardEvent;
import com.moode.server.dto.response.HazardEventListItemResponse;
import com.moode.server.service.HazardEventStreamService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
public class SseHazardEventStreamService implements HazardEventStreamService {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    // 0 이면 무제한. 짧게 잡으면 연결이 계속 끊겼다 붙는다.
    private final long timeoutMillis;

    public SseHazardEventStreamService(
            @Value("${hazard.stream.timeout-millis:0}") long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    @Override
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(timeoutMillis);

        // 세 콜백 모두에서 컬렉션에서 빼야 한다. 하나라도 빠뜨리면 끊긴 연결에 계속 send 를 시도하며
        // emitter 가 쌓여 메모리가 샌다.
        emitter.onCompletion(() -> remove(emitter, "completion"));
        emitter.onTimeout(() -> {
            remove(emitter, "timeout");
            emitter.complete();
        });
        emitter.onError(throwable -> remove(emitter, "error"));

        emitters.add(emitter);
        log.info("SSE 구독 시작: 현재 구독자={}", emitters.size());

        // 연결 직후 한 건 보내 응답 헤더가 바로 흘러나가게 한다.
        // 이게 없으면 중간 프록시가 첫 이벤트까지 응답을 버퍼링해 연결된 걸 프론트가 모를 수 있다.
        if (!send(emitter, "connected", Map.of("connectedAt", LocalDateTime.now()))) {
            return emitter;
        }
        return emitter;
    }

    @Override
    public void publishHazard(HazardEvent event) {
        // 목록 API 항목과 같은 구조여야 프론트가 실시간 수신분을 그대로 목록에 이어붙일 수 있다.
        // 이 시점에는 클립이 아직 없으므로 hasClip 은 항상 false 다.
        broadcast("hazard", HazardEventListItemResponse.of(event, false));
    }

    @Override
    public void publishClipReady(Long eventId) {
        broadcast("clip-ready", Map.of("eventId", eventId));
    }

    /**
     * 유휴 연결을 중간 프록시가 끊지 않도록 주기적으로 흘려보낸다.
     * 타임아웃을 무제한으로 두면 끊긴 연결은 이 하트비트의 전송 실패로만 감지된다.
     */
    @Scheduled(fixedDelayString = "${hazard.stream.heartbeat-millis:30000}")
    public void heartbeat() {
        if (emitters.isEmpty()) {
            return;
        }
        // 명세상 페이로드는 빈 객체다. 프론트는 무시한다.
        broadcast("heartbeat", Map.of());
    }

    private void broadcast(String eventName, Object data) {
        if (emitters.isEmpty()) {
            log.debug("SSE 구독자가 없어 {} 이벤트를 보내지 않음", eventName);
            return;
        }
        int delivered = 0;
        for (SseEmitter emitter : emitters) {
            if (send(emitter, eventName, data)) {
                delivered++;
            }
        }
        log.debug("SSE {} 전송: {}/{}", eventName, delivered, emitters.size());
    }

    /**
     * @return 전송 성공 여부. 실패한 emitter 는 즉시 컬렉션에서 제거한다.
     */
    private boolean send(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(data, MediaType.APPLICATION_JSON));
            return true;
        } catch (Exception e) {
            // IOException(클라이언트가 끊음) 외에 이미 complete 된 emitter 의 IllegalStateException 도 여기로 온다.
            // 어느 쪽이든 더 쓸 수 없는 연결이므로 바로 제거한다. 예외는 밖으로 던지지 않는다.
            remove(emitter, "send 실패: " + e.getClass().getSimpleName());
            return false;
        }
    }

    private void remove(SseEmitter emitter, String reason) {
        if (emitters.remove(emitter)) {
            log.info("SSE 구독 해제({}): 남은 구독자={}", reason, emitters.size());
        }
    }

    /** 테스트/모니터링용 */
    public int subscriberCount() {
        return emitters.size();
    }
}
