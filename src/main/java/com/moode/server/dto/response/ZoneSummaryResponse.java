package com.moode.server.dto.response;

import com.moode.server.domain.Zone;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ZoneSummaryResponse {

    private Long id;
    private String code;
    private String name;

    public static ZoneSummaryResponse from(Zone zone) {
        if (zone == null) {
            return null;
        }
        return ZoneSummaryResponse.builder()
                .id(zone.getId())
                .code(zone.getZoneCode())
                .name(zone.getZoneName())
                .build();
    }
}
