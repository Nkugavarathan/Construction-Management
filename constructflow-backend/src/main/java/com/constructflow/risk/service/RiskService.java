package com.constructflow.risk.service;

import com.constructflow.audit.entity.AuditAction;
import com.constructflow.audit.service.AuditService;
import com.constructflow.common.exception.BadRequestException;
import com.constructflow.common.exception.ForbiddenException;
import com.constructflow.common.exception.NotFoundException;
import com.constructflow.common.exception.OptimisticLock;
import com.constructflow.common.response.PageResponse;
import com.constructflow.notification.entity.NotificationType;
import com.constructflow.notification.service.NotificationService;
import com.constructflow.project.entity.Project;
import com.constructflow.project.service.ProjectAccessPolicy;
import com.constructflow.risk.dto.RiskDtos.RiskRequest;
import com.constructflow.risk.dto.RiskDtos.RiskResponse;
import com.constructflow.risk.entity.Risk;
import com.constructflow.risk.repository.RiskRepository;
import com.constructflow.security.CurrentUser;
import com.constructflow.user.entity.User;
import com.constructflow.user.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Project risk register. Score = likelihood x impact on a 5x5 matrix (1-25), banded Low 1-4,
 * Medium 5-9, High 10-16, Critical 17-25.
 */
@Service
public class RiskService {

    private final RiskRepository risks;
    private final ProjectAccessPolicy access;
    private final NotificationService notifications;
    private final AuditService auditService;
    private final UserRepository users;

    public RiskService(RiskRepository risks, ProjectAccessPolicy access, NotificationService notifications,
                       AuditService auditService, UserRepository users) {
        this.risks = risks;
        this.access = access;
        this.notifications = notifications;
        this.auditService = auditService;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public PageResponse<RiskResponse> list(CurrentUser user, UUID projectId, Risk.Status status, Pageable pageable) {
        access.requireReadable(user, projectId);
        return PageResponse.of(risks.search(projectId, status, pageable), RiskResponse::of);
    }

    @Transactional
    public RiskResponse create(CurrentUser user, UUID projectId, RiskRequest request) {
        Project project = access.requireManageable(user, projectId);
        access.requireOpen(project);
        Risk risk = new Risk(project, request.title().trim(), users.getReferenceById(user.userId()));
        User owner = resolveOwner(project, request.ownerId());
        risk.update(request.title().trim(), blank(request.description()), request.likelihood(), request.impact(),
                blank(request.mitigation()), owner, request.dueDate(), Risk.Status.OPEN);
        risk = risks.save(risk);
        auditService.record(user, AuditAction.RISK_CREATED, "Risk", risk.getId(), projectId,
                "Risk '%s' scored %d (%s)".formatted(risk.getTitle(), risk.getScore(), risk.getLevel()),
                Map.of("likelihood", risk.getLikelihood(), "impact", risk.getImpact()));
        notifyOwner(user, risk, owner);
        risks.flush();
        return RiskResponse.of(risk);
    }

    @Transactional
    public RiskResponse update(CurrentUser user, UUID riskId, RiskRequest request) {
        Risk risk = risks.findInOrganization(riskId, user.organizationId()).orElseThrow(() -> new NotFoundException("Risk"));
        Project project = access.requireReadable(user, risk.getProject().getId());
        if (!access.canManage(user, project)) {
            throw new ForbiddenException("NOT_PROJECT_MANAGER", "Only the project's managers can update risks.");
        }
        OptimisticLock.check(request.version(), risk.getVersion(), "Risk");
        UUID previousOwner = risk.getOwner() != null ? risk.getOwner().getId() : null;
        int previousScore = risk.getScore();
        User owner = resolveOwner(project, request.ownerId());
        risk.update(request.title().trim(), blank(request.description()), request.likelihood(), request.impact(),
                blank(request.mitigation()), owner, request.dueDate(),
                request.status() != null ? request.status() : risk.getStatus());
        risks.flush();
        auditService.record(user, AuditAction.RISK_UPDATED, "Risk", risk.getId(), project.getId(),
                "Risk '%s' updated (score %d → %d, %s)".formatted(risk.getTitle(), previousScore, risk.getScore(), risk.getStatus()),
                Map.of("scoreBefore", previousScore, "scoreAfter", risk.getScore(), "status", risk.getStatus()));
        if (owner != null && !Objects.equals(previousOwner, owner.getId())) {
            notifyOwner(user, risk, owner);
        }
        return RiskResponse.of(risk);
    }

    @Transactional(readOnly = true)
    public Map<Risk.Level, Long> openRiskDistribution(Collection<UUID> projectIds) {
        Map<Risk.Level, Long> distribution = new EnumMap<>(Risk.Level.class);
        for (Risk.Level level : Risk.Level.values()) {
            distribution.put(level, 0L);
        }
        if (!projectIds.isEmpty()) {
            risks.openScores(projectIds).forEach(score -> distribution.merge(Risk.Level.of(score), 1L, Long::sum));
        }
        return distribution;
    }

    @Transactional(readOnly = true)
    public List<RiskResponse> topOpen(UUID projectId, int limit) {
        return risks.openByScore(projectId, PageRequest.of(0, limit)).stream().map(RiskResponse::of).toList();
    }

    private User resolveOwner(Project project, UUID ownerId) {
        if (ownerId == null) {
            return null;
        }
        if (!access.isUserOnProject(project.getId(), ownerId)) {
            throw new BadRequestException("OWNER_NOT_MEMBER", "ownerId", "The risk owner must be a project member.");
        }
        return users.getReferenceById(ownerId);
    }

    private void notifyOwner(CurrentUser user, Risk risk, User owner) {
        if (owner != null && !owner.getId().equals(user.userId())) {
            notifications.notify(user.organizationId(), owner.getId(), NotificationType.RISK_ASSIGNED,
                    "Risk assigned: " + risk.getTitle(), "%s risk, score %d".formatted(risk.getLevel(), risk.getScore()),
                    "/projects/%s/risks".formatted(risk.getProject().getId()), null);
        }
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
