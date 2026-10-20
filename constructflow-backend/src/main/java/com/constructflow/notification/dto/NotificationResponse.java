package com.constructflow.notification.dto;

import com.constructflow.notification.entity.Notification;
import com.constructflow.notification.entity.NotificationType;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(UUID id, NotificationType type, String title, String message, String link,
                                   boolean read, Instant createdAt) {

    public static NotificationResponse of(Notification n) {
        return new NotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getMessage(), n.getLink(),
                n.getReadAt() != null, n.getCreatedAt());
    }
}
