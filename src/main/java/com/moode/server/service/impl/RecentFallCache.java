package com.moode.server.service.impl;

import com.moode.server.domain.WorkerState;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 실시간 상태 조회용 "최근 추락" 보관소.
 * 헬멧이 0.2초마다 보내서 추락 줄이 곧바로 정상 줄에 덮이므로,
 * 추락 후 HOLD_SECONDS 동안은 실시간 응답에 추락 값을 유지한다.
 * DB 원본(sensor_data, hazard_event)은 건드리지 않는다.
 */
@Component
public class RecentFallCache {

    static final long HOLD_SECONDS = 10;

    public record HeldFall(WorkerState state, Double confidence, LocalDateTime recordedAt) {}

    private final Map<String, HeldFall> lastFallByDevice = new ConcurrentHashMap<>();

    /** 추락(FALLEN/UNCONSCIOUS)으로 판정된 순간만 기록한다. 그 외 상태는 무시. */
    public void record(String deviceId, WorkerState state, Double confidence) {
        if (deviceId == null || !isFall(state)) return;
        lastFallByDevice.put(deviceId, new HeldFall(state, confidence, LocalDateTime.now()));
    }

    /** HOLD_SECONDS 안에 기록된 추락이 있으면 돌려주고, 지났으면 지우고 empty. */
    public Optional<HeldFall> recent(String deviceId) {
        HeldFall held = lastFallByDevice.get(deviceId);
        if (held == null) return Optional.empty();
        if (held.recordedAt().isBefore(LocalDateTime.now().minusSeconds(HOLD_SECONDS))) {
            lastFallByDevice.remove(deviceId, held);
            return Optional.empty();
        }
        return Optional.of(held);
    }

    public static boolean isFall(WorkerState state) {
        return state == WorkerState.FALLEN || state == WorkerState.UNCONSCIOUS;
    }
}
