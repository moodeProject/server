package com.moode.server.support;

import com.moode.server.domain.*;
import com.moode.server.repository.HelmetRepository;
import com.moode.server.repository.WorkerRepository;
import com.moode.server.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 위험 이벤트는 helmet -> worker -> zone 배정이 있어야 저장되므로 테스트마다 이 셋을 심는다.
 */
@Component
@RequiredArgsConstructor
public class TestFixtures {

    private final ZoneRepository zoneRepository;
    private final WorkerRepository workerRepository;
    private final HelmetRepository helmetRepository;

    public Zone zone(String code, String name) {
        return zoneRepository.save(Zone.builder()
                .zoneCode(code)
                .zoneName(name)
                .floor("3F")
                .riskLevel(ZoneRiskLevel.MEDIUM)
                .build());
    }

    public Worker worker(String employeeNo, String name, Zone zone) {
        return workerRepository.save(Worker.builder()
                .employeeNo(employeeNo)
                .passwordHash("hashed")
                .name(name)
                .department("공무팀")
                .status(WorkerStatus.WORKING)
                .currentZone(zone)
                .build());
    }

    public Helmet helmet(String helmetNo, String deviceId, Worker worker) {
        return helmetRepository.save(Helmet.builder()
                .helmetNo(helmetNo)
                .deviceId(deviceId)
                .worker(worker)
                .sensorStatus(SensorStatus.CONNECTED)
                .operationalStatus(OperationalStatus.IN_USE)
                .build());
    }

    /** H-001 / 박민수 / A-3F 기본 세트 */
    public Helmet defaultHelmet() {
        Zone zone = zone("A-3F", "A구역 3층");
        Worker worker = worker("WK-20241103", "박민수", zone);
        return helmet("H-001", "ESP32-001", worker);
    }
}
