package com.moode.server.controller;

import com.moode.server.domain.*;
import com.moode.server.infrastructure.client.AiServerClient;
import com.moode.server.repository.*;
import com.moode.server.security.JwtTokenProvider;
import com.moode.server.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 프론트 연동 중 나온 건강 이상·추락 이력 요청 6건을 실제 요청 순서대로 확인한다.
 * 헬멧 POST /api/sensor-data -> 사고 생성·알림 -> 관리자 상세/조치/이력 조회.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HealthIncidentFollowupTest {

    private static final String SENSOR_PATH = "/api/sensor-data";
    private static final String HAZARD_PATH = "/api/v1/hazard-events";

    @Autowired private MockMvc mockMvc;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private HazardEventActionRepository hazardEventActionRepository;
    @Autowired private HazardClipRepository hazardClipRepository;
    @Autowired private HazardEventRepository hazardEventRepository;
    @Autowired private SensorDataRepository sensorDataRepository;
    @Autowired private HelmetRepository helmetRepository;
    @Autowired private WorkerRepository workerRepository;
    @Autowired private ZoneRepository zoneRepository;
    @Autowired private AdminRepository adminRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private TestFixtures fixtures;

    @MockitoBean private AiServerClient aiServerClient;

    private Helmet helmet;
    private String adminToken;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        hazardEventActionRepository.deleteAll();
        hazardClipRepository.deleteAll();
        hazardEventRepository.deleteAll();
        sensorDataRepository.deleteAll();
        helmetRepository.deleteAll();
        workerRepository.deleteAll();
        zoneRepository.deleteAll();
        adminRepository.deleteAll();
        helmet = fixtures.defaultHelmet(); // H-001 / ESP32-001 / 박민수 / A-3F

        Admin admin = adminRepository.save(Admin.builder()
                .loginId("admin-health")
                .passwordHash(passwordEncoder.encode("Admin!2024"))
                .name("관리자")
                .email("admin-health@example.com")
                .status(AdminStatus.APPROVED)
                .build());
        adminToken = jwtTokenProvider.generateToken(admin.getId(), UserType.ADMIN, admin.getLoginId(), admin.getName());

        given(aiServerClient.predict(any())).willReturn(null);
    }

    /** 펌웨어 sendSensorData() 와 같은 모양. level 로 건강 단계를, posture 로 자세를 준다. */
    private void sendSensor(String deviceId, String posture, String level, double heartRate, double spo2, double hrv)
            throws Exception {
        mockMvc.perform(post(SENSOR_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deviceId": "%s",
                                  "ax": 0.5, "ay": 0.3, "az": 9.6,
                                  "gx": 12.0, "gy": -8.0, "gz": 3.0,
                                  "heartRate": %s, "spo2": %s, "hrv": %s,
                                  "posture": "%s",
                                  "healthAbnormal": %s,
                                  "level": "%s"
                                }
                                """.formatted(deviceId, heartRate, spo2, hrv, posture,
                                !"NORMAL".equals(level), level)))
                .andExpect(status().isOk());
    }

    private HazardEvent onlyHealthEvent() {
        List<HazardEvent> health = hazardEventRepository.findAll().stream()
                .filter(e -> e.getCategory() == HazardCategory.HEALTH)
                .toList();
        assertThat(health).hasSize(1);
        return health.get(0);
    }

    @Test
    @DisplayName("1·2) 건강 이상 사고 상세에 발생 당시 생체 값이 실리고, 조치 완료하면 조치자·시각·메모가 남는다")
    void healthIncidentDetailAndResolve() throws Exception {
        sendSensor(helmet.getDeviceId(), "STABLE", "ACTION_REQUIRED", 131.0, 92.0, 18.5);
        HazardEvent event = onlyHealthEvent();

        mockMvc.perform(get(HAZARD_PATH + "/" + event.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("HEALTH"))
                .andExpect(jsonPath("$.vitals.heartRate").value(131.0))
                .andExpect(jsonPath("$.vitals.spo2").value(92.0))
                .andExpect(jsonPath("$.vitals.hrv").value(18.5))
                .andExpect(jsonPath("$.vitals.healthState").value("ACTION_REQUIRED"))
                .andExpect(jsonPath("$.vitals.sensorDataId").isNumber())
                .andExpect(jsonPath("$.actions.length()").value(0));

        mockMvc.perform(post(HAZARD_PATH + "/" + event.getId() + "/actions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "actionType": "RESOLVE", "adminId": 7, "memo": "휴식 후 심박 정상 확인" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        mockMvc.perform(get(HAZARD_PATH + "/" + event.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.handledBy").value(7))
                .andExpect(jsonPath("$.handledAt").isNotEmpty())
                .andExpect(jsonPath("$.actions.length()").value(1))
                .andExpect(jsonPath("$.actions[0].actionType").value("RESOLVE"))
                .andExpect(jsonPath("$.actions[0].performedBy").value(7))
                .andExpect(jsonPath("$.actions[0].memo").value("휴식 후 심박 정상 확인"));
    }

    @Test
    @DisplayName("3·5) ACTION_REQUIRED 로 올라가면 관리자·작업자 알림이 저장되고, 작업자 기준 조회·읽음 처리가 된다")
    void healthEscalationNotifiesManagerAndWorker() throws Exception {
        sendSensor(helmet.getDeviceId(), "STABLE", "NORMAL", 80.0, 98.0, 40.0);
        sendSensor(helmet.getDeviceId(), "STABLE", "RECOMMEND", 110.0, 96.0, 30.0);
        sendSensor(helmet.getDeviceId(), "STABLE", "ACTION_REQUIRED", 131.0, 92.0, 18.5);
        sendSensor(helmet.getDeviceId(), "STABLE", "ACTION_REQUIRED", 132.0, 92.0, 18.0); // 유지 중엔 추가 알림 없음

        HazardEvent event = onlyHealthEvent();
        List<Notification> notifications = notificationRepository.findAll().stream()
                .filter(n -> n.getEvent() != null && event.getId().equals(n.getEvent().getId()))
                .toList();
        assertThat(notifications).extracting(Notification::getRecipientType)
                .containsExactlyInAnyOrder(NotificationRecipientType.MANAGER, NotificationRecipientType.WORKER);

        Long workerId = helmet.getWorker().getId();
        mockMvc.perform(get("/api/notifications")
                        .param("recipientType", "WORKER")
                        .param("recipientId", String.valueOf(workerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].hazardEventId").value(event.getId()))
                .andExpect(jsonPath("$.data.unreadCount").value(1));

        mockMvc.perform(patch("/api/notifications/read-all")
                        .param("recipientType", "WORKER")
                        .param("recipientId", String.valueOf(workerId)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/notifications")
                        .param("recipientType", "WORKER")
                        .param("recipientId", String.valueOf(workerId)))
                .andExpect(jsonPath("$.data.unreadCount").value(0));
    }

    @Test
    @DisplayName("3) 작업자가 배정되지 않은 헬멧이어도 관리자에게는 건강 이상 알림이 남는다")
    void unassignedHelmetStillNotifiesManager() throws Exception {
        Helmet spare = fixtures.helmet("H-099", "ESP32-099", null);

        sendSensor(spare.getDeviceId(), "STABLE", "ACTION_REQUIRED", 131.0, 92.0, 18.5);

        assertThat(notificationRepository.findAll())
                .extracting(Notification::getRecipientType)
                .containsExactly(NotificationRecipientType.MANAGER);
    }

    @Test
    @DisplayName("4) 센서 오류로 0 이 들어와도 헬멧별 마지막 유효 측정값을 준다")
    void latestValidVitalsSkipZeros() throws Exception {
        sendSensor(helmet.getDeviceId(), "STABLE", "NORMAL", 84.0, 97.0, 35.0);
        sendSensor(helmet.getDeviceId(), "STABLE", "NORMAL", 0.0, 0.0, 0.0);

        mockMvc.perform(get("/api/workers/" + helmet.getDeviceId() + "/vitals/latest")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.heartRate").value(84.0))
                .andExpect(jsonPath("$.data.spo2").value(97.0))
                .andExpect(jsonPath("$.data.hrv").value(35.0))
                .andExpect(jsonPath("$.data.lastReceivedAt").isNotEmpty());
    }

    @Test
    @DisplayName("6) 추락 이력(type=FALL)에는 자세 STABLE·추락 NORMAL 인 건강 이상 기록이 섞이지 않는다")
    void fallHistoryExcludesHealthOnlyRows() throws Exception {
        sendSensor(helmet.getDeviceId(), "STABLE", "ACTION_REQUIRED", 131.0, 92.0, 18.5); // 건강만 이상
        sendSensor(helmet.getDeviceId(), "STUMBLE", "NORMAL", 90.0, 97.0, 30.0);         // 추락 쪽 이상

        mockMvc.perform(get("/api/workers/alerts").param("type", "FALL")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].fallState").value("ACTION_REQUIRED"));

        mockMvc.perform(get("/api/workers/alerts").param("type", "HEALTH")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].fallState").value("NORMAL"));

        // type 을 안 주면 예전처럼 둘 다
        mockMvc.perform(get("/api/workers/alerts")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.data.length()").value(2));
    }
}
