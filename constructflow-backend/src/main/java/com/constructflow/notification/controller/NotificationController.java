package com.constructflow.notification.controller;

import com.constructflow.common.response.PageResponse;
import com.constructflow.notification.dto.NotificationResponse;
import com.constructflow.notification.service.NotificationService;
import com.constructflow.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/** A user's own notifications. No permission needed beyond being logged in; queries are scoped to the recipient. */
@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<NotificationResponse> list(@AuthenticationPrincipal CurrentUser user,
                                                   @RequestParam(defaultValue = "false") boolean unreadOnly,
                                                   @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.list(user, unreadOnly, pageable);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal CurrentUser user) {
        return Map.of("count", service.unreadCount(user));
    }

    @PostMapping("/{id}/read")
    public NotificationResponse markRead(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
        return service.markRead(user, id);
    }

    @PostMapping("/read-all")
    public Map<String, Integer> markAllRead(@AuthenticationPrincipal CurrentUser user) {
        return Map.of("updated", service.markAllRead(user));
    }
}
