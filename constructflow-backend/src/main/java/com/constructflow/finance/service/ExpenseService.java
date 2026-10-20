package com.constructflow.finance.service;

import com.constructflow.audit.entity.AuditAction;
import com.constructflow.audit.service.AuditService;
import com.constructflow.common.exception.BadRequestException;
import com.constructflow.common.exception.ConflictException;
import com.constructflow.common.exception.ForbiddenException;
import com.constructflow.common.exception.NotFoundException;
import com.constructflow.common.exception.OptimisticLock;
import com.constructflow.common.persistence.OrgSequenceService;
import com.constructflow.common.response.PageResponse;
import com.constructflow.finance.dto.FinanceDtos.ApprovalHistoryItem;
import com.constructflow.finance.dto.FinanceDtos.CategorySummary;
import com.constructflow.finance.dto.FinanceDtos.CreateExpenseRequest;
import com.constructflow.finance.dto.FinanceDtos.DecisionRequest;
import com.constructflow.finance.dto.FinanceDtos.ExpenseResponse;
import com.constructflow.finance.dto.FinanceDtos.FinancialSummaryResponse;
import com.constructflow.finance.entity.BudgetAllocation;
import com.constructflow.finance.entity.BudgetCategory;
import com.constructflow.finance.entity.Expense;
import com.constructflow.finance.entity.ExpenseApproval;
import com.constructflow.finance.entity.ExpenseStatus;
import com.constructflow.finance.mapper.ExpenseMapper;
import com.constructflow.finance.repository.ExpenseApprovalRepository;
import com.constructflow.finance.repository.ExpenseRepository;
import com.constructflow.notification.entity.NotificationType;
import com.constructflow.notification.service.NotificationService;
import com.constructflow.organization.entity.Organization;
import com.constructflow.organization.service.MemberService;
import com.constructflow.organization.service.OrganizationService;
import com.constructflow.project.entity.Project;
import com.constructflow.project.entity.ProjectRole;
import com.constructflow.project.service.ProjectAccessPolicy;
import com.constructflow.project.service.ProjectMemberService;
import com.constructflow.security.CurrentUser;
import com.constructflow.security.Role;
import com.constructflow.user.entity.User;
import com.constructflow.user.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Expense workflow. Every state change writes the expense, an approval-history row and an audit event
 * in one transaction: either all three are stored or none is.
 */
@Service
public class ExpenseService {

    private final ExpenseRepository expenses;
    private final ExpenseApprovalRepository approvals;
    private final BudgetCategoryService categories;
    private final BudgetService budgets;
    private final ProjectAccessPolicy access;
    private final ProjectMemberService projectMembers;
    private final OrganizationService organizations;
    private final MemberService members;
    private final OrgSequenceService sequences;
    private final NotificationService notifications;
    private final AuditService auditService;
    private final UserRepository users;
    private final Clock clock;

    public ExpenseService(ExpenseRepository expenses, ExpenseApprovalRepository approvals,
                          BudgetCategoryService categories, BudgetService budgets, ProjectAccessPolicy access,
                          ProjectMemberService projectMembers, OrganizationService organizations, MemberService members,
                          OrgSequenceService sequences, NotificationService notifications, AuditService auditService,
                          UserRepository users, Clock clock) {
        this.expenses = expenses;
        this.approvals = approvals;
        this.categories = categories;
        this.budgets = budgets;
        this.access = access;
        this.projectMembers = projectMembers;
        this.organizations = organizations;
        this.members = members;
        this.sequences = sequences;
        this.notifications = notifications;
        this.auditService = auditService;
        this.users = users;
        this.clock = clock;
    }

