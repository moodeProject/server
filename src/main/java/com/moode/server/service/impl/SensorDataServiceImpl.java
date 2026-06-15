package com.moode.server.service.impl;

import com.moode.server.domain.SensorData;
import com.moode.server.domain.WorkerState;
import com.moode.server.dto.request.SensorDataRequest;
import com.moode.server.dto.response.SensorDataResponse;
import com.moode.server.infrastructure.client.AiServerClient;
import com.moode.server.infrastructure.dto.request.AiRequest;
import com.moode.server.infrastructure.dto.response.AiResponse;
import com.moode.server.repository.SensorDataRepository;
import com.moode.server.service.SensorDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SensorDataServiceImpl implements SensorDataService {

    private final SensorDataRepository sensorDataRepository;
    private final AiServerClient aiServerClient;

    private static final double FALL_THRESHOLD = 2.5;
    private static final double STILL_THRESHOLD = 0.3;

    @Override
    public SensorDataResponse processSensorData(SensorDataRequest request) {

        // 1. 데이터 유효성 검사
        if (!isValidSensorData(request)) {
            throw new IllegalArgumentException("센서 데이터가 유효 범위를 벗어났습니다.");
        }

        // 2. 규칙 기반 : 1차 상태 판단
        WorkerState preliminaryState = evaluatePreliminaryState(request);
        log.info("1차 상태 판단: deviceId={}, state={}", request.getDeviceId(), preliminaryState);

        // 3. AI 서버 호출해서 최종 판단
        WorkerState finalState = getFinalStateFromAi(request, preliminaryState);
        log.info("최종 상태: deviceId={}, state={}", request.getDeviceId(), finalState);

        // 4. DB 저장
        SensorData saved = sensorDataRepository.save(
                SensorData.builder()
                        .deviceId(request.getDeviceId())
                        .ax(request.getAx()).ay(request.getAy()).az(request.getAz())
                        .gx(request.getGx()).gy(request.getGy()).gz(request.getGz())
                        .state(finalState)
                        .build()
        );

        // 5. 추락 확정 -> 알림
        boolean alertTriggered = false;
        if (finalState == WorkerState.FALLEN) {
            triggerAlert(request.getDeviceId());
            alertTriggered = true;
        }

        return SensorDataResponse.builder()
                .id(saved.getId())
                .deviceId(saved.getDeviceId())
                .state(finalState)
                .alertTriggered(alertTriggered)
                .recordedAt(saved.getRecordedAt())
                .build();
    }

    private boolean isValidSensorData(SensorDataRequest req) {
        return Math.abs(req.getAx()) <= 50
                && Math.abs(req.getAy()) <= 50
                && Math.abs(req.getAz()) <= 50;
    }

    private WorkerState evaluatePreliminaryState(SensorDataRequest req) {
        double totalAccel = Math.sqrt(
                req.getAx() * req.getAx() +
                        req.getAy() * req.getAy() +
                        req.getAz() * req.getAz()
        );

        if (totalAccel > FALL_THRESHOLD) return WorkerState.FALLING;
        if (totalAccel < STILL_THRESHOLD) return WorkerState.FALLEN;
        return  WorkerState.NORMAL;
    }

    private WorkerState getFinalStateFromAi(SensorDataRequest req, WorkerState preliminary) {
        //Todo: ai 서버 연결


        try {
            AiRequest aiRequest = AiRequest.builder()
                    .ax(req.getAx()).ay(req.getAy()).az(req.getAz())
                    .gx(req.getGx()).gy(req.getGy()).gz(req.getGz())
                    .build();
            AiResponse aiResponse = aiServerClient.predict(aiRequest);

            if (aiResponse.isFallDetected()) {
                return WorkerState.FALLEN;
            }
            return preliminary;

        } catch (Exception e) {
            log.error("AI 서버 호출 실패, 1차 판단 결과 사용: {}", e.getMessage());
            return preliminary;
        }
        // 임시: AI 없이 1차 판단 결과만 사용
        //log.info("AI 연동 전 임시 모드 - 1차 판단 결과 사용: {}", preliminary);
        //return preliminary;
    }

    private void triggerAlert(String deviceId) {
        log.warn("추락 감지 : deviceId = {}", deviceId);
    }

}
