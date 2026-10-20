package com.constructflow.support;

import com.constructflow.auth.dto.AuthDtos.RegisterRequest;
import com.constructflow.auth.service.AuthService;
import com.constructflow.auth.service.ClientInfo;
import com.constructflow.finance.dto.FinanceDtos.BudgetCategoryResponse;
import com.constructflow.finance.service.BudgetCategoryService;
import com.constructflow.organization.dto.OrganizationDtos.CreateMemberRequest;
import com.constructflow.organization.repository.OrganizationMembershipRepository;
import com.constructflow.organization.service.MemberService;
import com.constructflow.project.dto.ProjectDtos.AddProjectMemberRequest;
import com.constructflow.project.dto.ProjectDtos.CreateProjectRequest;
import com.constructflow.project.dto.ProjectDtos.ProjectResponse;
import com.constructflow.project.dto.ProjectDtos.TransitionProjectRequest;
import com.constructflow.project.entity.ProjectRole;
import com.constructflow.project.entity.ProjectStatus;
import com.constructflow.project.service.ProjectMemberService;
import com.constructflow.project.service.ProjectService;
import com.constructflow.security.CurrentUser;
import com.constructflow.security.JwtService;
import com.constructflow.security.Role;
import com.constructflow.user.entity.User;
import com.constructflow.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * Builds isolated test data through the real services. Every call uses unique names and emails, so
 * tests never depend on each other's data and the database never needs cleaning between tests.
 */
public class TestFixtures {

    public static final String PASSWORD = "Correct-Horse-42";

    private final AuthService authService;
    private final MemberService memberService;
    private final ProjectService projectService;
    private final ProjectMemberService projectMemberService;
    private final BudgetCategoryService categoryService;
    private final UserRepository users;
    private final OrganizationMembershipRepository memberships;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TransactionTemplate tx;

    public TestFixtures(AuthService authService, MemberService memberService, ProjectService projectService,
                        ProjectMemberService projectMemberService, BudgetCategoryService categoryService,
                        UserRepository users, OrganizationMembershipRepository memberships,
                        PasswordEncoder passwordEncoder, JwtService jwtService, TransactionTemplate tx) {
        this.authService = authService;
        this.memberService = memberService;
        this.projectService = projectService;
        this.projectMemberService = projectMemberService;
        this.categoryService = categoryService;
        this.users = users;
        this.memberships = memberships;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.tx = tx;
    }

    /** An organization with one active user per role. */
    public Org organization() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String adminEmail = "admin-" + suffix + "@test.dev";
        UUID orgId = authService.register(new RegisterRequest("Org " + suffix, "Admin " + suffix, adminEmail, PASSWORD),
                new ClientInfo("10.0.0." + (int) (Math.random() * 250), "test")).me().activeOrganization().id();
        Map<Role, CurrentUser> byRole = new EnumMap<>(Role.class);
        byRole.put(Role.ADMIN, as(adminEmail, orgId));
        for (Role role : Role.values()) {
            if (role != Role.ADMIN) {
                String email = role.name().toLowerCase().replace('_', '-') + "-" + suffix + "@test.dev";
                memberService.create(byRole.get(Role.ADMIN), new CreateMemberRequest(role.name() + " " + suffix, email, role, PASSWORD));
                tx.executeWithoutResult(s -> users.findByEmail(email).orElseThrow().changePassword(passwordEncoder.encode(PASSWORD)));
                byRole.put(role, as(email, orgId));
            }
        }
        return new Org(orgId, byRole);
    }

    /** An ACTIVE project managed by the org's project manager, with the site engineer and viewer as members. */
    public ProjectResponse activeProject(Org org, BigDecimal budget) {
        CurrentUser pm = org.user(Role.PROJECT_MANAGER);
        ProjectResponse project = projectService.create(pm, new CreateProjectRequest(
                "P-" + UUID.randomUUID().toString().substring(0, 6), "Test project", null, "Client", null, "Site",
                LocalDate.now().minusDays(10), LocalDate.now().plusDays(100), budget, null));
        projectMemberService.add(pm, project.id(), new AddProjectMemberRequest(org.user(Role.SITE_ENGINEER).userId(), ProjectRole.MEMBER));
        projectMemberService.add(pm, project.id(), new AddProjectMemberRequest(org.user(Role.VIEWER).userId(), ProjectRole.MEMBER));
        return projectService.transition(pm, project.id(), new TransitionProjectRequest(ProjectStatus.ACTIVE, null, project.version()));
    }

    public UUID category(Org org, String name) {
        return categoryService.list(org.user(Role.ADMIN)).stream()
                .filter(c -> c.name().equals(name)).map(BudgetCategoryResponse::id).findFirst().orElseThrow();
    }

    public String token(CurrentUser user) {
        return jwtService.issue(user.userId(), user.organizationId()).value();
    }

    /** Adds a member with the given role and skips the forced password change. */
    public CurrentUser addMember(Org org, Role role) {
        String email = role.name().toLowerCase().replace('_', '-') + "-" + UUID.randomUUID().toString().substring(0, 8) + "@test.dev";
        memberService.create(org.user(Role.ADMIN), new CreateMemberRequest("Extra " + role, email, role, PASSWORD));
        tx.executeWithoutResult(s -> users.findByEmail(email).orElseThrow().changePassword(passwordEncoder.encode(PASSWORD)));
        return as(email, org.id());
    }

    public CurrentUser as(String email, UUID organizationId) {
        User user = users.findByEmail(email).orElseThrow();
        Role role = memberships.findMembership(user.getId(), organizationId).orElseThrow().getRole();
        return new CurrentUser(user.getId(), organizationId, role, user.getEmail(), user.getFullName(), false);
    }

    public record Org(UUID id, Map<Role, CurrentUser> users) {

        public CurrentUser user(Role role) {
            return users.get(role);
        }
    }
}
