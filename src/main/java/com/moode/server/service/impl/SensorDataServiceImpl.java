package com.moode.server.service.impl;

import com.moode.server.domain.Helmet;
import com.moode.server.domain.SensorData;
import com.moode.server.domain.Worker;
import com.moode.server.domain.WorkerState;
import com.moode.server.domain.Zone;
import com.moode.server.dto.request.SensorDataRequest;
import com.moode.server.dto.response.SensorDataResponse;
import com.moode.server.infrastructure.client.AiServerClient;
import com.moode.server.infrastructure.dto.request.AiRequest;
import com.moode.server.infrastructure.dto.response.AiResponse;
import com.moode.server.repository.HelmetRepository;
import com.moode.server.repository.SensorDataRepository;
import com.moode.server.repository.ZoneRepository;
import com.moode.server.service.SensorDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SensorDataServiceImpl implements SensorDataService {

    private final SensorDataRepository sensorDataRepository;
    private final AiServerClient aiServerClient;
    private final WeatherHeatRiskCache weatherHeatRiskCache;
    private final HelmetRepository helmetRepository;
    private final ZoneRepository zoneRepository;

    private static final double ACC_LIMIT = 50.0;    // 가속도 허용 범위
    private static final double GYRO_LIMIT = 2000.0; // 각속도 허용 범위

    @Override
    @Transactional
    public SensorDataResponse processSensorData(SensorDataRequest request) {

        // 1. 데이터 유효성 검사 (원본 값 자체가 이상하면 여기서 막음)
        if (!isValidSensorData(request)) {
            throw new IllegalArgumentException("센서 데이터가 유효 범위를 벗어났습니다.");
        }

        // 2. 독립 판단 1: 추락 (MPU6050 가속도/자이로 + AI + posture)
        FallCheckResult fallResult = checkFall(request);

        // 3. 독립 판단 2: 건강 (MAX30102 heartRate/spo2 + healthAbnormal + level + 외부 기상)
        WorkerState healthState = checkHealth(request);

        log.info("판단 결과: deviceId={}, fall={}({}), health={}, hrv={}, fatigue={}, heatRisk={}",
                request.getDeviceId(), fallResult.state(), fallResult.confidence(), healthState,
                request.getHrv(), request.getFatigueAbnormal(), request.getHeatRiskAbnormal());

        // 4. DB 저장
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

        // 5. 도메인별로 각자 알림 여부 판단 + 각자 알림 발생 (섞지 않음)
        boolean fallAlert = isAlertState(fallResult.state());
        boolean healthAlert = isAlertState(healthState);

        if (fallAlert) triggerAlert(request.getDeviceId(), "FALL", fallResult.state());
        if (healthAlert) triggerAlert(request.getDeviceId(), "HEALTH", healthState);

        // 6. 헬멧/작업자 위치(비콘 구역) 갱신 — 등록 안 된 deviceId거나 미배정 헬멧이면 조용히 스킵
        updateWorkerLocation(request);

        return SensorDataResponse.builder()
                .id(saved.getId())
                .deviceId(saved.getDeviceId())
                .fallState(fallResult.state())
                .healthState(healthState)
                .fallConfidence(fallResult.confidence())
                .alertTriggered(fallAlert || healthAlert)
                .zoneId(saved.getZoneId())
                .recordedAt(saved.getRecordedAt())
                .build();
    }

    // ===================== 1. 추락 (독립) =====================

    private FallCheckResult checkFall(SensorDataRequest request) {
        AiResponse aiResponse = callAiServer(request);

        if (aiResponse != null && aiResponse.isBuffered()) {
            // AI가 아직 최근 샘플을 충분히 못 모아서 진짜 판단을 안 한 상태.
            // fallDetected=false로 오지만 "정상 확인됨"이 아니라 "판단 보류"라는 뜻이므로 구분해서 로그만 남김.
            log.info("AI 버퍼링 중이라 아직 판단 안 함: deviceId={}, bufferSize={}",
                    request.getDeviceId(), aiResponse.getBufferSize());
        }

        boolean aiFallDetected = aiResponse != null && aiResponse.isFallDetected();
        Double confidence = aiResponse != null ? aiResponse.getConfidence() : null;

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

    private AiResponse callAiServer(SensorDataRequest req) {
        try {
            AiRequest aiRequest = AiRequest.builder()
                    .deviceId(req.getDeviceId())
                    .ax(req.getAx()).ay(req.getAy()).az(req.getAz())
                    .gx(req.getGx()).gy(req.getGy()).gz(req.getGz())
                    .heartRate(req.getHeartRate()).spo2(req.getSpo2())
                    .build();
            return aiServerClient.predict(aiRequest);
        } catch (Exception e) {
            log.error("AI 서버 호출 실패 : {}", e.getMessage());
            return null;
        }
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
     */
    private void updateWorkerLocation(SensorDataRequest request) {
        Helmet helmet = helmetRepository.findByDeviceId(request.getDeviceId()).orElse(null);
        if (helmet == null) {
            log.debug("등록되지 않은 deviceId라 위치 갱신 스킵: {}", request.getDeviceId());
            return;
        }
        helmet.recordCommunication();

        Worker worker = helmet.getWorker();
        if (worker == null) {
            log.debug("작업자가 배정되지 않은 헬멧이라 위치 갱신 스킵: deviceId={}", request.getDeviceId());
            return;
        }

        String zoneId = request.getZoneId();
        if (zoneId == null || zoneId.isBlank()) {
            return; // 이번 전송엔 구역 정보가 없음 — 기존 값 유지, 건드리지 않음
        }

        Zone zone = zoneRepository.findByZoneCode(zoneId).orElse(null);
        if (zone == null) {
            log.warn("zone 테이블에 없는 zoneId 수신: {} (deviceId={})", zoneId, request.getDeviceId());
        }
        // nearestBeaconId/rssi는 펌웨어가 아직 원시 비콘 식별자를 안 보내고 있어서(구역 이름만 보냄)
        // 지금은 채우지 않는다 — 필요해지면 페이로드에 major/rssi를 추가하고 여기도 같이 채운다.
        worker.updateBeaconLocation(zone, null, null);
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
