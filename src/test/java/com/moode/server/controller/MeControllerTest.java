package com.moode.server.controller;

import com.moode.server.domain.*;
import com.moode.server.repository.*;
import com.moode.server.security.JwtTokenProvider;
import com.moode.server.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MeControllerTest {

    private static final String PATH = "/api/me/home";

    @Autowired private MockMvc mockMvc;
    @Autowired private WorkerRepository workerRepository;
    @Autowired private HelmetRepository helmetRepository;
    @Autowired private ZoneRepository zoneRepository;
    @Autowired private AdminRepository adminRepository;
    @Autowired private HazardEventRepository hazardEventRepository;
    @Autowired private HazardEventActionRepository hazardEventActionRepository;
    @Autowired private HazardClipRepository hazardClipRepository;
    @Autowired private SensorDataRepository sensorDataRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private TestFixtures fixtures;

    @BeforeEach
    void setUp() {
        // 공유 H2 인메모리 DB — HelmetControllerTest 와 같은 자식 -> 부모 순서로 지운다.
        hazardEventActionRepository.deleteAll();
        hazardClipRepository.deleteAll();
        hazardEventRepository.deleteAll();
        sensorDataRepository.deleteAll();
        helmetRepository.deleteAll();
        workerRepository.deleteAll();
        zoneRepository.deleteAll();
        adminRepository.deleteAll();
    }

    private String tokenOf(Worker worker) {
        return jwtTokenProvider.generateToken(worker.getId(), UserType.WORKER, worker.getEmployeeNo(), worker.getName());
    }

    @Test
    @DisplayName("배정된 헬멧의 최신 센서값과 내 구역을 함께 받는다")
    void homeReturnsMyHelmetAndLatestSensor() throws Exception {
        Zone zone = fixtures.zone("B-3F", "B구역 3층");
        Worker me = fixtures.worker("WK-0001", "테스트 작업자", zone);
        Helmet helmet = fixtures.helmet("H-001", "ESP32-001", me);

        sensorDataRepository.save(SensorData.builder()
                .deviceId(helmet.getDeviceId())
                .heartRate(80.0)
                .fallState(WorkerState.NORMAL)
                .healthState(WorkerState.NORMAL)
                .build());
        sensorDataRepository.save(SensorData.builder()
                .deviceId(helmet.getDeviceId())
                .heartRate(92.0)
                .fatigueAbnormal(true)
                .heatRiskAbnormal(true)
                .posture("STUMBLE")
                .fallState(WorkerState.ACTION_REQUIRED)
                .healthState(WorkerState.RECOMMEND)
                .build());

        mockMvc.perform(get(PATH).header("Authorization", "Bearer " + tokenOf(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.worker.employeeNo").value("WK-0001"))
                .andExpect(jsonPath("$.data.worker.status").value("WORKING"))
                .andExpect(jsonPath("$.data.worker.zone.zoneName").value("B구역 3층"))
                .andExpect(jsonPath("$.data.worker.zone.floor").value("3F"))
                .andExpect(jsonPath("$.data.helmet.helmetNo").value("H-001"))
                .andExpect(jsonPath("$.data.helmet.sensorStatus").value("CONNECTED"))
                .andExpect(jsonPath("$.data.sensor.heartRate").value(92.0))
                .andExpect(jsonPath("$.data.sensor.fallState").value("ACTION_REQUIRED"))
                .andExpect(jsonPath("$.data.sensor.overallRiskState").value("ACTION_REQUIRED"))
                .andExpect(jsonPath("$.data.sensor.postureAbnormal").value(true))
                .andExpect(jsonPath("$.data.sensor.fatigueAbnormal").value(true))
                // 테스트 환경은 기상 캐시가 NORMAL 이라 몸 신호만으로 RECOMMEND
                .andExpect(jsonPath("$.data.sensor.heatRiskLevel").value("RECOMMEND"))
                .andExpect(jsonPath("$.data.sensor.externalHeatWarning").value(false));
    }

    @Test
    @DisplayName("다른 작업자의 헬멧 데이터는 섞이지 않는다")
    void homeReturnsOnlyMyHelmet() throws Exception {
        Worker me = fixtures.worker("WK-0001", "테스트 작업자", null);
        Worker other = fixtures.worker("WK-0002", "다른 작업자", null);
        fixtures.helmet("H-001", "ESP32-001", me);
        Helmet otherHelmet = fixtures.helmet("H-002", "ESP32-002", other);
        sensorDataRepository.save(SensorData.builder()
                .deviceId(otherHelmet.getDeviceId())
                .heartRate(130.0)
                .build());

        mockMvc.perform(get(PATH).header("Authorization", "Bearer " + tokenOf(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.helmet.helmetNo").value("H-001"))
                .andExpect(jsonPath("$.data.sensor").value(nullValue()));
    }

    @Test
    @DisplayName("헬멧이 미배정이면 404가 아니라 helmet·sensor 가 null 인 200을 받는다")
    void homeWithoutHelmet() throws Exception {
        Worker me = fixtures.worker("WK-0003", "미배정 작업자", null);

        mockMvc.perform(get(PATH).header("Authorization", "Bearer " + tokenOf(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.worker.name").value("미배정 작업자"))
                .andExpect(jsonPath("$.data.worker.zone").value(nullValue()))
                .andExpect(jsonPath("$.data.helmet").value(nullValue()))
                .andExpect(jsonPath("$.data.sensor").value(nullValue()));
    }

    // ===================== 권한 =====================

    @Test
    @DisplayName("토큰 없이 조회하면 401을 받는다")
    void homeRejectsAnonymous() throws Exception {
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("ADMIN 토큰으로 조회하면 403을 받는다")
    void homeRejectsAdminRole() throws Exception {
        String adminToken = jwtTokenProvider.generateToken(1L, UserType.ADMIN, "admin", "관리자");
        mockMvc.perform(get(PATH).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }
}
