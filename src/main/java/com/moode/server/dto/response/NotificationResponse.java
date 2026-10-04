package com.moode.server.dto.response;

import com.moode.server.common.Times;
import com.moode.server.domain.Notification;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
@Builder
public class NotificationResponse {

    private Long id;
    private String recipientType;
    private String channel;
    private String message;
    private boolean isRead;
    private String triggeredBy;

    private Long hazardEventId;
    private Long sosId;

    private OffsetDateTime sentAt;
    private OffsetDateTime confirmedAt;

    public static NotificationResponse from(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .recipientType(notification.getRecipientType().name())
                .channel(notification.getChannel().name())
                .message(notification.getMessage())
                .isRead(notification.isRead())
                .triggeredBy(notification.getTriggeredBy().name())
                .hazardEventId(notification.getEvent() == null ? null : notification.getEvent().getId())
                .sosId(notification.getSosId())
                .sentAt(Times.toOffset(notification.getSentAt()))
                .confirmedAt(Times.toOffset(notification.getConfirmedAt()))
                .build();
    }
}
