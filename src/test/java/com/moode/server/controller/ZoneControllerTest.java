package com.moode.server.controller;

import com.moode.server.repository.HazardClipRepository;
import com.moode.server.repository.HazardEventActionRepository;
import com.moode.server.repository.HazardEventRepository;
import com.moode.server.repository.HelmetRepository;
import com.moode.server.repository.WorkerRepository;
import com.moode.server.repository.ZoneRepository;
import com.moode.server.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ZoneControllerTest {

    private static final String PATH = "/api/zones";

    @Autowired private MockMvc mockMvc;
    @Autowired private HazardEventActionRepository hazardEventActionRepository;
    @Autowired private HazardClipRepository hazardClipRepository;
    @Autowired private HazardEventRepository hazardEventRepository;
    @Autowired private HelmetRepository helmetRepository;
    @Autowired private WorkerRepository workerRepository;
    @Autowired private ZoneRepository zoneRepository;
    @Autowired private TestFixtures fixtures;

    @BeforeEach
    void clean() {
        hazardEventActionRepository.deleteAll();
        hazardClipRepository.deleteAll();
        hazardEventRepository.deleteAll();
        helmetRepository.deleteAll();
        workerRepository.deleteAll();
        zoneRepository.deleteAll();
    }

    @Test
    @DisplayName("등록된 구역을 코드순으로 반환한다")
    void returnsZonesOrderedByCode() throws Exception {
        // 일부러 역순으로 넣어 정렬이 실제로 적용되는지 본다.
        fixtures.zone("ZONE-02", "B구역 1층");
        fixtures.zone("ZONE-01", "A구역 3층");

        mockMvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].code").value("ZONE-01"))
                .andExpect(jsonPath("$.data[0].name").value("A구역 3층"))
                .andExpect(jsonPath("$.data[0].id").isNumber())
                .andExpect(jsonPath("$.data[1].code").value("ZONE-02"))
                .andExpect(jsonPath("$.data[1].name").value("B구역 1층"));
    }

    @Test
    @DisplayName("등록된 구역이 없으면 빈 배열을 반환한다")
    void returnsEmptyListWhenNoZones() throws Exception {
        mockMvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(0));
    }
}
