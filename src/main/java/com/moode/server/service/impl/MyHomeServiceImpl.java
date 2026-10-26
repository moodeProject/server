package com.moode.server.service.impl;

import com.moode.server.domain.Helmet;
import com.moode.server.domain.SensorData;
import com.moode.server.domain.Worker;
import com.moode.server.dto.response.HeatRiskResponse;
import com.moode.server.dto.response.HelmetStatusResponse;
import com.moode.server.dto.response.MyHomeResponse;
import com.moode.server.exception.NotFoundException;
import com.moode.server.repository.HelmetRepository;
import com.moode.server.repository.SensorDataRepository;
import com.moode.server.repository.WorkerRepository;
import com.moode.server.service.MyHomeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyHomeServiceImpl implements MyHomeService {

    private final WorkerRepository workerRepository;
    private final HelmetRepository helmetRepository;
    private final SensorDataRepository sensorDataRepository;
    private final WeatherHeatRiskCache weatherHeatRiskCache;

    // 헬멧 미배정/센서 미수신은 에러가 아니라 화면이 "배정 없음/데이터 없음" 을 그리는 정상 상태라 null 로 내린다.
    @Override
    public MyHomeResponse getHome(Long workerId) {
        Worker worker = workerRepository.findById(workerId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 작업자입니다. " + workerId));

        Helmet helmet = helmetRepository.findByWorker_IdAndActiveTrue(workerId).orElse(null);
        if (helmet == null) {
            return MyHomeResponse.of(worker, null, null, null);
        }

        // HelmetServiceImpl 과 같은 기준(id 역순 1건)으로 최신 측정값을 고른다.
        SensorData latest = sensorDataRepository
                .findTop1ByDeviceIdOrderByIdDesc(helmet.getDeviceId())
                .orElse(null);
        if (latest == null) {
            return MyHomeResponse.of(worker, helmet, null, null);
        }

        return MyHomeResponse.of(
                worker,
                helmet,
                HelmetStatusResponse.of(helmet, latest),
                HeatRiskResponse.of(latest, weatherHeatRiskCache.currentLevel()));
    }
}