    @Transactional
    public ExpenseResponse create(CurrentUser user, UUID projectId, CreateExpenseRequest request) {
        Project project = access.requireReadable(user, projectId);
        access.requireOpen(project);
        BudgetCategory category = categories.requireActive(user.organizationId(), request.categoryId());
        requireNotFuture(user, request.expenseDate());
        String number = sequences.next(user.organizationId(), "EXPENSE", "EXP");
        Expense expense = expenses.save(new Expense(project, category, number, request.description().trim(),
                blankToNull(request.vendorName()), request.amount(), request.expenseDate(), currentUser(user)));
        return toResponse(user, expense);
    }

    @Transactional
    public ExpenseResponse updateDraft(CurrentUser user, UUID id, CreateExpenseRequest request) {
        Expense expense = load(user, id);
        requireCreator(user, expense);
        access.requireOpen(expense.getProject());
        BudgetCategory category = categories.requireActive(user.organizationId(), request.categoryId());
        requireNotFuture(user, request.expenseDate());
        expense.editDraft(category, request.description().trim(), blankToNull(request.vendorName()),
                request.amount(), request.expenseDate());
        expenses.flush();
        return toResponse(user, expense);
    }

    @Transactional
    public void deleteDraft(CurrentUser user, UUID id) {
        Expense expense = load(user, id);
        requireCreator(user, expense);
        expense.requireStatus(ExpenseStatus.DRAFT, "deleted");
        expenses.delete(expense);
    }

    @Transactional
    public ExpenseResponse submit(CurrentUser user, UUID id) {
        Expense expense = load(user, id);
        requireCreator(user, expense);
        access.requireOpen(expense.getProject());
        User submitter = currentUser(user);
        expense.submit(submitter, clock.instant());
        approvals.save(new ExpenseApproval(expense, ExpenseApproval.Action.SUBMITTED, submitter, null));
        auditService.record(user, AuditAction.EXPENSE_SUBMITTED, "Expense", expense.getId(), expense.getProject().getId(),
                "Submitted %s for %s".formatted(expense.getNumber(), expense.getAmount()),
                Map.of("amount", expense.getAmount(), "category", expense.getCategory().getName()));
        notifyApprovers(user, expense);
        return toResponse(user, expense);
    }

    @Transactional
    public ExpenseResponse approve(CurrentUser user, UUID id, DecisionRequest request) {
        return decide(user, id, request, true);
    }

    @Transactional
    public ExpenseResponse reject(CurrentUser user, UUID id, DecisionRequest request) {
        if (request.comment() == null || request.comment().isBlank()) {
            throw new BadRequestException("COMMENT_REQUIRED", "comment", "A reason is required when rejecting.");
        }
        return decide(user, id, request, false);
    }

    private ExpenseResponse decide(CurrentUser user, UUID id, DecisionRequest request, boolean approve) {
        Expense expense = load(user, id);
        OptimisticLock.check(request.version(), expense.getVersion(), "Expense");
        Organization organization = organizations.load(user.organizationId());
        ExpensePolicy.assertCanDecide(user.role(), access.isProjectManager(user, expense.getProject()), user.userId(),
                expense.getSubmittedBy() != null ? expense.getSubmittedBy().getId() : null,
                expense.getAmount(), organization.getExpenseApprovalThreshold());

        User approver = currentUser(user);
        if (approve) {
            expense.approve(approver, clock.instant());
        } else {
            expense.reject(approver, clock.instant());
        }
        approvals.save(new ExpenseApproval(expense, approve ? ExpenseApproval.Action.APPROVED : ExpenseApproval.Action.REJECTED,
                approver, blankToNull(request.comment())));
        auditService.record(user, approve ? AuditAction.EXPENSE_APPROVED : AuditAction.EXPENSE_REJECTED, "Expense",
                expense.getId(), expense.getProject().getId(),
                "%s %s (%s)".formatted(approve ? "Approved" : "Rejected", expense.getNumber(), expense.getAmount()),
                Map.of("before", ExpenseStatus.SUBMITTED, "after", expense.getStatus(), "amount", expense.getAmount()));
        // Flush now so a concurrent decision surfaces as an optimistic-lock conflict inside this transaction.
        expenses.flush();
        notifications.notify(user.organizationId(), expense.getSubmittedBy().getId(), NotificationType.EXPENSE_DECIDED,
                "Expense %s %s".formatted(expense.getNumber(), approve ? "approved" : "rejected"),
                request.comment(), "/finance/expenses?expense=" + expense.getId(), null);
        return toResponse(user, expense);
    }

