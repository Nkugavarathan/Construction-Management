package com.constructflow.finance;

import com.constructflow.common.exception.ForbiddenException;
import com.constructflow.finance.service.ExpensePolicy;
import com.constructflow.finance.service.ExpenseService;
import com.constructflow.security.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** FIN-3: who may decide on an expense. */
class ExpensePolicyTest {

    private static final UUID ACTOR = UUID.randomUUID();
    private static final UUID SUBMITTER = UUID.randomUUID();
    private static final BigDecimal THRESHOLD = new BigDecimal("100000.00");

    @ParameterizedTest
    @EnumSource(Role.class)
    void nobodyApprovesTheirOwnSubmission(Role role) {
        assertThatThrownBy(() -> ExpensePolicy.assertCanDecide(role, true, ACTOR, ACTOR, BigDecimal.ONE, THRESHOLD))
                .isInstanceOf(ForbiddenException.class)
                .hasFieldOrPropertyWithValue("code", "SELF_APPROVAL");
    }

    @Test
    void financeAndAdminApproveAnyAmount() {
        BigDecimal large = new BigDecimal("99999999.99");
        assertThatCode(() -> ExpensePolicy.assertCanDecide(Role.FINANCE_OFFICER, false, ACTOR, SUBMITTER, large, THRESHOLD))
                .doesNotThrowAnyException();
        assertThatCode(() -> ExpensePolicy.assertCanDecide(Role.ADMIN, false, ACTOR, SUBMITTER, large, THRESHOLD))
                .doesNotThrowAnyException();
    }

    @Test
    void projectManagerLimitedToThresholdOnOwnProjects() {
        assertThat(ExpensePolicy.canDecide(Role.PROJECT_MANAGER, true, ACTOR, SUBMITTER, THRESHOLD, THRESHOLD)).isTrue();
        assertThatThrownBy(() -> ExpensePolicy.assertCanDecide(Role.PROJECT_MANAGER, true, ACTOR, SUBMITTER,
                new BigDecimal("100000.01"), THRESHOLD)).hasFieldOrPropertyWithValue("code", "APPROVAL_LIMIT_EXCEEDED");
        assertThatThrownBy(() -> ExpensePolicy.assertCanDecide(Role.PROJECT_MANAGER, false, ACTOR, SUBMITTER,
                BigDecimal.ONE, THRESHOLD)).hasFieldOrPropertyWithValue("code", "NOT_PROJECT_MANAGER");
    }

    @Test
    void reversalAmountsAreComparedByMagnitude() {
        assertThat(ExpensePolicy.canDecide(Role.PROJECT_MANAGER, true, ACTOR, SUBMITTER,
                new BigDecimal("-150000.00"), THRESHOLD)).isFalse();
    }

    @Test
    void otherRolesCannotDecide() {
        for (Role role : new Role[]{Role.SITE_ENGINEER, Role.STOREKEEPER, Role.VIEWER}) {
            assertThat(ExpensePolicy.canDecide(role, true, ACTOR, SUBMITTER, BigDecimal.ONE, THRESHOLD)).isFalse();
        }
    }

    @Test
    void percentUsesExactDecimalArithmetic() {
        assertThat(ExpenseService.percent(new BigDecimal("8380000.00"), new BigDecimal("185000000.00")))
                .isEqualByComparingTo("4.53");
        assertThat(ExpenseService.percent(new BigDecimal("0.10"), new BigDecimal("0.30"))).isEqualByComparingTo("33.33");
        assertThat(ExpenseService.percent(BigDecimal.ONE, BigDecimal.ZERO)).isNull();
    }
}
