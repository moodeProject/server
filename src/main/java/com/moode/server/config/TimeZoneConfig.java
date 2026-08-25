package com.moode.server.config;

import com.moode.server.common.Times;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

/**
 * JVM 기본 시간대를 서비스 기준 시간대로 고정한다.
 *
 * <p>엔티티의 LocalDateTime.now() 처럼 기본 시간대에 의존하는 코드가 이미 있어서,
 * 장비마다 시간대가 다르면 저장되는 값이 달라진다. 배포 환경 설정(TZ 환경변수)에 맡기지 않고
 * 코드에서 못박아 로컬 / CI / 운영이 동일하게 동작하게 한다.
 */
@Slf4j
@Configuration
public class TimeZoneConfig {

    @PostConstruct
    public void setDefaultTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone(Times.ZONE));
        log.info("서비스 기준 시간대 설정: {}", Times.ZONE);
    }
}
