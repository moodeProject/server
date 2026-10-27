package com.moode.server.controller;

import com.moode.server.domain.Helmet;
import com.moode.server.infrastructure.client.AiServerClient;
import com.moode.server.repository.HazardClipRepository;
import com.moode.server.repository.HazardEventActionRepository;
import com.moode.server.repository.HazardEventRepository;
import com.moode.server.repository.HelmetRepository;
import com.moode.server.repository.SensorDataRepository;
import com.moode.server.repository.WorkerRepository;
import com.moode.server.repository.ZoneRepository;
import com.moode.server.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 헬멧이 보낸 값이 실제로 관리자 대시보드 목록까지 도달하는지 종단 확인.
 *
 * <p>POST /api/sensor-data (펌웨어가 보내는 것과 같은 모양)
 * -> 추락 판정 -> hazard_event 저장 -> GET /api/v1/hazard-events 에 노출
 *
 * <p>AI 서버는 테스트 환경에 없으므로 호출이 실패한 상황(null 응답)으로 둔다.
 * 그러면 추락 판정은 펌웨어 posture 값만으로 결정되어, 검증하려는 경로가 그대로 드러난다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SensorFallHazardEventIntegrationTest {

    private static final String SENSOR_PATH = "/api/sensor-data";
    private static final String HAZARD_PATH = "/api/v1/hazard-events";

    @Autowired private MockMvc mockMvc;
    @Autowired private HazardEventRepository hazardEventRepository;
    @Autowired private HazardEventActionRepository hazardEventActionRepository;
    @Autowired private HazardClipRepository hazardClipRepository;
    @Autowired private SensorDataRepository sensorDataRepository;
    @Autowired private HelmetRepository helmetRepository;
    @Autowired private WorkerRepository workerRepository;
    @Autowired private ZoneRepository zoneRepository;
    @Autowired private TestFixtures fixtures;

    @MockitoBean private AiServerClient aiServerClient;

    private Helmet helmet;

    @BeforeEach
    void setUp() {
        hazardEventActionRepository.deleteAll();
        hazardClipRepository.deleteAll();
        hazardEventRepository.deleteAll();
        sensorDataRepository.deleteAll();
        helmetRepository.deleteAll();
        workerRepository.deleteAll();
        zoneRepository.deleteAll();
        helmet = fixtures.defaultHelmet(); // H-001 / ESP32-001 / 박민수 / A-3F

        // AI 서버 미가동 상태. checkFall() 이 예외를 삼키고 posture 만으로 판정한다.
        given(aiServerClient.predict(any())).willReturn(null);
    }

    /** 펌웨어 sendSensorData() 가 만드는 것과 같은 payload. zoneId 는 비콘 구역 코드다. */
    private String payload(String posture) {
        return """
                {
                  "deviceId": "%s",
                  "ax": 0.5, "ay": 0.3, "az": 9.6,
                  "gx": 12.0, "gy": -8.0, "gz": 3.0,
                  "heartRate": 88.0, "spo2": 97.0,
                  "posture": "%s",
                  "healthAbnormal": false,
                  "level": "NORMAL",
                  "zoneId": "A-3F"
                }
                """.formatted(helmet.getDeviceId(), posture);
    }

    private void sendSensorData(String posture) throws Exception {
        mockMvc.perform(post(SENSOR_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(posture)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("휘청거림이 감지되면 위험 이벤트가 만들어지고 목록에 구역까지 실려 나온다")
    void stumbleBecomesHazardEvent() throws Exception {
        sendSensorData("STUMBLE");

        mockMvc.perform(get(HAZARD_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].category").value("FALL"))
                .andExpect(jsonPath("$.content[0].hazardType").value("stumble"))
                .andExpect(jsonPath("$.content[0].severity").value("WARNING"))
                .andExpect(jsonPath("$.content[0].helmetNo").value("H-001"))
                .andExpect(jsonPath("$.content[0].worker.name").value("박민수"))
                // 비콘 zoneId -> zone 테이블 조회 -> 이벤트에 스냅샷. 프론트가 요청한 code/name 매핑.
                .andExpect(jsonPath("$.content[0].zone.code").value("A-3F"))
                .andExpect(jsonPath("$.content[0].zone.name").value("A구역 3층"))
                .andExpect(jsonPath("$.summary.fall").value(1));
    }

    @Test
    @DisplayName("같은 상태가 이어지는 동안은 이벤트를 새로 만들지 않는다")
    void sameStateDoesNotPileUp() throws Exception {
        // 펌웨어는 50Hz 로 보내므로 휘청거리는 동안 같은 판정이 계속 도착한다.
        sendSensorData("STUMBLE");
        sendSensorData("STUMBLE");
        sendSensorData("STUMBLE");

        assertThat(hazardEventRepository.count()).isEqualTo(1);
        // 센서 원본 로그는 매 건 그대로 쌓인다. 억제되는 건 위험 이벤트뿐이다.
        assertThat(sensorDataRepository.count()).isEqualTo(3);
    }

    @Test
    @DisplayName("휘청거림 뒤에 실제로 쓰러지면 같은 사고가 추락 단계로 올라간다")
    void escalationUpgradesSameEvent() throws Exception {
        sendSensorData("STUMBLE");
        sendSensorData("STUMBLE");
        sendSensorData("COLLAPSE");

        // 60초 안의 같은 헬멧 사고라 하나로 묶이고, 위험 종류만 추락으로 올라간다.
        assertThat(hazardEventRepository.count()).isEqualTo(1);

        mockMvc.perform(get(HAZARD_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].hazardType").value("fall"))
                .andExpect(jsonPath("$.content[0].severity").value("DANGER"))
                .andExpect(jsonPath("$.summary.fall").value(1));
    }

    /** 펌웨어가 낙상 감지 직후, 그리고 의식불명 확정 시 보내는 payload. */
    private void sendFall(boolean unconscious) throws Exception {
        mockMvc.perform(post(SENSOR_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deviceId": "%s",
                                  "ax": 0.5, "ay": 0.3, "az": 9.6,
                                  "gx": 12.0, "gy": -8.0, "gz": 3.0,
                                  "posture": "STABLE",
                                  "zoneId": "A-3F",
                                  "fallDetected": true,
                                  "fallConfidence": 0.92,
                                  "unconscious": %s
                                }
                                """.formatted(helmet.getDeviceId(), unconscious)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("낙상 뒤 의식불명이 확정되면 같은 사고가 의식불명 단계로 올라간다")
    void unconsciousAfterFallUpgradesSameEvent() throws Exception {
        sendFall(false);  // 낙상 감지 즉시 전송
        sendFall(false);  // 같은 낙상 상태가 이어지는 정기 전송
        sendFall(true);   // 무움직임 확인 후 의식불명 확정

        // 같은 사고로 묶이고, 의식불명은 추락보다 한 단계 위라 위험 종류가 올라간다.
        assertThat(hazardEventRepository.count()).isEqualTo(1);

        mockMvc.perform(get(HAZARD_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].hazardType").value("unconscious"))
                .andExpect(jsonPath("$.content[0].severity").value("DANGER"))
                .andExpect(jsonPath("$.content[0].zone.code").value("A-3F"))
                .andExpect(jsonPath("$.summary.fall").value(1));
    }

    @Test
    @DisplayName("의식불명 상태가 이어지는 동안은 이벤트를 새로 만들지 않는다")
    void unconsciousDoesNotPileUp() throws Exception {
        sendFall(true);
        sendFall(true);

        assertThat(hazardEventRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("자세가 정상이면 위험 이벤트를 만들지 않는다")
    void stableCreatesNothing() throws Exception {
        sendSensorData("STABLE");

        assertThat(hazardEventRepository.count()).isZero();
        assertThat(sensorDataRepository.count()).isEqualTo(1);
    }
}
