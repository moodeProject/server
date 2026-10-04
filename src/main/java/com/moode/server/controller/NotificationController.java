package com.moode.server.controller;

import com.moode.server.common.ApiResponse;
import com.moode.server.domain.NotificationRecipientType;
import com.moode.server.dto.response.NotificationListResponse;
import com.moode.server.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 알림 조회/읽음 API.
 *
 * <p>인증이 아직 없어 수신자를 쿼리 파라미터로 받는다. recipientType 은 기본 MANAGER
 * (관리자 대시보드 알림함), recipientId 는 WORKER 면 workerId, MANAGER 면 adminId — 생략하면
 * manager_account 가 아직 없는 동안의 "관리자 전체 공용 수신함"으로 조회된다.
 * 인증 도입 후에는 principal 기준으로 바뀔 예정이다.
 */
@Tag(name = "알림", description = "Frontend: 알림 목록/읽음 처리 API")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "알림 목록 조회")
    @GetMapping
    public ApiResponse<NotificationListResponse> getNotifications(
            @Parameter(description = "WORKER 또는 MANAGER. 생략하면 MANAGER")
            @RequestParam(required = false, defaultValue = "MANAGER") NotificationRecipientType recipientType,
            @Parameter(description = "WORKER 면 workerId, MANAGER 면 adminId. 생략 가능")
            @RequestParam(required = false) Long recipientId,
            @RequestParam(required = false) Boolean isRead,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(
                notificationService.getNotifications(recipientType, recipientId, isRead, page, size));
    }

    @Operation(summary = "알림 읽음 처리")
    @PatchMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(id);
        return ApiResponse.success(null);
    }

    @Operation(summary = "전체 읽음 처리")
    @PatchMapping("/read-all")
    public ApiResponse<Integer> markAllRead(
            @RequestParam(required = false, defaultValue = "MANAGER") NotificationRecipientType recipientType,
            @RequestParam(required = false) Long recipientId) {
        return ApiResponse.success(notificationService.markAllRead(recipientType, recipientId));
    }
}
