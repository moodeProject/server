package com.moode.server.controller;

import com.moode.server.common.ApiResponse;
import com.moode.server.dto.response.ZoneSummaryResponse;
import com.moode.server.repository.ZoneRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 구역 목록 조회. 헬멧이 보내는 비콘 zoneId("ZONE-01")가 어떤 구역 이름인지 프론트가
 * 미리 알아야 하는 화면(구역 필터, 드롭다운)이 있어 별도로 연다.
 *
 * <p>위험 이벤트 응답에는 이미 zone 이 통째로 실려 나가므로, 개별 이벤트의 구역 이름을
 * 표시하는 데는 이 API 가 필요하지 않다.
 *
 * <p>읽기 전용이라 서비스 계층 없이 리포지토리를 직접 쓴다. 등록/수정/삭제가 생기면
 * 그때 ZoneService 로 옮긴다.
 */
@Tag(name = "구역", description = "Frontend: 작업 구역 조회 API")
@RestController
@RequestMapping("/api/zones")
@RequiredArgsConstructor
public class ZoneController {

    private final ZoneRepository zoneRepository;

    @Operation(summary = "구역 목록 조회",
            description = "등록된 전체 구역을 zone_code 순으로 반환한다. "
                    + "code 는 헬멧이 센서 데이터의 zoneId 로 보내는 값과 같다.")
    @GetMapping
    @Transactional(readOnly = true)
    public ApiResponse<List<ZoneSummaryResponse>> getZones() {
        return ApiResponse.success(
                zoneRepository.findAllByOrderByZoneCodeAsc().stream()
                        .map(ZoneSummaryResponse::from)
                        .toList());
    }
}
