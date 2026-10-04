package com.moode.server.controller;

import com.moode.server.common.ApiResponse;
import com.moode.server.dto.request.HelmetAssignRequest;
import com.moode.server.dto.request.HelmetCreateRequest;
import com.moode.server.dto.request.HelmetUpdateRequest;
import com.moode.server.dto.response.HelmetResponse;
import com.moode.server.dto.response.HelmetStatusResponse;
import com.moode.server.service.HelmetManagementService;
import com.moode.server.service.HelmetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "헬멧 관리", description = "Admin 화면: 헬멧 등록/수정/삭제/배정")
@RestController
@RequestMapping("/api/helmets")
@RequiredArgsConstructor
public class HelmetController {

    private final HelmetManagementService helmetManagementService;
    private final HelmetService helmetService; // 헬멧 상태 조회(d~g)용

    @Operation(summary = "헬멧 목록", description = "q: 헬멧번호 또는 연결된 작업자 이름 검색")
    @GetMapping
    public ApiResponse<List<HelmetResponse>> list(@RequestParam(required = false) String q) {
        return ApiResponse.success(helmetManagementService.list(q));
    }

    @Operation(summary = "헬멧 등록", description = "workerId를 함께 보내면 등록과 동시에 배정한다.")
    @PostMapping
    public ApiResponse<HelmetResponse> create(@Valid @RequestBody HelmetCreateRequest request) {
        return ApiResponse.success("헬멧이 등록되었습니다.", helmetManagementService.create(request));
    }

    @Operation(summary = "헬멧 정보 수정", description = "메모만 수정 대상이다. 헬멧번호/기기ID는 등록 후 변경하지 않는다.")
    @PutMapping("/{helmetId}")
    public ApiResponse<HelmetResponse> update(
            @PathVariable Long helmetId, @Valid @RequestBody HelmetUpdateRequest request) {
        return ApiResponse.success(helmetManagementService.update(helmetId, request));
    }

    @Operation(summary = "헬멧 삭제", description = "소프트 삭제(active=false). 배정돼 있었으면 자동으로 해제된다.")
    @DeleteMapping("/{helmetId}")
    public ApiResponse<Void> delete(@PathVariable Long helmetId) {
        helmetManagementService.delete(helmetId);
        return ApiResponse.success("헬멧이 삭제되었습니다.", null);
    }

    @Operation(summary = "작업자에게 배정", description = "이미 같은 작업자에게 배정돼 있으면 멱등하게 성공 처리한다.")
    @PostMapping("/{helmetId}/assign")
    public ApiResponse<HelmetResponse> assign(
            @PathVariable Long helmetId, @Valid @RequestBody HelmetAssignRequest request) {
        return ApiResponse.success(helmetManagementService.assign(helmetId, request.getWorkerId()));
    }

    @Operation(summary = "배정 해제", description = "원래 미배정 상태였으면 멱등하게 성공 처리한다.")
    @PostMapping("/{helmetId}/unassign")
    public ApiResponse<HelmetResponse> unassign(@PathVariable Long helmetId) {
        return ApiResponse.success(helmetManagementService.unassign(helmetId));
    }

    @Operation(summary = "센서 재연결 요청", description = "하드웨어 연동 전 스텁 — 요청을 기록만 하고 상태는 바꾸지 않는다.")
    @PostMapping("/{helmetId}/reconnect")
    public ApiResponse<HelmetResponse> reconnect(@PathVariable Long helmetId) {
        return ApiResponse.success(helmetManagementService.reconnect(helmetId));
    }

    // ===================== 헬멧 상태 조회 (d~g) =====================

    @Operation(summary = "전체 헬멧 상태 조회",
            description = "심박·SpO2·추락 상태·종합 위험·마지막 측정 시간. 삭제되지 않은 헬멧만 나온다.")
    @GetMapping("/status")
    public ApiResponse<List<HelmetStatusResponse>> getHelmetStatuses() {
        return ApiResponse.success(helmetService.getHelmetStatuses());
    }

    @Operation(summary = "특정 헬멧 상태 조회", description = "작업자 상세 화면용")
    @GetMapping("/{helmetId}/status")
    public ApiResponse<HelmetStatusResponse> getHelmetStatus(@PathVariable Long helmetId) {
        return ApiResponse.success(helmetService.getHelmetStatus(helmetId));
    }
}
