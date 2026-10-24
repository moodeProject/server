package com.moode.server.service.impl;

import com.moode.server.domain.*;
import com.moode.server.dto.request.WorkerCreateRequest;
import com.moode.server.dto.request.WorkerUpdateRequest;
import com.moode.server.dto.response.WorkerDetailResponse;
import com.moode.server.dto.response.WorkerListItemResponse;
import com.moode.server.dto.response.WorkerListResponse;
import com.moode.server.exception.ConflictException;
import com.moode.server.exception.ManagementErrorCode;
import com.moode.server.exception.NotFoundException;
import com.moode.server.repository.*;
import com.moode.server.service.WorkerManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 작업자 관리(CRUD). "정상/주의/위험" 상태는 저장 컬럼이 아니라 최신 센서값(fallState/healthState) +
 * 미해결 외부요인(HazardEvent category=EXTERNAL)을 조회 시점에 조합해 계산한다 — WorkerServiceImpl의
 * heatRiskLevel 조합 방식과 같은 패턴이다.
 */
@Service
@RequiredArgsConstructor
public class WorkerManagementServiceImpl implements WorkerManagementService {

    private static final List<HazardStatus> UNRESOLVED = List.of(HazardStatus.UNHANDLED, HazardStatus.IN_PROGRESS);

