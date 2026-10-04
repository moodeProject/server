package com.moode.server.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class NotificationListResponse {
    private List<NotificationResponse> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private long unreadCount;
}
