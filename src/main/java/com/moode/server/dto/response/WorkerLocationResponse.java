package com.moode.server.dto.response;

import com.moode.server.common.Times;
import com.moode.server.domain.Worker;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

/**
 * 작업자의 현재 작업 위치 (c. 현재 작업 위치).
 *
 * <p>헬멧이 BLE 비콘 스캔으로 보낸 zoneId를 서버가 zone 테이블로 매칭해 Worker.currentZone을
 * 갱신하는 경로는 이미 있다 ({@code SensorDataServiceImpl.updateWorkerLocation()}).
 * 이 DTO는 그 결과를 GET /api/workers, GET /api/workers/{workerId} 응답에 끼워 넣기 위한
 * 조립용 블록이다. Worker 엔티티는 건드리지 않는다.
 *
 * <p>nearestBeaconId / rssi / posX / posY 는 현재 채워지는 경로가 없다(펌웨어가 구역 이름만
 * 보낸다). 지도 화면이 좌표를 요구하면 이 DTO만으로는 부족하므로 별도 확인이 필요하다.
 */
@Getter
@Builder
public class WorkerLocationResponse {

    @Schema(description = "구역 PK. 구역이 아직 판정되지 않았으면 null", nullable = true)
    private Long zoneId;

    @Schema(description = "구역 코드 (예: ZONE-01). 헬멧이 보내는 zoneId 원본과 같다", nullable = true)
    private String zoneCode;

    @Schema(description = "구역 이름 (예: 구역 1 (임시명))", nullable = true)
    private String zoneName;

    @Schema(nullable = true)
    private String floor;

    @Schema(description = "위치가 마지막으로 갱신된 시각. 한 번도 갱신되지 않았으면 null", nullable = true)
    private OffsetDateTime updatedAt;

    public static WorkerLocationResponse from(Worker worker) {
        if (worker == null) {
            return WorkerLocationResponse.builder().build();
        }
        var zone = worker.getCurrentZone();
        return WorkerLocationResponse.builder()
                .zoneId(zone == null ? null : zone.getId())
                .zoneCode(zone == null ? null : zone.getZoneCode())
                .zoneName(zone == null ? null : zone.getZoneName())
                .floor(zone == null ? null : zone.getFloor())
                .updatedAt(Times.toOffset(worker.getPositionUpdatedAt()))
                .build();
    }
}
