package com.moode.server.controller;

import com.moode.server.config.InternalApiKeyInterceptor;
import com.moode.server.domain.HazardCategory;
import com.moode.server.domain.HazardEvent;
import com.moode.server.domain.HazardSeverity;
import com.moode.server.domain.HazardType;
import com.moode.server.domain.Helmet;
import com.moode.server.infrastructure.client.AiServerClient;
import com.moode.server.repository.HazardClipRepository;
import com.moode.server.repository.HazardEventActionRepository;
import com.moode.server.repository.HazardEventRepository;
import com.moode.server.repository.HelmetRepository;
import com.moode.server.repository.InboundMessageRepository;
import com.moode.server.repository.SensorDataRepository;
import com.moode.server.repository.WorkerRepository;
import com.moode.server.repository.ZoneRepository;
import com.moode.server.service.impl.VisionWarningRegistry;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 영상 단독 감지는 헬멧 경고로만 끝나고, 헬멧 센서 이상이 겹칠 때만 대시보드 위험 이벤트가 된다.
 *
 * <p>AI 서버(/internal/v1/hazard-events)와 헬멧(/api/sensor-data)을 실제 순서대로 호출해 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VisionWarningEscalationTest {

    private static final String INGEST_PATH = "/internal/v1/hazard-events";
    private static final String SENSOR_PATH = "/api/sensor-data";
    private static final String VALID_KEY = "test-internal-key";
    private static final String EVENT_UID = "dadeb552483643e4b32052f12ffbef3e";

    @Autowired private MockMvc mockMvc;
    @Autowired private HazardEventActionRepository hazardEventActionRepository;
    @Autowired private HazardClipRepository hazardClipRepository;
    @Autowired private HazardEventRepository hazardEventRepository;
    @Autowired private InboundMessageRepository inboundMessageRepository;
    @Autowired private SensorDataRepository sensorDataRepository;
    @Autowired private HelmetRepository helmetRepository;
    @Autowired private WorkerRepository workerRepository;
    @Autowired private ZoneRepository zoneRepository;
    @Autowired private TestFixtures fixtures;
    @Autowired private VisionWarningRegistry visionWarningRegistry;

    @MockitoBean private AiServerClient aiServerClient;

    private Helmet helmet;

    @BeforeEach
    void setUp() {
        hazardEventActionRepository.deleteAll();
        hazardClipRepository.deleteAll();
        hazardEventRepository.deleteAll();
        inboundMessageRepository.deleteAll();
        sensorDataRepository.deleteAll();
        helmetRepository.deleteAll();
        workerRepository.deleteAll();
        zoneRepository.deleteAll();
        helmet = fixtures.defaultHelmet(); // H-001 / ESP32-001
        visionWarningRegistry.clear();
        given(aiServerClient.predict(any())).willReturn(null);
    }

    private void sendVision(String idempotencyKey) throws Exception {
        mockMvc.perform(post(INGEST_PATH)
                        .header(InternalApiKeyInterceptor.HEADER_NAME, VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "idempotencyKey": "%s",
                                  "eventId": "%s",
                                  "eventType": "HAZARD_CONFIRMED",
                                  "helmetId": "H-001",
                                  "hazardTypes": ["forklift"],
                                  "detections": [
                                    { "type": "forklift", "confidence": 0.8483, "bbox": [214.9, 38.6, 806.2, 440.3] }
                                  ],
                                  "frameWidth": 640,
                                  "frameHeight": 480,
                                  "occurredAt": "2026-10-26T10:40:31.000000+00:00",
                                  "streamOffsetSeconds": 2.04
                                }
                                """.formatted(idempotencyKey, EVENT_UID)))
                .andExpect(status().isCreated());
    }

    private void sendClip() throws Exception {
        mockMvc.perform(post(INGEST_PATH)
                        .header(InternalApiKeyInterceptor.HEADER_NAME, VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "idempotencyKey": "c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1",
                                  "eventId": "%s",
                                  "eventType": "CLIP_READY",
                                  "helmetId": "H-001",
                                  "clip": {
                                    "storage": "S3",
                                    "bucket": "safe-helmet-clips",
                                    "key": "clips/2026/10/26/H-001_104031.mp4",
                                    "durationSeconds": 20.0,
                                    "frameCount": 200
                                  }
                                }
                                """.formatted(EVENT_UID)))
                // 경고 단계라 붙일 이벤트가 없어도 404(dead-letter)가 아니라 2xx 여야 한다
                .andExpect(status().isCreated());
    }

    private ResultActions sendSensor(String posture) throws Exception {
        return mockMvc.perform(post(SENSOR_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deviceId": "%s",
                                  "ax": 0.5, "ay": 0.3, "az": 9.6,
                                  "gx": 12.0, "gy": -8.0, "gz": 3.0,
                                  "heartRate": 88.0, "spo2": 97.0,
                                  "posture": "%s",
                                  "healthAbnormal": false,
                                  "level": "NORMAL"
                                }
                                """.formatted(helmet.getDeviceId(), posture)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("영상 단독 감지는 대시보드 이벤트를 만들지 않고 헬멧 응답에 경고만 싣는다")
    void visionOnlyIsHelmetWarning() throws Exception {
        sendVision("0bae5301b4f64746a2b9bb1db5de1e49");

        assertThat(hazardEventRepository.findAll()).isEmpty();

        sendSensor("STABLE")
                .andExpect(jsonPath("$.data.hazardWarning").value("forklift"))
                .andExpect(jsonPath("$.data.hazardWarningSeq").isNumber());
        assertThat(hazardEventRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("영상 경고 뒤 센서 이상이 오면 위험 이벤트로 올리고, 경고 단계에서 온 클립도 붙인다")
    void sensorAbnormalAfterVisionEscalates() throws Exception {
        sendVision("0bae5301b4f64746a2b9bb1db5de1e49");
        sendClip();

        sendSensor("STUMBLE");
        sendSensor("STUMBLE"); // 같은 경고로 두 번 올리지 않는다

        List<HazardEvent> external = hazardEventRepository.findAll().stream()
                .filter(e -> e.getCategory() == HazardCategory.EXTERNAL)
                .toList();
        assertThat(external).hasSize(1);
        assertThat(external.get(0).getHazardType()).isEqualTo(HazardType.FORKLIFT);
        assertThat(external.get(0).getSeverity()).isEqualTo(HazardSeverity.DANGER);
        assertThat(external.get(0).getEventUid()).isEqualTo(EVENT_UID);
        assertThat(hazardClipRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("센서 이상이 먼저 있었으면 영상 감지 즉시 위험 이벤트가 된다")
    void visionAfterSensorAbnormalIsDanger() throws Exception {
        sendSensor("STUMBLE");

        sendVision("0bae5301b4f64746a2b9bb1db5de1e49");

        List<HazardEvent> external = hazardEventRepository.findAll().stream()
                .filter(e -> e.getCategory() == HazardCategory.EXTERNAL)
                .toList();
        assertThat(external).hasSize(1);
        assertThat(external.get(0).getSeverity()).isEqualTo(HazardSeverity.DANGER);
    }

    @Test
    @DisplayName("센서가 정상이면 영상 경고가 있어도 위험 이벤트가 생기지 않는다")
    void normalSensorDoesNotEscalate() throws Exception {
        sendVision("0bae5301b4f64746a2b9bb1db5de1e49");

        sendSensor("STABLE");
        sendSensor("STABLE");

        assertThat(hazardEventRepository.findAll()).isEmpty();
    }
}
