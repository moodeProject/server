package com.moode.server.service.impl;

import com.moode.server.domain.HazardType;
import com.moode.server.dto.request.HazardClipRequest;
import com.moode.server.dto.request.HazardEventIngestRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 영상 단독 감지를 "헬멧 경고"로만 붙잡아 두는 곳.
 *
 * <p>영상에 위험이 보였다는 것만으로는 관리자 대시보드에 위험으로 올리지 않는다. 헬멧 스피커로
 * 작업자에게만 경고하고, 앞뒤 {@link #ESCALATION_WINDOW} 안에 헬멧 센서 이상(자세·건강·추락)이
 * 겹칠 때 비로소 위험 이벤트로 올린다. 그 판단에 필요한 최근 상태를 헬멧(deviceId)별로 들고 있다.
 *
 * <p>메모리에만 둔다. 서버를 재시작하면 대기 중인 경고는 사라지지만, 경고는 수십 초짜리
 * 상태라 잃어도 다음 감지에서 다시 생긴다. EC2 단일 인스턴스 전제다.
 */
@Component
public class VisionWarningRegistry {

    /** 영상 경고와 센서 이상이 이 시간 안에 겹치면 위험으로 올린다 (앞뒤 모두). */
    static final Duration ESCALATION_WINDOW = Duration.ofSeconds(30);

    /** 헬멧이 이 시간 안에 받은 경고만 스피커로 안내한다. 늦게 붙은 헬멧이 지난 경고를 울리지 않게. */
    static final Duration HELMET_NOTICE_WINDOW = Duration.ofSeconds(10);

    private final AtomicLong sequence = new AtomicLong();
    private final Map<String, VisionWarning> latestByDevice = new ConcurrentHashMap<>();
    private final Map<String, VisionWarning> byEventUid = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastAbnormalByDevice = new ConcurrentHashMap<>();

    public VisionWarning record(String deviceId, HazardEventIngestRequest request,
                                HazardType hazardType, boolean escalated) {
        VisionWarning warning = new VisionWarning(sequence.incrementAndGet(), deviceId, hazardType,
                request, Instant.now(), escalated);
        latestByDevice.put(deviceId, warning);
        byEventUid.put(request.getEventId(), warning);
        pruneOld();
        return warning;
    }

    public void noteSensorAbnormal(String deviceId) {
        lastAbnormalByDevice.put(deviceId, Instant.now());
    }

    public boolean sensorAbnormalWithin(String deviceId) {
        Instant last = lastAbnormalByDevice.get(deviceId);
        return last != null && last.isAfter(Instant.now().minus(ESCALATION_WINDOW));
    }

    /** 아직 위험으로 올라가지 않은 영상 경고인지. 클립을 이벤트 대신 경고에 맡길지 정할 때 쓴다. */
    public Optional<VisionWarning> findPending(String eventUid) {
        return Optional.ofNullable(byEventUid.get(eventUid)).filter(w -> !w.isEscalated());
    }

    /**
     * 센서 이상이 들어왔을 때 올려야 할 영상 경고를 꺼낸다. 꺼낸 경고는 escalated 로 표시되어
     * 다시 꺼내지지 않는다 (센서는 초당 5번 들어오므로 한 번만 올려야 한다).
     */
    public Optional<VisionWarning> takeForEscalation(String deviceId) {
        VisionWarning warning = latestByDevice.get(deviceId);
        if (warning == null || warning.getReceivedAt().isBefore(Instant.now().minus(ESCALATION_WINDOW))) {
            return Optional.empty();
        }
        return warning.markEscalated() ? Optional.of(warning) : Optional.empty();
    }

    /** 헬멧 응답에 실어 보낼 최근 경고. 헬멧은 seq 가 바뀔 때만 안내 음성을 낸다. */
    public Optional<VisionWarning> activeForHelmet(String deviceId) {
        return Optional.ofNullable(latestByDevice.get(deviceId))
                .filter(w -> w.getReceivedAt().isAfter(Instant.now().minus(HELMET_NOTICE_WINDOW)));
    }

    /** 테스트끼리 상태가 새지 않게 비운다 (스프링 컨텍스트가 테스트 클래스 사이에 재사용됨). */
    public void clear() {
        latestByDevice.clear();
        byEventUid.clear();
        lastAbnormalByDevice.clear();
    }

    private void pruneOld() {
        Instant cutoff = Instant.now().minus(ESCALATION_WINDOW.multipliedBy(4));
        byEventUid.values().removeIf(w -> w.getReceivedAt().isBefore(cutoff));
    }

    public static final class VisionWarning {
        private final long seq;
        private final String deviceId;
        private final HazardType hazardType;
        private final HazardEventIngestRequest request;
        private final Instant receivedAt;
        private boolean escalated;
        private HazardClipRequest clip;
        private BigDecimal clipOffsetSeconds;

        private VisionWarning(long seq, String deviceId, HazardType hazardType,
                              HazardEventIngestRequest request, Instant receivedAt, boolean escalated) {
            this.seq = seq;
            this.deviceId = deviceId;
            this.hazardType = hazardType;
            this.request = request;
            this.receivedAt = receivedAt;
            this.escalated = escalated;
        }

        /** @return 이번 호출로 escalated 가 된 경우에만 true */
        synchronized boolean markEscalated() {
            if (escalated) {
                return false;
            }
            escalated = true;
            return true;
        }

        public synchronized boolean isEscalated() {
            return escalated;
        }

        /** 위험으로 오르기 전에 도착한 클립을 맡아 둔다. 위험으로 오를 때 이벤트에 붙인다. */
        public synchronized void keepClip(HazardClipRequest clip, BigDecimal offsetSeconds) {
            this.clip = clip;
            this.clipOffsetSeconds = offsetSeconds;
        }

        public synchronized HazardClipRequest getClip() {
            return clip;
        }

        public synchronized BigDecimal getClipOffsetSeconds() {
            return clipOffsetSeconds;
        }

        public long getSeq() {
            return seq;
        }

        public String getDeviceId() {
            return deviceId;
        }

        public HazardType getHazardType() {
            return hazardType;
        }

        public HazardEventIngestRequest getRequest() {
            return request;
        }

        public Instant getReceivedAt() {
            return receivedAt;
        }
    }
}
