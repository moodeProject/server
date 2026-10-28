package com.moode.server.service.impl;

import com.moode.server.config.HazardSeverityPolicy;
import com.moode.server.domain.HazardCategory;
import com.moode.server.domain.HazardEvent;
import com.moode.server.domain.HazardSource;
import com.moode.server.domain.HazardStatus;
import com.moode.server.domain.HazardType;
import com.moode.server.domain.Helmet;
import com.moode.server.domain.SensorData;
import com.moode.server.domain.Worker;
import com.moode.server.domain.WorkerState;
import com.moode.server.domain.Zone;
import com.moode.server.dto.request.SensorDataRequest;
import com.moode.server.dto.response.SensorDataResponse;
import com.moode.server.repository.HazardEventRepository;
import com.moode.server.repository.HelmetRepository;
import com.moode.server.repository.SensorDataRepository;
import com.moode.server.repository.ZoneRepository;
import com.moode.server.service.HazardEventStreamService;
import com.moode.server.service.HazardNotificationService;
import com.moode.server.service.NotificationService;
import com.moode.server.service.SensorDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SensorDataServiceImpl implements SensorDataService {

    private final SensorDataRepository sensorDataRepository;
    private final WeatherHeatRiskCache weatherHeatRiskCache;
    private final HelmetRepository helmetRepository;
    private final ZoneRepository zoneRepository;
    private final HazardEventRepository hazardEventRepository;
    private final HazardSeverityPolicy hazardSeverityPolicy;
    private final HazardEventStreamService hazardEventStreamService;
    private final HazardNotificationService hazardNotificationService;
    private final NotificationService notificationService;
    private final RecentFallCache recentFallCache;
    private final HelmetVisionWarningRegistry helmetVisionWarningRegistry;

    private static final double ACC_LIMIT = 160.0;   // 가속도 허용 범위 (MPU6050 ±16g = 약 157 m/s^2)
    private static final double GYRO_LIMIT = 2000.0; // 각속도 허용 범위
    private static final int FALL_MERGE_WINDOW_SECONDS = 60; // 같은 헬멧의 추락 반복을 한 사고로 묶는 시간창

    @Override
    @Transactional
    public SensorDataResponse processSensorData(SensorDataRequest request) {

        // 1. 데이터 유효성 검사 (원본 값 자체가 이상하면 여기서 막음)
        if (!isValidSensorData(request)) {
            throw new IllegalArgumentException("센서 데이터가 유효 범위를 벗어났습니다.");
        }

        // 2. 직전 추락·건강 상태 — 반드시 이번 건을 저장하기 "전에" 읽어야 비교 대상이 된다.
        //    (저장 후에 읽으면 방금 넣은 행이 잡혀서 항상 "변화 없음"이 된다)
        SensorData previous = sensorDataRepository
                .findTop1ByDeviceIdOrderByIdDesc(request.getDeviceId())
                .orElse(null);
        WorkerState previousFallState = previous == null ? null : previous.getFallState();
        WorkerState previousHealthState = previous == null ? null : previous.getHealthState();

        // 3. 독립 판단 1: 추락 (펌웨어 온보드 AI 결과 + posture)
        FallCheckResult fallResult = checkFall(request);

        // 4. 독립 판단 2: 건강 (MAX30102 heartRate/spo2 + healthAbnormal + level + 외부 기상)
        WorkerState healthState = checkHealth(request);

        log.info("판단 결과: deviceId={}, fall={}({}), health={}, hrv={}, fatigue={}, heatRisk={}",
                request.getDeviceId(), fallResult.state(), fallResult.confidence(), healthState,
                request.getHrv(), request.getFatigueAbnormal(), request.getHeatRiskAbnormal());

        // 5. DB 저장
        SensorData saved = sensorDataRepository.save(
                SensorData.builder()
                        .deviceId(request.getDeviceId())
                        .ax(request.getAx()).ay(request.getAy()).az(request.getAz())
                        .gx(request.getGx()).gy(request.getGy()).gz(request.getGz())
                        .heartRate(request.getHeartRate())
                        .spo2(request.getSpo2())
                        .healthAbnormal(Boolean.TRUE.equals(request.getHealthAbnormal()))
                        .hrv(request.getHrv())
                        .fatigueAbnormal(Boolean.TRUE.equals(request.getFatigueAbnormal()))
                        .heatRiskAbnormal(Boolean.TRUE.equals(request.getHeatRiskAbnormal()))
                        .fallState(fallResult.state())
                        .fallConfidence(fallResult.confidence())
                        .healthState(healthState)
                        .posture(request.getPosture())
                        .zoneId(request.getZoneId())
                        .build()
        );

        // 실시간 조회에서 추락 값이 바로 덮이지 않도록 최근 추락을 보관 (RecentFallCache 참고)
        recentFallCache.record(request.getDeviceId(), fallResult.state(), fallResult.confidence());

        // 6. 도메인별로 각자 알림 여부 판단 + 각자 알림 발생 (섞지 않음)
        boolean fallAlert = isAlertState(fallResult.state());
        boolean healthAlert = isAlertState(healthState);

        if (fallAlert) triggerAlert(request.getDeviceId(), "FALL", fallResult.state());
        if (healthAlert) triggerAlert(request.getDeviceId(), "HEALTH", healthState);

        // 7. 헬멧/작업자 위치(비콘 구역) 갱신 — 등록 안 된 deviceId거나 미배정 헬멧이면 조용히 스킵
        //    위험 이벤트보다 먼저 실행해야 이번에 판정된 구역이 이벤트에 박제된다.
        Helmet helmet = updateWorkerLocation(request);

        // 8. 추락 위험 사고 기록 — 관리자 대시보드(/api/v1/hazard-events)로 올린다.
        Long fallHazardEventId = fallAlert
                ? raiseFallHazardEvent(request.getDeviceId(), helmet, previousFallState, fallResult, saved.getId())
                : null;

        // 9. 건강 이상 사고 기록 — 조치 필요 단계로 올라간 순간에만 만들고 작업자·관리자에게 알린다.
        Long healthHazardEventId = healthAlert
                ? raiseHealthHazardEvent(request, helmet, previousHealthState, healthState, saved.getId())
                : null;

        Optional<HelmetVisionWarningRegistry.Warning> visionWarning =
                helmetVisionWarningRegistry.activeFor(request.getDeviceId());

        return SensorDataResponse.builder()
                .id(saved.getId())
                .deviceId(saved.getDeviceId())
                .fallState(fallResult.state())
                .healthState(healthState)
                .fallConfidence(fallResult.confidence())
                .alertTriggered(fallAlert || healthAlert)
                .zoneId(saved.getZoneId())
                .recordedAt(saved.getRecordedAt())
                .fallHazardEventId(fallHazardEventId)
                .healthHazardEventId(healthHazardEventId)
                .hazardWarning(visionWarning.map(HelmetVisionWarningRegistry.Warning::hazardType).orElse(null))
                .hazardWarningSeq(visionWarning.map(HelmetVisionWarningRegistry.Warning::seq).orElse(null))
                .build();
    }

    // ===================== 1. 추락 (독립) =====================

    private FallCheckResult checkFall(SensorDataRequest request) {
        // 낙상 추론은 펌웨어(ESP32)에서 온보드로 수행하고 결과만 받는다. (FastAPI /predict 호출 제거 — 지연 때문)
        if (request.getFallDetected() == null) {
            // 온보드 판정 결과가 없는 구버전 펌웨어 등. "정상 확인됨"이 아니라 "판정 없음"이므로 구분해서 로그만 남김.
            log.info("펌웨어 낙상 판정 결과 없음(판정 보류): deviceId={}", request.getDeviceId());
        }

        boolean aiFallDetected = Boolean.TRUE.equals(request.getFallDetected());
        Double confidence = request.getFallConfidence();

        // 의식불명은 펌웨어가 낙상 이후 무움직임까지 확인한 최종 판정이라 다른 판정보다 우선한다.
        if (Boolean.TRUE.equals(request.getUnconscious())) {
            return new FallCheckResult(WorkerState.UNCONSCIOUS, confidence);
        }

        // AI 낙상 판정과 펌웨어 자세 판정 중 더 심각한 쪽을 최종 추락 상태로 쓴다.
        WorkerState aiState = aiFallDetected ? WorkerState.FALLEN : WorkerState.NORMAL;
        WorkerState postureState = mapPostureToFallState(request.getPosture());
        WorkerState finalFallState = aiState.ordinal() >= postureState.ordinal() ? aiState : postureState;

        return new FallCheckResult(finalFallState, confidence);
    }

    private WorkerState mapPostureToFallState(String posture) {
        if (posture == null) {
            return WorkerState.NORMAL;
        }
        return switch (posture.toUpperCase()) {
            case "STUMBLE" -> WorkerState.ACTION_REQUIRED; // 휘청거림 - 확인 필요
            case "COLLAPSE" -> WorkerState.FALLING;        // 쓰러짐 감지, AI 미확정
            default -> WorkerState.NORMAL;                 // STABLE 등
        };
    }

    private record FallCheckResult(WorkerState state, Double confidence) {
    }

    // ===================== 2. 건강 (독립) =====================

    private WorkerState checkHealth(SensorDataRequest request) {
        boolean healthAbnormal = Boolean.TRUE.equals(request.getHealthAbnormal());
        WorkerState flagState = healthAbnormal ? WorkerState.RECOMMEND : WorkerState.NORMAL;
        WorkerState levelState = parseHealthLevel(request.getLevel());
        WorkerState heatState = checkHeatRisk(request);

        WorkerState result = flagState;
        if (levelState.ordinal() > result.ordinal()) result = levelState;
        if (heatState.ordinal() > result.ordinal()) result = heatState;
        return result;
    }

    // 몸 신호(펌웨어의 heatRiskAbnormal: 활동량 대비 심박 지속 상승) + 외부 기상(체감온도 캐시) 조합.
    // 둘 다 위험 신호면 조치 필요, 하나만이면 권고, 둘 다 아니면 정상.
    private WorkerState checkHeatRisk(SensorDataRequest request) {
        boolean bodySignal = Boolean.TRUE.equals(request.getHeatRiskAbnormal());
        WorkerState externalLevel = weatherHeatRiskCache.currentLevel();
        boolean externalSignal = externalLevel.ordinal() >= WorkerState.RECOMMEND.ordinal();

        if (bodySignal && externalSignal) return WorkerState.ACTION_REQUIRED;
        if (bodySignal || externalSignal) return WorkerState.RECOMMEND;
        return WorkerState.NORMAL;
    }

    private WorkerState parseHealthLevel(String level) {
        if (level == null || level.isBlank()) {
            return WorkerState.NORMAL;
        }
        WorkerState parsed;
        try {
            parsed = WorkerState.valueOf(level.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("알 수 없는 level 값: {}", level);
            return WorkerState.NORMAL;
        }
        // level은 건강 판단 범위(NORMAL~ACTION_REQUIRED)만 신뢰. 추락 관련 값이 오면 무시.
        if (parsed == WorkerState.FALLING || parsed == WorkerState.FALLEN) {
            log.warn("level에 추락 관련 값이 들어와 무시함: {}", level);
            return WorkerState.NORMAL;
        }
        return parsed;
    }

    // ===================== 3. 위치 (헬멧 비콘 구역, 독립) =====================

    /**
     * deviceId로 헬멧을 찾아 통신 상태를 갱신하고, 배정된 작업자가 있으면 zoneId(구역 코드)로
     * Zone을 조회해 Worker.currentZone까지 갱신한다.
     * 등록 안 된 deviceId, 미배정 헬멧, 매핑 안 되는 zoneId는 전부 에러 없이 조용히 넘어간다 —
     * 위치 갱신은 부가 기능이라 이것 때문에 센서 데이터 저장/판단 자체가 실패하면 안 된다.
     *
     * @return 조회된 헬멧. 등록되지 않은 deviceId면 null. 위험 이벤트를 붙일 대상으로 재사용한다.
     */
    private Helmet updateWorkerLocation(SensorDataRequest request) {
        Helmet helmet = helmetRepository.findByDeviceId(request.getDeviceId()).orElse(null);
        if (helmet == null) {
            log.debug("등록되지 않은 deviceId라 위치 갱신 스킵: {}", request.getDeviceId());
            return null;
        }
        helmet.recordCommunication();

        Worker worker = helmet.getWorker();
        if (worker == null) {
            log.debug("작업자가 배정되지 않은 헬멧이라 위치 갱신 스킵: deviceId={}", request.getDeviceId());
            return helmet;
        }

        String zoneId = request.getZoneId();
        if (zoneId == null || zoneId.isBlank()) {
            return helmet; // 이번 전송엔 구역 정보가 없음 — 기존 값 유지, 건드리지 않음
        }

        Zone zone = zoneRepository.findByZoneCode(zoneId).orElse(null);
        if (zone == null) {
            log.warn("zone 테이블에 없는 zoneId 수신: {} (deviceId={})", zoneId, request.getDeviceId());
        }
        // nearestBeaconId/rssi는 펌웨어가 아직 원시 비콘 식별자를 안 보내고 있어서(구역 이름만 보냄)
        // 지금은 채우지 않는다 — 필요해지면 페이로드에 major/rssi를 추가하고 여기도 같이 채운다.
        worker.updateBeaconLocation(zone, null, null);
        return helmet;
    }

    // ===================== 4. 추락 위험 이벤트 (독립) =====================

    /**
     * 추락/휘청거림을 hazard_event 로 올려 관리자 대시보드 목록(/api/v1/hazard-events)에 노출한다.
     * 영상 감지 경로와 같은 테이블을 쓰고 source 로만 구분되므로, 목록/상세/조치 API 는 그대로 재사용된다.
     *
     * <p>펌웨어가 200ms(5Hz) 주기로 보내는 데다 posture 는 3초 윈도우 결과라, 휘청거리는 동안 들어오는 모든
     * 전송이 같은 판정을 실어 나른다. 그래서 매 수신마다 만들지 않고 <b>상태가 올라간 순간에만</b>
     * 만든다. 등급이 유지되거나 내려가는 동안은 이미 올라간 이벤트가 그 상황을 대표한다.
     * (NORMAL -> 휘청거림 -> 추락 이면 휘청거림 1건 + 추락 1건이 남는다)
     */
    private Long raiseFallHazardEvent(String deviceId, Helmet helmet,
                                      WorkerState previous, FallCheckResult fall, Long sensorDataId) {
        if (helmet == null) {
            // 등록되지 않은 deviceId. hazard_event.helmet_id 가 NOT NULL 이라 붙일 대상이 없다.
            // 이 경우 위험을 감지하고도 대시보드에 아무것도 안 뜨는데, 화면만 봐서는 판정이
            // 안 된 건지 헬멧이 미등록인 건지 구분할 수 없다. 그래서 debug 가 아니라 warn 이다.
            log.warn("헬멧 미등록이라 추락 이벤트를 만들지 못함 — helmet 테이블에 device_id 등록 필요: "
                    + "deviceId={}, state={}", deviceId, fall.state());
            return null;
        }
        if (!isEscalation(previous, fall.state())) {
            return null; // 같은 상황이 이어지는 중 — 이미 만들어진 이벤트가 대표한다
        }

        HazardType hazardType = toHazardType(fall.state());

        // 같은 헬멧의 미처리·처리중 추락 사고가 시간창 안에 있으면 새로 만들지 않고 그 사고를 갱신한다.
        // 휘청거림 → 추락처럼 같은 사고가 단계를 올리는 경우에는 위험 종류만 더 높은 쪽으로 바꾼다.
        Optional<HazardEvent> openIncident = hazardEventRepository
                .findFirstByHelmet_IdAndCategoryAndStatusInAndOccurredAtAfterOrderByOccurredAtDesc(
                        helmet.getId(), HazardCategory.FALL,
                        List.of(HazardStatus.UNHANDLED, HazardStatus.IN_PROGRESS),
                        LocalDateTime.now().minusSeconds(FALL_MERGE_WINDOW_SECONDS));
        if (openIncident.isPresent()) {
            HazardEvent existing = openIncident.get();
            HazardType before = existing.getHazardType();
            if (hazardRank(hazardType) > hazardRank(before)) {
                existing.escalate(hazardType, hazardSeverityPolicy.resolve(hazardType));
                log.info("추락 사고 단계 상향: id={}, {} -> {}", existing.getId(), before, hazardType);
            } else {
                log.info("추락 사고 중복 병합(알림 생략): id={}, state={}", existing.getId(), fall.state());
            }
            return existing.getId();
        }

        Worker worker = helmet.getWorker();

        HazardEvent saved = hazardEventRepository.save(HazardEvent.builder()
                .eventUid(UUID.randomUUID().toString().replace("-", "")) // CHAR(32)
                .helmet(helmet)
                .worker(worker)
                // 바로 위 updateWorkerLocation 이 이번 zoneId 로 갱신해둔 구역. 이후 작업자가 이동해도
                // 이 사건의 기록은 당시 구역을 유지해야 하므로 여기서 스냅샷으로 박아둔다.
                .zone(worker == null ? null : worker.getCurrentZone())
                .category(HazardCategory.FALL)
                .hazardType(hazardType)
                .severity(hazardSeverityPolicy.resolve(hazardType))
                .confidence(toConfidence(fall.confidence()))
                .occurredAt(LocalDateTime.now())
                .status(HazardStatus.UNHANDLED)
                .source(HazardSource.IMU_HEALTH)
                .sensorDataId(sensorDataId)
                .build());

        log.info("추락 위험 이벤트 저장: id={}, deviceId={}, {} -> {}, hazardType={}",
                saved.getId(), helmet.getDeviceId(), previous, fall.state(), hazardType);

        // 해당 작업자에게 알림 저장. 위 isEscalation 을 통과한 "상태가 올라간 순간"에만 도달하므로
        // 200ms 주기 수신마다 알림이 쌓이지 않는다. 작업자 미배정 헬멧이면 0건 반환(경고 로그).
        // hazard_event 를 FK 로 참조하므로 같은 트랜잭션에서 저장한다(분리하면 미커밋 이벤트를 못 본다).
        hazardNotificationService.notifyWorker(saved, worker);

        // 관리자 사고알림 기록에 추락을 남긴다. 작업자 미배정 헬멧이어도 관리자에게는 알린다.
        notificationService.notifyFallAlert(saved, worker);

        // 대시보드로 즉시 push. 빠뜨리면 새로고침 전까지 목록이 갱신되지 않는다.
        // 구독자 문제로 센서 수신 자체가 실패하면 안 되므로 예외는 여기서 삼킨다.
        try {
            hazardEventStreamService.publishHazard(saved);
        } catch (Exception e) {
            log.error("추락 이벤트 SSE push 실패 (저장은 완료): id={}, {}", saved.getId(), e.getMessage());
        }
        return saved.getId();
    }

    /**
     * 건강 이상을 hazard_event(카테고리 HEALTH)로 올리고 작업자·관리자에게 알린다.
     * 낙상과 같은 isEscalation 으로 "상태가 올라간 순간"에만 만들고, 같은 헬멧에 미처리·처리중 건강 사고가
     * 이미 있으면 새로 만들지 않는다. 발생 당시 심박·SpO₂·HRV·위험 단계를 사고에 함께 저장한다.
     * 헬멧이 등록되지 않았으면 사고를 만들 수 없어 건너뛰고, 작업자가 배정되지 않았으면 관리자에게만 알린다.
     */
    private Long raiseHealthHazardEvent(SensorDataRequest request, Helmet helmet,
                                        WorkerState previous, WorkerState current, Long sensorDataId) {
        if (helmet == null) {
            log.warn("헬멧 미등록이라 건강 이벤트를 만들지 못함: deviceId={}, state={}", request.getDeviceId(), current);
            return null;
        }
        if (!isEscalation(previous, current)) {
            return null; // 같은 건강 이상이 이어지는 중 — 이미 만들어진 사고가 대표한다
        }

        Optional<HazardEvent> open = hazardEventRepository
                .findFirstByHelmet_IdAndCategoryAndStatusInOrderByOccurredAtDesc(
                        helmet.getId(), HazardCategory.HEALTH,
                        List.of(HazardStatus.UNHANDLED, HazardStatus.IN_PROGRESS));
        if (open.isPresent()) {
            log.info("건강 사고 중복 생성 방지(미처리 사고 존재): id={}, state={}", open.get().getId(), current);
            return open.get().getId();
        }

        Worker worker = helmet.getWorker();
        HazardEvent saved = hazardEventRepository.save(HazardEvent.builder()
                .eventUid(UUID.randomUUID().toString().replace("-", "")) // CHAR(32)
                .helmet(helmet)
                .worker(worker)
                .zone(worker == null ? null : worker.getCurrentZone())
                .category(HazardCategory.HEALTH)
                .hazardType(HazardType.HEALTH_ABNORMAL)
                .severity(hazardSeverityPolicy.resolve(HazardType.HEALTH_ABNORMAL))
                .occurredAt(LocalDateTime.now())
                .status(HazardStatus.UNHANDLED)
                .source(HazardSource.IMU_HEALTH)
                .sensorDataId(sensorDataId)
                .heartRate(request.getHeartRate())
                .spo2(request.getSpo2())
                .hrv(request.getHrv())
                .healthState(current)
                // 목록·상세 화면에서 바로 보이도록 발생 당시 생체 값을 설명 문구에도 남긴다.
                .description(healthDescription(current, request))
                .build());

        log.info("건강 위험 이벤트 저장: id={}, deviceId={}, {} -> {}",
                saved.getId(), helmet.getDeviceId(), previous, current);

        // 작업자 미배정 헬멧이어도 관리자에게는 알린다 (추락 알림과 같은 기준)
        notificationService.notifyHealthAlert(saved, worker);

        try {
            hazardEventStreamService.publishHazard(saved);
        } catch (Exception e) {
            log.error("건강 이벤트 SSE push 실패 (저장은 완료): id={}, {}", saved.getId(), e.getMessage());
        }
        return saved.getId();
    }

    /** 예: "건강 이상 감지 (ACTION_REQUIRED) · 심박 130.0bpm · SpO2 88.0% · HRV 20.0" */
    private String healthDescription(WorkerState current, SensorDataRequest request) {
        return "건강 이상 감지 (" + current.name() + ")"
                + " · 심박 " + (request.getHeartRate() == null ? "미측정" : request.getHeartRate() + "bpm")
                + " · SpO2 " + (request.getSpo2() == null ? "미측정" : request.getSpo2() + "%")
                + " · HRV " + (request.getHrv() == null ? "미측정" : request.getHrv());
    }

    /**
     * 이전보다 위험 등급이 올라갔는지. 직전 기록이 없거나(첫 수신) 직전이 정상 범위였다면
     * 이번이 곧 진입이므로 true 다.
     */
    private boolean isEscalation(WorkerState previous, WorkerState current) {
        if (previous == null || !isAlertState(previous)) {
            return true;
        }
        return current.ordinal() > previous.ordinal();
    }

    /**
     * 추락 트랙의 WorkerState 를 위험 종류로 옮긴다.
     * ACTION_REQUIRED 는 posture 가 STUMBLE(휘청거림)일 때 올라오는 값이고,
     * FALLING/FALLEN 은 자세 붕괴 또는 AI 낙상 판정, UNCONSCIOUS 는 펌웨어 의식불명 판정이다.
     */
    // 추락 사고 안에서의 단계 순서. 숫자가 클수록 더 심각한 단계다.
    private int hazardRank(HazardType type) {
        return switch (type) {
            case STUMBLE -> 1;
            case FALL -> 2;
            case UNCONSCIOUS -> 3;
            default -> 0;
        };
    }

    private HazardType toHazardType(WorkerState fallState) {
        return switch (fallState) {
            case ACTION_REQUIRED -> HazardType.STUMBLE;
            case UNCONSCIOUS -> HazardType.UNCONSCIOUS;
            default -> HazardType.FALL;
        };
    }

    /** confidence 컬럼이 DECIMAL(5,4) 라 자릿수를 맞춘다. AI 호출이 실패했으면 null 이다. */
    private BigDecimal toConfidence(Double confidence) {
        return confidence == null
                ? null
                : BigDecimal.valueOf(confidence).setScale(4, RoundingMode.HALF_UP);
    }

    // ===================== 공통 =====================

    private boolean isAlertState(WorkerState state) {
        return state.ordinal() >= WorkerState.ACTION_REQUIRED.ordinal();
    }

    private boolean isValidSensorData(SensorDataRequest req) {
        if (req.getDeviceId() == null || req.getDeviceId().isBlank()) {
            return false;
        }
        if (req.getAx() == null || req.getAy() == null || req.getAz() == null
                || req.getGx() == null || req.getGy() == null || req.getGz() == null) {
            return false;
        }
        boolean accInRange = Math.abs(req.getAx()) <= ACC_LIMIT
                && Math.abs(req.getAy()) <= ACC_LIMIT
                && Math.abs(req.getAz()) <= ACC_LIMIT;
        boolean gyroInRange = Math.abs(req.getGx()) <= GYRO_LIMIT
                && Math.abs(req.getGy()) <= GYRO_LIMIT
                && Math.abs(req.getGz()) <= GYRO_LIMIT;
        return accInRange && gyroInRange;
    }

    private void triggerAlert(String deviceId, String domain, WorkerState state) {
        // TODO: 실시간 알림(웹소켓 등) 전달 방식이 정해지면 여기에 연동. 지금은 로그만 남김.
        log.warn("경고 상태 감지 : deviceId = {}, domain = {}, state = {}", deviceId, domain, state);
    }

}
