package com.moode.server.config;

import com.moode.server.domain.HazardSeverity;
import com.moode.server.domain.HazardType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * hazardType -> severity 판정 규칙. AI 서버는 등급을 보내지 않고 서버가 여기서 결정한다.
 *
 * <p>회사 안전규정이 바뀌면 코드가 아니라 application.yml 의 hazard.severity 만 고치면 된다.
 * <pre>
 * hazard:
 *   severity:
 *     puddle: WARNING
 *     pothole: WARNING
 *     forklift: DANGER
 * </pre>
 * 설정에 없는 종류는 {@link HazardType#getDefaultSeverity()} 로 fallback 하므로,
 * 새 hazardType 을 추가해도 설정 누락으로 판정이 깨지지 않는다.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "hazard")
public class HazardSeverityPolicy {

    private Map<HazardType, HazardSeverity> severity = new EnumMap<>(HazardType.class);

    public HazardSeverity resolve(HazardType hazardType) {
        HazardSeverity configured = severity.get(hazardType);
        return configured != null ? configured : hazardType.getDefaultSeverity();
    }
}
