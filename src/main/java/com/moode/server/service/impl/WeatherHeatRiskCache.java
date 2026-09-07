package com.moode.server.service.impl;

import com.moode.server.domain.WorkerState;
import com.moode.server.infrastructure.client.WeatherClient;
import com.moode.server.infrastructure.dto.response.KmaWeatherResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 외부 기상(기온/습도) 을 주기적으로 가져와 체감온도 기반 위험도를 캐싱한다.
 * 매 센서 요청마다 외부 API를 호출하지 않도록, checkHealth() 는 이 캐시만 읽는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherHeatRiskCache {

    private final WeatherClient weatherClient;

    @Value("${weather.api.service-key}")
    private String serviceKey;
    @Value("${weather.site.nx}")
    private int nx;
    @Value("${weather.site.ny}")
    private int ny;

    // 고용노동부 기준: 체감온도 33도 이상 주의(RECOMMEND), 35도 이상 경보(ACTION_REQUIRED)
    private static final double CAUTION_THRESHOLD = 33.0;
    private static final double WARNING_THRESHOLD = 35.0;

    private final AtomicReference<WorkerState> cachedLevel = new AtomicReference<>(WorkerState.NORMAL);
    private final AtomicReference<Double> cachedApparentTemp = new AtomicReference<>(null);

    @Scheduled(fixedRate = 10 * 60 * 1000) // 10분마다 갱신
    public void refresh() {
        try {
            LocalDateTime now = LocalDateTime.now();
            // 초단기실황은 매시 40분에 생성되므로, 40분 이전이면 전 시간 데이터를 사용
            LocalDateTime baseDateTime = now.getMinute() < 40 ? now.minusHours(1) : now;
            String baseDate = baseDateTime.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String baseTime = baseDateTime.format(DateTimeFormatter.ofPattern("HH")) + "00";

            KmaWeatherResponse res = weatherClient.getUltraSrtNcst(
                    serviceKey, 1, 10, "JSON", baseDate, baseTime, nx, ny);

            Double ta = null;
            Double rh = null;
            for (KmaWeatherResponse.Item item : res.getResponse().getBody().getItems().getItem()) {
                if ("T1H".equals(item.getCategory())) {
                    ta = Double.parseDouble(item.getObsrValue());
                } else if ("REH".equals(item.getCategory())) {
                    rh = Double.parseDouble(item.getObsrValue());
                }
            }

            if (ta == null || rh == null) {
                log.warn("기상 응답에 T1H/REH 값이 없어 캐시를 유지합니다.");
                return;
            }

            double apparentTemp = HeatIndexCalculator.calculate(ta, rh);
            WorkerState level = apparentTemp >= WARNING_THRESHOLD ? WorkerState.ACTION_REQUIRED
                    : apparentTemp >= CAUTION_THRESHOLD ? WorkerState.RECOMMEND
                    : WorkerState.NORMAL;

            cachedApparentTemp.set(apparentTemp);
            cachedLevel.set(level);
            log.info("외부 기상 갱신 완료: 기온={}, 습도={}, 체감온도={}, level={}", ta, rh, apparentTemp, level);

        } catch (Exception e) {
            // 외부 API 장애가 전체 판단 파이프라인을 막지 않도록, 실패 시 직전 캐시값을 그대로 유지
            log.error("외부 기상 정보 갱신 실패: {}", e.getMessage());
        }
    }

    public WorkerState currentLevel() {
        return cachedLevel.get();
    }

    public Double currentApparentTemperature() {
        return cachedApparentTemp.get();
    }
}