    /** Corrects an approved expense without editing it (ADR-0005). The reversal goes through approval. */
    @Transactional
    public ExpenseResponse reverse(CurrentUser user, UUID id, String reason) {
        if (!user.isAdmin() && user.role() != Role.FINANCE_OFFICER) {
            throw new ForbiddenException("Only finance officers and administrators can reverse expenses.");
        }
        Expense original = load(user, id);
        original.requireStatus(ExpenseStatus.APPROVED, "reversed");
        if (original.isReversal()) {
            throw new ConflictException("CANNOT_REVERSE_REVERSAL", "A reversal cannot itself be reversed.");
        }
        if (expenses.existsByReversesExpenseId(original.getId())) {
            throw new ConflictException("ALREADY_REVERSED", "This expense has already been reversed.");
        }
        String number = sequences.next(user.organizationId(), "EXPENSE", "EXP");
        User requester = currentUser(user);
        Expense reversal = expenses.save(Expense.reversalOf(original, number, reason.trim(),
                organizations.today(user.organizationId()), requester, clock.instant()));
        approvals.save(new ExpenseApproval(reversal, ExpenseApproval.Action.SUBMITTED, requester, reason.trim()));
        auditService.record(user, AuditAction.EXPENSE_REVERSAL_CREATED, "Expense", reversal.getId(),
                original.getProject().getId(), "Reversal %s created for %s".formatted(number, original.getNumber()),
                Map.of("originalExpense", original.getNumber(), "amount", reversal.getAmount(), "reason", reason.trim()));
        notifyApprovers(user, reversal);
        return toResponse(user, reversal);
    }

    @Transactional(readOnly = true)
    public ExpenseResponse get(CurrentUser user, UUID id) {
        return toResponse(user, load(user, id));
    }

    @Transactional(readOnly = true)
    public List<ApprovalHistoryItem> history(CurrentUser user, UUID id) {
        Expense expense = load(user, id);
        return approvals.findByExpenseIdOrderByCreatedAtAsc(expense.getId()).stream()
                .map(a -> new ApprovalHistoryItem(a.getAction(),
                        com.constructflow.user.dto.UserSummary.of(a.getActor()), a.getComment(), a.getCreatedAt()))
                .toList();
    }

