package com.constructflow.finance.service;

import com.constructflow.common.exception.ForbiddenException;
import com.constructflow.security.Role;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Who may approve or reject a submitted expense. Pure function of its inputs (no database, no Spring),
 * so every branch is covered by fast unit tests.
 *
 * <ol>
 *   <li>Segregation of duties: nobody decides on an expense they submitted, whatever their role.</li>
 *   <li>Administrators and finance officers may decide any amount.</li>
 *   <li>Project managers may decide expenses on projects they manage, up to the organization's threshold.</li>
 *   <li>Everyone else may not decide.</li>
 * </ol>
 */
public final class ExpensePolicy {

    private ExpensePolicy() {
    }

    public static void assertCanDecide(Role role, boolean managesProject, UUID actorId, UUID submitterId,
                                       BigDecimal amount, BigDecimal threshold) {
        if (Objects.equals(actorId, submitterId)) {
            throw new ForbiddenException("SELF_APPROVAL", "You cannot approve or reject an expense you submitted.");
        }
        if (role == Role.ADMIN || role == Role.FINANCE_OFFICER) {
            return;
        }
        if (role == Role.PROJECT_MANAGER) {
            if (!managesProject) {
                throw new ForbiddenException("NOT_PROJECT_MANAGER", "You can only decide expenses on projects you manage.");
            }
            if (amount.abs().compareTo(threshold) > 0) {
                throw new ForbiddenException("APPROVAL_LIMIT_EXCEEDED",
                        "Expenses above %s need approval from finance or an administrator.".formatted(threshold));
            }
            return;
        }
        throw new ForbiddenException("You cannot approve expenses.");
    }

    public static boolean canDecide(Role role, boolean managesProject, UUID actorId, UUID submitterId,
                                    BigDecimal amount, BigDecimal threshold) {
        try {
            assertCanDecide(role, managesProject, actorId, submitterId, amount, threshold);
            return true;
        } catch (ForbiddenException ex) {
            return false;
        }
    }
}
