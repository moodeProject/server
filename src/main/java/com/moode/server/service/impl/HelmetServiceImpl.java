package com.moode.server.service.impl;

import com.moode.server.domain.Helmet;
import com.moode.server.domain.SensorData;
import com.moode.server.dto.response.HelmetStatusResponse;
import com.moode.server.exception.NotFoundException;
import com.moode.server.repository.HelmetRepository;
import com.moode.server.repository.SensorDataRepository;
import com.moode.server.service.HelmetService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 헬멧 상태 조회(d~g). 헬멧 관리(CRUD/배정)는 HelmetManagementServiceImpl 담당. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HelmetServiceImpl implements HelmetService {

    private final HelmetRepository helmetRepository;
    private final SensorDataRepository sensorDataRepository;

    // 헬멧 관리가 소프트 삭제를 하므로, 삭제된(비활성) 헬멧은 상태 목록에서 뺀다.
    @Override
    public List<HelmetStatusResponse> getHelmetStatuses() {
        return helmetRepository.findAll().stream()
                .filter(Helmet::isActive)
                .map(this::toStatus)
                .toList();
    }

    @Override
    public HelmetStatusResponse getHelmetStatus(Long helmetId) {
        Helmet helmet = helmetRepository.findById(helmetId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 헬멧입니다. " + helmetId));
        return toStatus(helmet);
    }

    // 헬멧의 deviceId 로 가장 최근 센서 데이터 1건을 붙인다.
    // id 기준 정렬: 50Hz 수신이라 recordedAt 동률이 생길 수 있어서 (SensorDataRepository 주석 참고)
    private HelmetStatusResponse toStatus(Helmet helmet) {
        SensorData latest = sensorDataRepository
                .findTop1ByDeviceIdOrderByIdDesc(helmet.getDeviceId())
                .orElse(null);
        return HelmetStatusResponse.of(helmet, latest);
    }
}