    /**
     * Expense list with visibility rules applied in SQL: admins and finance see the whole organization,
     * project managers see the projects they manage plus their own, everyone else only their own.
     */
    @Transactional(readOnly = true)
    public PageResponse<ExpenseResponse> search(CurrentUser user, UUID projectId, ExpenseStatus status,
                                                UUID categoryId, LocalDate from, LocalDate to, boolean mineOnly,
                                                Pageable pageable) {
        if (projectId != null) {
            access.requireReadable(user, projectId);
        }
        Set<UUID> managed = new HashSet<>(access.managedProjectIds(user));
        boolean orgWide = user.isAdmin() || user.role() == Role.FINANCE_OFFICER;

        Specification<Expense> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("organizationId"), user.organizationId()));
            if (projectId != null) {
                predicates.add(cb.equal(root.get("project").get("id"), projectId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("expenseDate"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("expenseDate"), to));
            }
            Predicate own = cb.equal(root.get("createdBy").get("id"), user.userId());
            if (mineOnly) {
                predicates.add(own);
            } else if (!orgWide) {
                predicates.add(managed.isEmpty() ? own : cb.or(own, root.get("project").get("id").in(managed)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Page<Expense> page = expenses.findAll(spec, pageable);
        BigDecimal threshold = organizations.load(user.organizationId()).getExpenseApprovalThreshold();
        return PageResponse.of(page, page.getContent().stream()
                .map(e -> ExpenseMapper.toResponse(e, canDecide(user, e, managed.contains(e.getProject().getId()), threshold)))
                .toList());
    }

    @Transactional(readOnly = true)
    public FinancialSummaryResponse financialSummary(CurrentUser user, UUID projectId) {
        Project project = access.requireReadable(user, projectId);
        if (!access.canViewFinance(user, project)) {
            throw new ForbiddenException("You cannot view this project's finances.");
        }
        Organization organization = organizations.load(user.organizationId());
        List<UUID> ids = List.of(projectId);
        BigDecimal approved = expenses.total(ids, ExpenseStatus.APPROVED);
        BigDecimal pending = expenses.total(ids, ExpenseStatus.SUBMITTED);
        BigDecimal budget = project.getApprovedBudget();

        Map<UUID, CategoryAccumulator> byCategory = new LinkedHashMap<>();
        for (BudgetAllocation allocation : budgets.allocationsFor(projectId)) {
            byCategory.computeIfAbsent(allocation.getCategory().getId(),
                    k -> new CategoryAccumulator(allocation.getCategory().getName())).allocated = allocation.getAmount();
        }
        for (ExpenseRepository.CategoryTotal row : expenses.totalsByCategory(ids,
                List.of(ExpenseStatus.APPROVED, ExpenseStatus.SUBMITTED))) {
            CategoryAccumulator acc = byCategory.computeIfAbsent(row.getCategoryId(),
                    k -> new CategoryAccumulator(row.getCategoryName()));
            if (row.getStatus() == ExpenseStatus.APPROVED) {
                acc.approved = row.getTotal();
            } else {
                acc.pending = row.getTotal();
            }
        }
        List<CategorySummary> categoryRows = byCategory.entrySet().stream()
                .map(e -> new CategorySummary(e.getKey(), e.getValue().name, e.getValue().allocated, e.getValue().approved,
                        e.getValue().pending, e.getValue().allocated.subtract(e.getValue().approved)))
                .toList();

        return new FinancialSummaryResponse(projectId, organization.getCurrencyCode(), budget, approved, pending,
                budget.subtract(approved), percent(approved, budget), percent(approved.add(pending), budget), categoryRows);
    }

    /** Approved spend per project for list screens, as one grouped query. */
    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> approvedSpendByProject(Collection<UUID> projectIds) {
        Map<UUID, BigDecimal> result = new HashMap<>();
        if (projectIds.isEmpty()) {
            return result;
        }
        expenses.totalsByProject(projectIds, ExpenseStatus.APPROVED)
                .forEach(row -> result.put(row.getProjectId(), row.getTotal()));
        return result;
    }

    /** Submitted expenses this user could approve right now (their approval queue size). */
    @Transactional(readOnly = true)
    public long countDecidable(CurrentUser user) {
        if (!user.has(com.constructflow.security.Permission.EXPENSE_APPROVE)) {
            return 0;
        }
        return search(user, null, ExpenseStatus.SUBMITTED, null, null, null, false,
                org.springframework.data.domain.PageRequest.of(0, 500)).content().stream()
                .filter(ExpenseResponse::canDecide).count();
    }

    /** Approved spend per category across projects, largest first (for the dashboard chart). */
    @Transactional(readOnly = true)
    public List<CategorySummary> approvedByCategory(Collection<UUID> projectIds) {
        if (projectIds.isEmpty()) {
            return List.of();
        }
        return expenses.totalsByCategory(projectIds, List.of(ExpenseStatus.APPROVED)).stream()
                .map(r -> new CategorySummary(r.getCategoryId(), r.getCategoryName(), BigDecimal.ZERO, r.getTotal(),
                        BigDecimal.ZERO, BigDecimal.ZERO))
                .sorted((a, b) -> b.approved().compareTo(a.approved()))
                .toList();
    }

    @Transactional(readOnly = true)
    public BigDecimal totalForProjects(Collection<UUID> projectIds, ExpenseStatus status) {
        return projectIds.isEmpty() ? BigDecimal.ZERO : expenses.total(projectIds, status);
    }

    @Transactional(readOnly = true)
    public long countPending(UUID projectId) {
        return expenses.countByProjectIdAndStatus(projectId, ExpenseStatus.SUBMITTED);
    }

    public static BigDecimal percent(BigDecimal part, BigDecimal whole) {
        if (whole == null || whole.signum() == 0) {
            return null;
        }
        return part.multiply(BigDecimal.valueOf(100)).divide(whole, 2, RoundingMode.HALF_EVEN);
    }

    private void notifyApprovers(CurrentUser user, Expense expense) {
        Organization organization = organizations.load(user.organizationId());
        List<UUID> recipients = new ArrayList<>(members.activeUserIdsWithRoles(user.organizationId(),
                List.of(Role.FINANCE_OFFICER, Role.ADMIN)));
        if (expense.getAmount().abs().compareTo(organization.getExpenseApprovalThreshold()) <= 0) {
            recipients.addAll(projectMembers.userIdsWithRole(expense.getProject().getId(), ProjectRole.MANAGER));
        }
        notifications.notifyAll(user.organizationId(), recipients, user.userId(), NotificationType.EXPENSE_SUBMITTED,
                "Expense %s awaiting approval".formatted(expense.getNumber()),
                "%s · %s %s".formatted(expense.getProject().getCode(), organization.getCurrencyCode(), expense.getAmount()),
                "/finance/expenses?expense=" + expense.getId());
    }

    private ExpenseResponse toResponse(CurrentUser user, Expense expense) {
        BigDecimal threshold = organizations.load(user.organizationId()).getExpenseApprovalThreshold();
        return ExpenseMapper.toResponse(expense,
                canDecide(user, expense, access.isProjectManager(user, expense.getProject()), threshold));
    }

    private static boolean canDecide(CurrentUser user, Expense expense, boolean managesProject, BigDecimal threshold) {
        return expense.getStatus() == ExpenseStatus.SUBMITTED
                && user.has(com.constructflow.security.Permission.EXPENSE_APPROVE)
                && ExpensePolicy.canDecide(user.role(), managesProject, user.userId(),
                expense.getSubmittedBy() != null ? expense.getSubmittedBy().getId() : null,
                expense.getAmount(), threshold);
    }

    /** Loads an expense the user may see; anything else in the organization is reported as not found. */
    private Expense load(CurrentUser user, UUID id) {
        Expense expense = expenses.findByIdAndOrganizationId(id, user.organizationId())
                .orElseThrow(() -> new NotFoundException("Expense"));
        boolean visible = user.isAdmin() || user.role() == Role.FINANCE_OFFICER
                || expense.getCreatedBy().getId().equals(user.userId())
                || access.isProjectManager(user, expense.getProject());
        if (!visible) {
            throw new NotFoundException("Expense");
        }
        return expense;
    }

    private static void requireCreator(CurrentUser user, Expense expense) {
        if (!expense.getCreatedBy().getId().equals(user.userId())) {
            throw new ForbiddenException("Only the person who recorded this expense can change it.");
        }
    }

    private void requireNotFuture(CurrentUser user, LocalDate date) {
        if (date.isAfter(organizations.today(user.organizationId()))) {
            throw new BadRequestException("FUTURE_DATE", "expenseDate", "Expense date cannot be in the future.");
        }
    }

    private User currentUser(CurrentUser user) {
        return users.getReferenceById(user.userId());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static final class CategoryAccumulator {
        private final String name;
        private BigDecimal allocated = BigDecimal.ZERO;
        private BigDecimal approved = BigDecimal.ZERO;
        private BigDecimal pending = BigDecimal.ZERO;

        private CategoryAccumulator(String name) {
            this.name = name;
        }
    }
}
