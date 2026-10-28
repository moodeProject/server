package com.moode.server.service.impl;

import com.moode.server.domain.HazardType;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 영상(헬멧 카메라)에서 확정된 위험을 헬멧 스피커로 알리기 위한 임시 우편함.
 *
 * <p>AI 서버가 위험을 확정하면 여기 적어 두고, 헬멧이 0.2초마다 보내는 센서 전송의 응답에
 * {@link #HELMET_NOTICE_WINDOW} 동안 실어 준다. 헬멧은 번호(seq)가 바뀔 때만 "조심하세요" 안내를 낸다.
 * 대시보드 사고 생성 로직과는 무관하다(그쪽은 그대로 동작).
 *
 * <p>나중에 라즈베리파이가 헬멧에 직접 신호를 보내게 되면 이 클래스와 응답 필드 두 개만 지우면 된다.
 * 메모리에만 두므로 서버를 재시작하면 대기 중인 경고는 사라진다(10초짜리라 문제없음).
 */
@Component
public class HelmetVisionWarningRegistry {

    /** 헬멧이 이 시간 안에 받은 경고만 안내한다. 늦게 붙은 헬멧이 지난 경고를 울리지 않게. */
    static final Duration HELMET_NOTICE_WINDOW = Duration.ofSeconds(10);

    private final AtomicLong sequence = new AtomicLong();
    private final Map<String, Warning> latestByDevice = new ConcurrentHashMap<>();

    public void record(String deviceId, HazardType hazardType) {
        latestByDevice.put(deviceId, new Warning(sequence.incrementAndGet(), hazardType, Instant.now()));
    }

    public Optional<Warning> activeFor(String deviceId) {
        return Optional.ofNullable(latestByDevice.get(deviceId))
                .filter(w -> w.receivedAt().isAfter(Instant.now().minus(HELMET_NOTICE_WINDOW)));
    }

    /** 테스트끼리 상태가 새지 않게 비운다 (스프링 컨텍스트가 테스트 클래스 사이에 재사용됨). */
    public void clear() {
        latestByDevice.clear();
    }

    public record Warning(long seq, HazardType hazardType, Instant receivedAt) {
    }
}
