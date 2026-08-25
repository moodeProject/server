package com.moode.server.common;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 서비스 기준 시간대 변환.
 *
 * <p>이 서비스의 기준 시간대는 Asia/Seoul 로 고정한다. JVM 기본 시간대를 따라가면
 * 같은 코드가 장비(로컬 / CI / EC2)에 따라 다르게 동작한다. 실제로 CI(UTC)에서
 * 응답 오프셋이 +09:00 이 아니라 Z 로 나가고 기간 필터가 9시간 어긋났다.
 *
 * <p>JDBC URL 의 serverTimezone=Asia/Seoul, 기존 SensorData 의 LocalDateTime.now() 도
 * 모두 KST 를 전제하고 있어 그와 일치시킨 것이다.
 * JVM 기본 시간대 자체도 {@link com.moode.server.config.TimeZoneConfig} 에서 여기에 맞춘다.
 */
public final class Times {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private Times() {
    }

    /** 저장된 시각(LocalDateTime) -> 응답용 오프셋 포함 시각 */
    public static OffsetDateTime toOffset(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atZone(ZONE).toOffsetDateTime();
    }

    /** 외부에서 받은 오프셋 포함 시각 -> 저장용 시각. 같은 순간을 서비스 시간대로 환산한다. */
    public static LocalDateTime toLocal(OffsetDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atZoneSameInstant(ZONE).toLocalDateTime();
    }
}
