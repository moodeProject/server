package com.moode.server.service.impl;

import com.moode.server.domain.Helmet;
import com.moode.server.domain.Worker;
import com.moode.server.domain.Zone;
import com.moode.server.exception.NotFoundException;
import com.moode.server.repository.HelmetRepository;
import com.moode.server.service.HelmetAssignment;
import com.moode.server.service.HelmetAssignmentResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 서버가 보내는 helmetId("H-001")를 helmet.helmet_no 로 보고 조회한다.
 * helmet_no 로 못 찾으면 device_id 로 한 번 더 시도한다.
 * (ESP32 쪽은 deviceId 문자열을 쓰고 있어 어느 쪽이 올지 확정되지 않았기 때문)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultHelmetAssignmentResolver implements HelmetAssignmentResolver {

    private final HelmetRepository helmetRepository;

    @Override
    @Transactional(readOnly = true)
    public HelmetAssignment resolve(String helmetCode) {
        if (helmetCode == null || helmetCode.isBlank()) {
            throw new NotFoundException("헬멧 코드가 비어 있습니다.");
        }
        String code = helmetCode.trim();

        Helmet helmet = helmetRepository.findByHelmetNo(code)
                .or(() -> helmetRepository.findByDeviceId(code))
                .orElseThrow(() -> new NotFoundException("등록되지 않은 헬멧입니다. " + code));

        // 미배정 헬멧이면 worker 가 null 이고, 그 경우 알림 대상이 없다.
        Worker worker = helmet.getWorker();
        Zone zone = worker == null ? null : worker.getCurrentZone();

        if (worker == null) {
            log.warn("배정된 작업자가 없는 헬멧에서 위험이 감지되었습니다. helmetNo={}", helmet.getHelmetNo());
        }
        return new HelmetAssignment(helmet, worker, zone);
    }
}
