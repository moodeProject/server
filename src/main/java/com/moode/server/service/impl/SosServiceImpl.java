package com.moode.server.service.impl;

import com.moode.server.domain.Helmet;
import com.moode.server.domain.Sos;
import com.moode.server.domain.SosStatus;
import com.moode.server.domain.Worker;
import com.moode.server.dto.request.SosCreateRequest;
import com.moode.server.dto.response.SosResponse;
import com.moode.server.exception.ConflictException;
import com.moode.server.exception.NotFoundException;
import com.moode.server.repository.HelmetRepository;
import com.moode.server.repository.SosRepository;
import com.moode.server.repository.WorkerRepository;
import com.moode.server.service.NotificationService;
import com.moode.server.service.SosService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * SOS 저장·조회·처리 완료와, SOS 접수 시 관리자 알림 저장 요청까지 담당한다.
 * 알림 자체의 저장·조회는 {@link NotificationService} 가 맡고,
 * 관리자와의 전화 연결은 근로자 화면(tel: 링크)에서 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SosServiceImpl implements SosService {

    private final SosRepository sosRepository;
    private final WorkerRepository workerRepository;
    private final HelmetRepository helmetRepository;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public SosResponse request(SosCreateRequest request) {
        Worker requester = workerRepository.findById(request.getRequesterId())
                .orElseThrow(() -> new NotFoundException("존재하지 않는 작업자입니다. " + request.getRequesterId()));

        // 누른 순간의 헬멧·구역을 스냅샷으로 남긴다.
        // 헬멧 미착용이거나 비콘이 안 잡히면 null — 그 경우는 전화 통화로 보완한다.
        Helmet helmet = helmetRepository.findByWorker_IdAndActiveTrue(requester.getId()).orElse(null);

        Sos sos = Sos.builder()
                .requester(requester)
                .requesterName(requester.getName())
                .requesterEmployeeNo(requester.getEmployeeNo())
                .helmet(helmet)
                .zone(requester.getCurrentZone())
                .zoneUpdatedAt(requester.getPositionUpdatedAt())
                .build();
        Sos saved = sosRepository.save(sos);
        notifyManagers(saved, requester);
        return SosResponse.from(saved);
    }

    /**
     * 관리자 알림함에 SOS 접수 알림을 남긴다.
     * 알림 저장이 실패해도 SOS 자체는 접수돼야 하므로 예외를 삼킨다.
     * (notifySosRaised 가 별도 트랜잭션이라, 여기서 잡아도 SOS 저장 트랜잭션은 롤백 표시가 되지 않는다)
     */
    private void notifyManagers(Sos sos, Worker requester) {
        try {
            notificationService.notifySosRaised(
                    sos.getId(), requester.getId(), requester.getName(),
                    sos.getZone() == null ? null : sos.getZone().getZoneName());
        } catch (Exception e) {
            log.error("SOS 알림 저장 실패(SOS는 저장됨): sosId={}, {}", sos.getId(), e.getMessage(), e);
        }
    }

    @Override
    public List<SosResponse> getSosList(SosStatus status) {
        List<Sos> list = (status == null)
                ? sosRepository.findAllByOrderByCreatedAtDesc()
                : sosRepository.findByStatusOrderByCreatedAtDesc(status);
        return list.stream()
                .map(SosResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public SosResponse resolve(Long sosId) {
        Sos sos = sosRepository.findById(sosId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 SOS 입니다. " + sosId));
        if (!sos.resolve()) {
            throw new ConflictException("이미 처리 완료된 SOS 입니다. " + sosId);
        }
        return SosResponse.from(sos);
    }
}
