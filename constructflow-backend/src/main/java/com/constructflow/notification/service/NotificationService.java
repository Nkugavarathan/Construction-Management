package com.constructflow.notification.service;

import com.constructflow.common.exception.NotFoundException;
import com.constructflow.common.response.PageResponse;
import com.constructflow.notification.dto.NotificationResponse;
import com.constructflow.notification.entity.Notification;
import com.constructflow.notification.entity.NotificationType;
import com.constructflow.notification.repository.NotificationRepository;
import com.constructflow.security.CurrentUser;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.UUID;

/**
 * In-app notifications stored in PostgreSQL. Business services call {@link #notify} inside their own
 * transaction, so a notification exists only if the change that caused it was committed. No message
 * broker is needed at this scale; the frontend polls the unread count.
 */
@Service
public class NotificationService {

    private final NotificationRepository repository;
    private final Clock clock;

    public NotificationService(NotificationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public void notify(UUID organizationId, UUID recipientId, NotificationType type, String title, String message,
                       String link, String dedupeKey) {
        if (recipientId == null) {
            return;
        }
        if (dedupeKey != null && repository.existsByRecipientIdAndDedupeKey(recipientId, dedupeKey)) {
            return;
        }
        repository.save(new Notification(organizationId, recipientId, type, title, message, link, dedupeKey));
    }

    /** Notifies each recipient once, skipping the user who triggered the event. */
    @Transactional
    public void notifyAll(UUID organizationId, Collection<UUID> recipients, UUID excludeUserId, NotificationType type,
                          String title, String message, String link) {
        new LinkedHashSet<>(recipients).stream()
                .filter(Objects::nonNull)
                .filter(id -> !id.equals(excludeUserId))
                .forEach(id -> notify(organizationId, id, type, title, message, link, null));
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(CurrentUser user, boolean unreadOnly, Pageable pageable) {
        var page = unreadOnly
                ? repository.findByRecipientIdAndOrganizationIdAndReadAtIsNull(user.userId(), user.organizationId(), pageable)
                : repository.findByRecipientIdAndOrganizationId(user.userId(), user.organizationId(), pageable);
        return PageResponse.of(page, NotificationResponse::of);
    }

    @Transactional(readOnly = true)
    public long unreadCount(CurrentUser user) {
        return repository.countByRecipientIdAndOrganizationIdAndReadAtIsNull(user.userId(), user.organizationId());
    }

    @Transactional
    public NotificationResponse markRead(CurrentUser user, UUID id) {
        Notification notification = repository.findByIdAndRecipientIdAndOrganizationId(id, user.userId(), user.organizationId())
                .orElseThrow(() -> new NotFoundException("Notification"));
        notification.markRead(clock.instant());
        return NotificationResponse.of(notification);
    }

    @Transactional
    public int markAllRead(CurrentUser user) {
        return repository.markAllRead(user.userId(), user.organizationId(), clock.instant());
    }
}