    private final WorkerRepository workerRepository;
    private final ZoneRepository zoneRepository;
    private final HelmetRepository helmetRepository;
    private final SensorDataRepository sensorDataRepository;
    private final HazardEventRepository hazardEventRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public WorkerListResponse list(String q, WorkerOverallStatus statusFilter, int page, int size) {
        List<Worker> matched = workerRepository.searchActive(q);

        List<WorkerListItemResponse> all = matched.stream().map(this::toListItem).toList();

        long normalCount = all.stream().filter(w -> w.getStatus() == WorkerOverallStatus.NORMAL).count();
        long cautionCount = all.stream().filter(w -> w.getStatus() == WorkerOverallStatus.CAUTION).count();
        long dangerCount = all.stream().filter(w -> w.getStatus() == WorkerOverallStatus.DANGER).count();

        List<WorkerListItemResponse> filtered = statusFilter == null
                ? all
                : all.stream().filter(w -> w.getStatus() == statusFilter).toList();

        // 데모 규모(작업자 수십 명) 전제로, 상태 계산 후 메모리에서 페이징한다.
        int totalElements = filtered.size();
        int totalPages = size <= 0 ? 1 : (int) Math.ceil(totalElements / (double) size);
        int fromIndex = Math.min(page * size, totalElements);
        int toIndex = Math.min(fromIndex + size, totalElements);

        return WorkerListResponse.builder()
                .content(filtered.subList(fromIndex, toIndex))
                .page(page)
                .size(size)
                .totalElements(totalElements)
                .totalPages(Math.max(totalPages, 1))
                .normalCount(normalCount)
                .cautionCount(cautionCount)
                .dangerCount(dangerCount)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public WorkerDetailResponse getDetail(Long workerId) {
        return toDetail(findActiveWorker(workerId));
    }

    @Override
    @Transactional
    public WorkerDetailResponse create(WorkerCreateRequest request) {
        Zone zone = findOrCreateZoneByName(request.getZoneName());

        Worker worker = Worker.builder()
                .employeeNo(generateEmployeeNo())
                .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                .name(request.getName())
                .status(WorkerStatus.OFF_DUTY)
                .currentZone(zone)
                .build();
        worker = workerRepository.save(worker);

        if (request.getHelmetNo() != null && !request.getHelmetNo().isBlank()) {
            createAndAssignHelmet(request.getHelmetNo(), worker);
        }

        return toDetail(worker);
    }

    @Override
    @Transactional
    public WorkerDetailResponse update(Long workerId, WorkerUpdateRequest request) {
        Worker worker = findActiveWorker(workerId);
        Zone zone = findOrCreateZoneByName(request.getZoneName());
        worker.updateProfile(request.getName(), zone);
        return toDetail(worker);
    }

    @Override
    @Transactional
    public void delete(Long workerId) {
        Worker worker = findActiveWorker(workerId);
        helmetRepository.findByWorker_IdAndActiveTrue(workerId).ifPresent(Helmet::unassign);
        worker.deactivate();
    }

    private Worker findActiveWorker(Long workerId) {
        return workerRepository.findById(workerId)
                .filter(Worker::isActive)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 작업자입니다. id=" + workerId));
    }

    /** 같은 이름의 구역이 있으면 재사용하고, 없으면 새로 만든다. "A구역 3층"처럼 구역+층을 합친 텍스트를 그대로 zoneName 에 담는다. */
    private Zone findOrCreateZoneByName(String zoneName) {
        return zoneRepository.findByZoneName(zoneName)
                .orElseGet(() -> zoneRepository.save(Zone.builder()
                        .zoneCode(generateZoneCode())
                        .zoneName(zoneName)
                        .riskLevel(ZoneRiskLevel.LOW)
                        .build()));
    }

    private void createAndAssignHelmet(String helmetNo, Worker worker) {
        if (helmetRepository.findByHelmetNo(helmetNo).isPresent()) {
            throw new ConflictException(ManagementErrorCode.HELMET_NO_DUPLICATE, "이미 사용 중인 헬멧 번호입니다. " + helmetNo);
        }
        Helmet helmet = Helmet.builder()
                .helmetNo(helmetNo)
                .deviceId(generateDeviceId())
                .build();
        helmet.assignTo(worker);
        helmetRepository.save(helmet);
    }

    private String generateEmployeeNo() {
        long base = workerRepository.count() + 1;
        for (int attempt = 0; attempt < 1000; attempt++) {
            String candidate = "WK-%05d".formatted(base + attempt);
            if (workerRepository.findByEmployeeNo(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new IllegalStateException("사번 자동 채번에 실패했습니다.");
    }

    private String generateDeviceId() {
        for (int attempt = 0; attempt < 1000; attempt++) {
            String candidate = "DEV-AUTO-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            if (helmetRepository.existsByDeviceId(candidate)) {
                continue;
            }
            return candidate;
        }
        throw new IllegalStateException("기기 ID 자동 생성에 실패했습니다.");
    }

    private String generateZoneCode() {
        long base = zoneRepository.count() + 1;
        for (int attempt = 0; attempt < 1000; attempt++) {
            String candidate = "ZONE-AUTO-%03d".formatted(base + attempt);
            if (zoneRepository.findByZoneCode(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new IllegalStateException("구역 코드 자동 생성에 실패했습니다.");
    }

    // ===== 상태 계산 =====

    private WorkerListItemResponse toListItem(Worker worker) {
        Snapshot snapshot = computeSnapshot(worker);
        Zone zone = worker.getCurrentZone();
        return WorkerListItemResponse.builder()
                .id(worker.getId())
                .employeeNo(worker.getEmployeeNo())
                .name(worker.getName())
                .zoneName(zone == null ? null : zone.getZoneName())
                .floor(zone == null ? null : zone.getFloor())
                .status(snapshot.overall())
                .heartRate(snapshot.heartRate())
                .fatigueAbnormal(snapshot.fatigueAbnormal())
                .statusMessage(snapshot.statusMessage())
                .build();
    }

    private WorkerDetailResponse toDetail(Worker worker) {
        Snapshot snapshot = computeSnapshot(worker);
        Zone zone = worker.getCurrentZone();
        Helmet helmet = snapshot.helmet();

        return WorkerDetailResponse.builder()
                .id(worker.getId())
                .employeeNo(worker.getEmployeeNo())
                .name(worker.getName())
                .status(snapshot.overall())
                .zoneName(zone == null ? null : zone.getZoneName())
                .floor(zone == null ? null : zone.getFloor())
                .helmetNo(helmet == null ? null : helmet.getHelmetNo())
                .sensorConnected(helmet != null && helmet.getSensorStatus() == SensorStatus.CONNECTED)
                .external(WorkerDetailResponse.ExternalBlock.builder()
                        .status(snapshot.externalStatus())
                        .message(snapshot.externalMessage())
                        .build())
                .health(WorkerDetailResponse.HealthBlock.builder()
                        .status(snapshot.healthStatus())
                        .heartRate(snapshot.heartRate())
                        .fatigueAbnormal(snapshot.fatigueAbnormal())
                        .build())
                .fall(WorkerDetailResponse.FallBlock.builder()
                        .status(snapshot.fallStatus())
                        .message(snapshot.fallMessage())
                        .build())
                .build();
    }

    private Snapshot computeSnapshot(Worker worker) {
        Helmet helmet = helmetRepository.findByWorker_IdAndActiveTrue(worker.getId()).orElse(null);

        SensorData latest = helmet == null
                ? null
                : sensorDataRepository.findTop1ByDeviceIdOrderByRecordedAtDesc(helmet.getDeviceId()).orElse(null);

        WorkerOverallStatus fallStatus = toOverall(latest == null ? null : latest.getFallState());
        WorkerOverallStatus healthStatus = toOverall(latest == null ? null : latest.getHealthState());

        Optional<com.moode.server.domain.HazardEvent> externalEvent = hazardEventRepository
                .findFirstByWorker_IdAndCategoryAndStatusInOrderByOccurredAtDesc(
                        worker.getId(), HazardCategory.EXTERNAL, UNRESOLVED);

        WorkerOverallStatus externalStatus = externalEvent
                .map(e -> e.getSeverity() == HazardSeverity.DANGER ? WorkerOverallStatus.DANGER : WorkerOverallStatus.CAUTION)
                .orElse(WorkerOverallStatus.NORMAL);
        String externalMessage = externalEvent
                .map(e -> e.getHazardType().getLabel() + " 감지")
                .orElse("위험 요소 없음");

        String fallMessage = switch (fallStatus) {
            case DANGER -> "추락 감지됨";
            case CAUTION -> "휘청거림 감지";
            case NORMAL -> "감지 없음";
        };

        WorkerOverallStatus overall = List.of(fallStatus, healthStatus, externalStatus).stream()
                .max(Comparator.comparingInt(WorkerOverallStatus::ordinal))
                .orElse(WorkerOverallStatus.NORMAL);

        String statusMessage;
        if (fallStatus == WorkerOverallStatus.DANGER) {
            statusMessage = fallMessage;
        } else if (externalStatus != WorkerOverallStatus.NORMAL) {
            statusMessage = externalMessage;
        } else if (healthStatus == WorkerOverallStatus.DANGER) {
            statusMessage = "건강 이상 감지";
        } else if (healthStatus == WorkerOverallStatus.CAUTION) {
            statusMessage = "피로도 주의";
        } else {
            statusMessage = "정상 작업 중";
        }

        return new Snapshot(
                helmet, overall, fallStatus, healthStatus, externalStatus,
                fallMessage, externalMessage, statusMessage,
                latest == null ? null : latest.getHeartRate(),
                latest == null ? null : latest.getFatigueAbnormal());
    }

    private WorkerOverallStatus toOverall(WorkerState state) {
        if (state == null) {
            return WorkerOverallStatus.NORMAL;
        }
        return switch (state) {
            case NORMAL -> WorkerOverallStatus.NORMAL;
            case RECOMMEND -> WorkerOverallStatus.CAUTION;
            case ACTION_REQUIRED, FALLING, FALLEN, UNCONSCIOUS -> WorkerOverallStatus.DANGER;
        };
    }

    private record Snapshot(
            Helmet helmet,
            WorkerOverallStatus overall,
            WorkerOverallStatus fallStatus,
            WorkerOverallStatus healthStatus,
            WorkerOverallStatus externalStatus,
            String fallMessage,
            String externalMessage,
            String statusMessage,
            Double heartRate,
            Boolean fatigueAbnormal) {
    }
}
