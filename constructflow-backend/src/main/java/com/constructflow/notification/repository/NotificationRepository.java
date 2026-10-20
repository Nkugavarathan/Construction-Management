package com.constructflow.notification.repository;

import com.constructflow.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByRecipientIdAndOrganizationId(UUID recipientId, UUID organizationId, Pageable pageable);

    Page<Notification> findByRecipientIdAndOrganizationIdAndReadAtIsNull(UUID recipientId, UUID organizationId,
                                                                         Pageable pageable);

    long countByRecipientIdAndOrganizationIdAndReadAtIsNull(UUID recipientId, UUID organizationId);

    Optional<Notification> findByIdAndRecipientIdAndOrganizationId(UUID id, UUID recipientId, UUID organizationId);

    boolean existsByRecipientIdAndDedupeKey(UUID recipientId, String dedupeKey);

    @Modifying
    @Query("""
            update Notification n set n.readAt = :now
            where n.recipientId = :recipientId and n.organizationId = :organizationId and n.readAt is null
            """)
    int markAllRead(@Param("recipientId") UUID recipientId, @Param("organizationId") UUID organizationId,
                    @Param("now") Instant now);
}
