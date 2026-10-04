package com.moode.server.controller;

import com.moode.server.common.ApiResponse;
import com.moode.server.domain.WorkerOverallStatus;
import com.moode.server.dto.request.WorkerCreateRequest;
import com.moode.server.dto.request.WorkerUpdateRequest;
import com.moode.server.dto.response.WorkerDetailResponse;
import com.moode.server.dto.response.WorkerListResponse;
import com.moode.server.service.WorkerManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 작업자 관리(CRUD, ADMIN 전용). 같은 base path(/api/workers)를 쓰는 기존 {@link WorkerController}는
 * deviceId 기준 "센서 상태 조회" 용도라 그대로 둔다.
 *
 * <p>상세 조회(GET /{workerId})가 기존 {@code GET /api/workers/{deviceId}} 와 경로 패턴이 완전히 같아서
 * (정규식으로 숫자만 받게 제한해도 스프링이 요청마다 Ambiguous handler methods 로 500을 던졌다) 기존
 * 엔드포인트를 {@code GET /api/workers/status/{deviceId}} 로 옮겼다. 이 쪽(새 CRUD)이 요청 스펙 그대로다.
 */
@Tag(name = "작업자 관리", description = "Admin 화면: 작업자 목록/상세/등록/수정/삭제")
@RestController
@RequestMapping("/api/workers")
@RequiredArgsConstructor
public class WorkerManagementController {

    private final WorkerManagementService workerManagementService;

    @Operation(summary = "작업자 목록", description = "q: 이름/사번 검색. status: NORMAL/CAUTION/DANGER 필터(선택)")
    @GetMapping
    public ApiResponse<WorkerListResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) WorkerOverallStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(workerManagementService.list(q, status, page, size));
    }

    @Operation(summary = "작업자 상세")
    @GetMapping("/{workerId}")
    public ApiResponse<WorkerDetailResponse> getDetail(@PathVariable Long workerId) {
        return ApiResponse.success(workerManagementService.getDetail(workerId));
    }

    @Operation(summary = "작업자 등록", description = "헬멧번호를 입력하면 같은 번호의 새 헬멧을 만들어 바로 배정한다.")
    @PostMapping
    public ApiResponse<WorkerDetailResponse> create(@Valid @RequestBody WorkerCreateRequest request) {
        return ApiResponse.success("작업자가 등록되었습니다.", workerManagementService.create(request));
    }

    @Operation(summary = "작업자 수정")
    @PutMapping("/{workerId}")
    public ApiResponse<WorkerDetailResponse> update(
            @PathVariable Long workerId, @Valid @RequestBody WorkerUpdateRequest request) {
        return ApiResponse.success(workerManagementService.update(workerId, request));
    }

    @Operation(summary = "작업자 삭제", description = "소프트 삭제(active=false). 배정된 헬멧은 자동으로 해제된다.")
    @DeleteMapping("/{workerId}")
    public ApiResponse<Void> delete(@PathVariable Long workerId) {
        workerManagementService.delete(workerId);
        return ApiResponse.success("작업자가 삭제되었습니다.", null);
    }
}
