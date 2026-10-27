package com.moode.server.controller;

import com.moode.server.config.InternalApiKeyInterceptor;
import com.moode.server.domain.Helmet;
import com.moode.server.infrastructure.client.AiServerClient;
import com.moode.server.repository.*;
import com.moode.server.service.impl.HelmetVisionWarningRegistry;
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
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 영상(헬멧 카메라) 위험 확정 → 헬멧 센서 전송 응답에 경고가 실려 스피커가 울릴 수 있는지.
 * 대시보드 사고 생성은 기존 그대로여야 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HelmetVisionWarningTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private HazardEventActionRepository hazardEventActionRepository;
    @Autowired private HazardClipRepository hazardClipRepository;
    @Autowired private HazardEventRepository hazardEventRepository;
    @Autowired private InboundMessageRepository inboundMessageRepository;
    @Autowired private SensorDataRepository sensorDataRepository;
    @Autowired private HelmetRepository helmetRepository;
    @Autowired private WorkerRepository workerRepository;
    @Autowired private ZoneRepository zoneRepository;
    @Autowired private TestFixtures fixtures;
    @Autowired private HelmetVisionWarningRegistry helmetVisionWarningRegistry;

    @MockitoBean private AiServerClient aiServerClient;

    private Helmet helmet;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        hazardEventActionRepository.deleteAll();
        hazardClipRepository.deleteAll();
        hazardEventRepository.deleteAll();
        inboundMessageRepository.deleteAll();
        sensorDataRepository.deleteAll();
        helmetRepository.deleteAll();
        workerRepository.deleteAll();
        zoneRepository.deleteAll();
        helmet = fixtures.defaultHelmet(); // H-001 / ESP32-001
        helmetVisionWarningRegistry.clear();
        given(aiServerClient.predict(any())).willReturn(null);
    }

    private void sendVision(String idempotencyKey) throws Exception {
        mockMvc.perform(post("/internal/v1/hazard-events")
                        .header(InternalApiKeyInterceptor.HEADER_NAME, "test-internal-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "idempotencyKey": "%s",
                                  "eventId": "dadeb552483643e4b32052f12ffbef3e",
                                  "eventType": "HAZARD_CONFIRMED",
                                  "helmetId": "H-001",
                                  "hazardTypes": ["forklift"],
                                  "detections": [
                                    { "type": "forklift", "confidence": 0.84, "bbox": [214.9, 38.6, 806.2, 440.3] }
                                  ],
                                  "frameWidth": 640,
                                  "frameHeight": 480,
                                  "occurredAt": "2026-10-28T01:40:31.000000+00:00"
                                }
                                """.formatted(idempotencyKey)))
                .andExpect(status().isCreated());
    }

    private ResultActions sendSensor() throws Exception {
        return mockMvc.perform(post("/api/sensor-data")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deviceId": "%s",
                                  "ax": 0.5, "ay": 0.3, "az": 9.6,
                                  "gx": 12.0, "gy": -8.0, "gz": 3.0,
                                  "heartRate": 88.0, "spo2": 97.0,
                                  "posture": "STABLE",
                                  "healthAbnormal": false,
                                  "level": "NORMAL"
                                }
                                """.formatted(helmet.getDeviceId())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("영상 위험이 확정되면 그 헬멧의 다음 센서 응답에 경고와 번호가 실린다")
    void warningReachesHelmet() throws Exception {
        sendSensor()
                .andExpect(jsonPath("$.data.hazardWarning").doesNotExist())
                .andExpect(jsonPath("$.data.hazardWarningSeq").doesNotExist());

        sendVision("0bae5301b4f64746a2b9bb1db5de1e49");

        sendSensor()
                .andExpect(jsonPath("$.data.hazardWarning").value("forklift"))
                .andExpect(jsonPath("$.data.hazardWarningSeq").isNumber());

        // 대시보드 사고 생성은 기존 그대로
        assertThat(hazardEventRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("새 위험이 확정되면 번호가 바뀌어 헬멧이 다시 안내한다")
    void newWarningGetsNewSeq() throws Exception {
        sendVision("0bae5301b4f64746a2b9bb1db5de1e49");
        long first = helmetVisionWarningRegistry.activeFor(helmet.getDeviceId()).orElseThrow().seq();

        sendVision("1bae5301b4f64746a2b9bb1db5de1e49");
        long second = helmetVisionWarningRegistry.activeFor(helmet.getDeviceId()).orElseThrow().seq();

        assertThat(second).isGreaterThan(first);
    }
}
