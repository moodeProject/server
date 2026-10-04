package com.moode.server.controller;

import com.moode.server.common.ApiResponse;
import com.moode.server.domain.SosStatus;
import com.moode.server.dto.request.SosCreateRequest;
import com.moode.server.dto.response.SosResponse;
import com.moode.server.service.SosService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "SOS", description = "근로자 긴급 요청 API")
@RestController
@RequestMapping("/api/sos")
@RequiredArgsConstructor
public class SosController {

    private final SosService sosService;

    @Operation(summary = "SOS 요청",
            description = "누른 사람의 현재 구역·착용 헬멧을 함께 저장. 관리자 전화 연결은 화면에서 tel: 링크로 처리")
    @PostMapping
    public ApiResponse<SosResponse> request(@Valid @RequestBody SosCreateRequest request) {
        return ApiResponse.success(sosService.request(request));
    }

    @Operation(summary = "SOS 목록 조회", description = "최신순. status=REQUESTED 면 미처리만")
    @GetMapping
    public ApiResponse<List<SosResponse>> getSosList(@RequestParam(required = false) SosStatus status) {
        return ApiResponse.success(sosService.getSosList(status));
    }

    @Operation(summary = "SOS 처리 완료", description = "body 없음. 이미 완료된 건이면 409")
    @PatchMapping("/{sosId}")
    public ApiResponse<SosResponse> resolve(@PathVariable Long sosId) {
        return ApiResponse.success(sosService.resolve(sosId));
    }
}
