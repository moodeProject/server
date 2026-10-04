package com.moode.server.service.impl;

import com.moode.server.domain.Helmet;
import com.moode.server.domain.Worker;
import com.moode.server.dto.request.HelmetCreateRequest;
import com.moode.server.dto.request.HelmetUpdateRequest;
import com.moode.server.dto.response.HelmetResponse;
import com.moode.server.exception.ConflictException;
import com.moode.server.exception.ManagementErrorCode;
import com.moode.server.exception.NotFoundException;
import com.moode.server.repository.HelmetRepository;
import com.moode.server.repository.WorkerRepository;
import com.moode.server.service.HelmetManagementService;
import com.moode.server.service.HelmetReconnectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class HelmetManagementServiceImpl implements HelmetManagementService {

    private final HelmetRepository helmetRepository;
    private final WorkerRepository workerRepository;
    private final HelmetReconnectService helmetReconnectService;

    @Override
    @Transactional(readOnly = true)
    public List<HelmetResponse> list(String q) {
        return helmetRepository.searchActive(q).stream().map(HelmetResponse::from).toList();
    }

    @Override
    @Transactional
    public HelmetResponse create(HelmetCreateRequest request) {
        if (helmetRepository.findByHelmetNo(request.getHelmetNo()).isPresent()) {
            throw new ConflictException(ManagementErrorCode.HELMET_NO_DUPLICATE,
                    "이미 사용 중인 헬멧 번호입니다. " + request.getHelmetNo());
        }
        if (helmetRepository.existsByDeviceId(request.getDeviceId())) {
            throw new ConflictException("이미 사용 중인 기기 ID입니다. " + request.getDeviceId());
        }

        Helmet helmet = Helmet.builder()
                .helmetNo(request.getHelmetNo())
                .deviceId(request.getDeviceId())
                .memo(request.getMemo())
                .build();

        if (request.getWorkerId() != null) {
            Worker worker = findActiveWorker(request.getWorkerId());
            requireNoOtherActiveHelmet(worker, null);
            helmet.assignTo(worker);
        }

        return HelmetResponse.from(helmetRepository.save(helmet));
    }

    @Override
    @Transactional
    public HelmetResponse update(Long helmetId, HelmetUpdateRequest request) {
        Helmet helmet = findActiveHelmet(helmetId);
        helmet.updateMemo(request.getMemo());
        return HelmetResponse.from(helmet);
    }

    @Override
    @Transactional
    public void delete(Long helmetId) {
        Helmet helmet = findActiveHelmet(helmetId);
        helmet.unassign();
        helmet.deactivate();
    }

    @Override
    @Transactional
    public HelmetResponse assign(Long helmetId, Long workerId) {
        Helmet helmet = findActiveHelmet(helmetId);
        Worker worker = findActiveWorker(workerId);

        if (helmet.isAssigned()) {
            if (helmet.getWorker().getId().equals(workerId)) {
                return HelmetResponse.from(helmet); // 이미 같은 조합 — 멱등하게 성공 처리
            }
            throw new ConflictException(ManagementErrorCode.HELMET_ALREADY_ASSIGNED,
                    "이미 다른 작업자(" + helmet.getWorker().getName() + ")에게 배정된 헬멧입니다.");
        }
        requireNoOtherActiveHelmet(worker, helmetId);

        helmet.assignTo(worker);
        return HelmetResponse.from(helmet);
    }

    @Override
    @Transactional
    public HelmetResponse unassign(Long helmetId) {
        Helmet helmet = findActiveHelmet(helmetId);
        helmet.unassign();
        return HelmetResponse.from(helmet);
    }

    @Override
    @Transactional
    public HelmetResponse reconnect(Long helmetId) {
        Helmet helmet = findActiveHelmet(helmetId);
        helmetReconnectService.requestReconnect(helmet);
        return HelmetResponse.from(helmet);
    }

    private Helmet findActiveHelmet(Long helmetId) {
        return helmetRepository.findById(helmetId)
                .filter(Helmet::isActive)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 헬멧입니다. id=" + helmetId));
    }

    private Worker findActiveWorker(Long workerId) {
        return workerRepository.findById(workerId)
                .filter(Worker::isActive)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 작업자입니다. id=" + workerId));
    }

    /** excludeHelmetId: 자기 자신(지금 배정하려는 헬멧)은 "이미 다른 헬멧을 보유" 판단에서 빼야 한다. */
    private void requireNoOtherActiveHelmet(Worker worker, Long excludeHelmetId) {
        helmetRepository.findByWorker_IdAndActiveTrue(worker.getId())
                .filter(existing -> excludeHelmetId == null || !existing.getId().equals(excludeHelmetId))
                .ifPresent(existing -> {
                    throw new ConflictException(ManagementErrorCode.WORKER_ALREADY_HAS_HELMET,
                            "이미 배정된 헬멧(" + existing.getHelmetNo() + ")이 있는 작업자입니다. 먼저 해제해주세요.");
                });
    }
}
