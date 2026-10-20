package com.constructflow.config;

import com.constructflow.auth.dto.AuthDtos.RegisterRequest;
import com.constructflow.auth.service.AuthService;
import com.constructflow.auth.service.ClientInfo;
import com.constructflow.finance.dto.FinanceDtos.AllocationItem;
import com.constructflow.finance.dto.FinanceDtos.BudgetCategoryResponse;
import com.constructflow.finance.dto.FinanceDtos.CreateExpenseRequest;
import com.constructflow.finance.dto.FinanceDtos.DecisionRequest;
import com.constructflow.finance.dto.FinanceDtos.ExpenseResponse;
import com.constructflow.finance.service.BudgetCategoryService;
import com.constructflow.finance.service.BudgetService;
import com.constructflow.finance.service.ExpenseService;
import com.constructflow.inventory.dto.InventoryDtos.CreateMaterialRequest;
import com.constructflow.inventory.dto.InventoryDtos.IssueRequest;
import com.constructflow.inventory.dto.InventoryDtos.MaterialResponse;
import com.constructflow.inventory.dto.InventoryDtos.ReceiptRequest;
import com.constructflow.inventory.entity.MaterialUnit;
import com.constructflow.inventory.service.InventoryService;
import com.constructflow.inventory.service.MaterialService;
import com.constructflow.organization.dto.OrganizationDtos.CreateMemberRequest;
import com.constructflow.organization.entity.OrganizationMembership;
import com.constructflow.organization.repository.OrganizationMembershipRepository;
import com.constructflow.organization.repository.OrganizationRepository;
import com.constructflow.organization.service.MemberService;
import com.constructflow.procurement.dto.ProcurementDtos.PurchaseOrderLine;
import com.constructflow.procurement.dto.ProcurementDtos.PurchaseOrderRequest;
import com.constructflow.procurement.dto.ProcurementDtos.PurchaseOrderResponse;
import com.constructflow.procurement.dto.ProcurementDtos.ReceiveLine;
import com.constructflow.procurement.dto.ProcurementDtos.ReceiveRequest;
import com.constructflow.procurement.dto.ProcurementDtos.StatusChangeRequest;
import com.constructflow.procurement.dto.ProcurementDtos.SupplierRequest;
import com.constructflow.procurement.service.PurchaseOrderService;
import com.constructflow.procurement.service.SupplierService;
import com.constructflow.project.dto.ProjectDtos.AddProjectMemberRequest;
import com.constructflow.project.dto.ProjectDtos.CreateProjectRequest;
import com.constructflow.project.dto.ProjectDtos.ProjectResponse;
import com.constructflow.project.dto.ProjectDtos.TransitionProjectRequest;
import com.constructflow.project.entity.ProjectRole;
import com.constructflow.project.entity.ProjectStatus;
import com.constructflow.project.service.ProjectMemberService;
import com.constructflow.project.service.ProjectService;
import com.constructflow.risk.dto.RiskDtos.RiskRequest;
import com.constructflow.risk.service.RiskService;
import com.constructflow.safety.dto.SafetyDtos.ReportIncidentRequest;
import com.constructflow.safety.entity.SafetyIncident;
import com.constructflow.safety.service.SafetyService;
import com.constructflow.security.CurrentUser;
import com.constructflow.security.Role;
import com.constructflow.task.dto.TaskDtos.MilestoneRequest;
import com.constructflow.task.dto.TaskDtos.MilestoneResponse;
import com.constructflow.task.dto.TaskDtos.SiteReportRequest;
import com.constructflow.task.dto.TaskDtos.TaskRequest;
import com.constructflow.task.dto.TaskDtos.TaskResponse;
import com.constructflow.task.dto.TaskDtos.TaskTransitionRequest;
import com.constructflow.task.entity.TaskPriority;
import com.constructflow.task.entity.TaskStatus;
import com.constructflow.task.service.MilestoneService;
import com.constructflow.task.service.SiteReportService;
import com.constructflow.task.service.TaskService;
import com.constructflow.user.entity.User;
import com.constructflow.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Demo data for local development and portfolio demos. Runs only when {@code app.seed.enabled=true}
 * (the {@code local} profile) and only on an empty database. Data is created through the real
 * services, so every business rule and audit event applies exactly as in normal use.
 *
 * <p>All demo accounts use the password {@value #DEMO_PASSWORD}. Never enable this in production.
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DevDataSeeder implements ApplicationRunner {

    static final String DEMO_PASSWORD = "ConstructFlow#2026";
    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);
    private static final ClientInfo SEED_CLIENT = new ClientInfo("127.0.0.1", "dev-seeder");

    private final AuthService auth;
    private final MemberService members;
    private final ProjectService projects;
    private final ProjectMemberService projectMembers;
    private final TaskService tasks;
    private final MilestoneService milestones;
    private final SiteReportService siteReports;
    private final BudgetCategoryService categories;
    private final BudgetService budgets;
    private final ExpenseService expenses;
    private final MaterialService materials;
    private final InventoryService inventory;
    private final SupplierService suppliers;
    private final PurchaseOrderService purchaseOrders;
    private final RiskService risks;
    private final SafetyService safety;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transactions;

    public DevDataSeeder(AuthService auth, MemberService members, ProjectService projects,
                         ProjectMemberService projectMembers, TaskService tasks, MilestoneService milestones,
                         SiteReportService siteReports, BudgetCategoryService categories, BudgetService budgets,
                         ExpenseService expenses, MaterialService materials, InventoryService inventory,
                         SupplierService suppliers, PurchaseOrderService purchaseOrders, RiskService risks,
                         SafetyService safety, OrganizationRepository organizationRepository,
                         OrganizationMembershipRepository membershipRepository, UserRepository userRepository,
                         PasswordEncoder passwordEncoder, TransactionTemplate transactions) {
        this.auth = auth;
        this.members = members;
        this.projects = projects;
        this.projectMembers = projectMembers;
        this.tasks = tasks;
        this.milestones = milestones;
        this.siteReports = siteReports;
        this.categories = categories;
        this.budgets = budgets;
        this.expenses = expenses;
        this.materials = materials;
        this.inventory = inventory;
        this.suppliers = suppliers;
        this.purchaseOrders = purchaseOrders;
        this.risks = risks;
        this.safety = safety;
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.transactions = transactions;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (organizationRepository.count() > 0) {
            log.info("Seed skipped: database already contains data");
            return;
        }
        log.info("Seeding demo data (password for all demo users: {})", DEMO_PASSWORD);
        seedAcme();
        seedLanka();
        log.info("Demo data ready. Log in as admin@acme.test, pm@acme.test, engineer@acme.test, finance@acme.test, "
                + "store@acme.test, viewer@acme.test or admin@lanka.test");
    }

    private void seedAcme() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        UUID orgId = auth.register(new RegisterRequest("Acme Builders", "Amara Perera", "admin@acme.test", DEMO_PASSWORD),
                SEED_CLIENT).me().activeOrganization().id();
        CurrentUser admin = as("admin@acme.test", orgId);

        addMember(admin, "Nuwan Silva", "pm@acme.test", Role.PROJECT_MANAGER);
        addMember(admin, "Kasun Fernando", "engineer@acme.test", Role.SITE_ENGINEER);
        addMember(admin, "Dilani Jayasinghe", "finance@acme.test", Role.FINANCE_OFFICER);
        addMember(admin, "Ruwan Bandara", "store@acme.test", Role.STOREKEEPER);
        addMember(admin, "Ishara Wijesinghe", "viewer@acme.test", Role.VIEWER);
        CurrentUser pm = as("pm@acme.test", orgId);
        CurrentUser engineer = as("engineer@acme.test", orgId);
        CurrentUser finance = as("finance@acme.test", orgId);
        CurrentUser store = as("store@acme.test", orgId);
        CurrentUser viewer = as("viewer@acme.test", orgId);

        // ---- Projects
        ProjectResponse kandy = projects.create(pm, new CreateProjectRequest("KDY-001", "Kandy Mixed-Use Block",
                "Four-storey retail and residential block with basement parking.", "Hill Country Developments",
                "Mr. Ranjith Herath, +94 81 222 3344", "No. 45, Peradeniya Road, Kandy", today.minusDays(120),
                today.plusDays(240), new BigDecimal("185000000.00"), null));
        ProjectResponse colombo = projects.create(pm, new CreateProjectRequest("COL-014", "Colombo Warehouse Extension",
                "2,400 m² steel-frame warehouse extension with loading bays.", "Ceylon Logistics PLC", null,
                "Port Access Road, Colombo 15", today.minusDays(30), today.plusDays(150),
                new BigDecimal("62000000.00"), null));
        projects.create(pm, new CreateProjectRequest("GAL-007", "Galle Coastal Villas", "Six-unit villa development.",
                "Southern Leisure (Pvt) Ltd", null, "Lighthouse Street, Galle", today.plusDays(30), today.plusDays(400),
                new BigDecimal("98000000.00"), null));

        for (ProjectResponse p : List.of(kandy, colombo)) {
            projectMembers.add(pm, p.id(), new AddProjectMemberRequest(engineer.userId(), ProjectRole.MEMBER));
            projectMembers.add(pm, p.id(), new AddProjectMemberRequest(viewer.userId(), ProjectRole.MEMBER));
            projects.transition(pm, p.id(), new TransitionProjectRequest(ProjectStatus.ACTIVE, null, version(pm, p.id())));
        }

        // ---- Milestones and tasks
        MilestoneResponse foundations = milestones.create(pm, kandy.id(),
                new MilestoneRequest("Foundations complete", null, today.minusDays(40), null, null));
        MilestoneResponse structure = milestones.create(pm, kandy.id(),
                new MilestoneRequest("Structure to level 2", null, today.plusDays(10), null, null));
        milestones.create(pm, kandy.id(), new MilestoneRequest("Roof and waterproofing", null, today.plusDays(90), null, null));

        record T(String title, MilestoneResponse m, TaskPriority p, int w, int startOffset, int dueOffset, TaskStatus target) {
        }
        List<T> kandyTasks = List.of(
                new T("Excavation and footing pour", foundations, TaskPriority.HIGH, 5, -110, -70, TaskStatus.DONE),
                new T("Basement retaining walls", foundations, TaskPriority.HIGH, 4, -80, -45, TaskStatus.DONE),
                new T("Ground floor slab", structure, TaskPriority.CRITICAL, 5, -40, -5, TaskStatus.IN_PROGRESS),
                new T("Level 1 columns and beams", structure, TaskPriority.HIGH, 4, -10, 8, TaskStatus.IN_PROGRESS),
                new T("Scaffolding inspection", structure, TaskPriority.MEDIUM, 1, -3, -1, TaskStatus.TODO),
                new T("Level 2 slab formwork", structure, TaskPriority.MEDIUM, 3, 5, 20, TaskStatus.TODO),
                new T("Order roofing sheets", null, TaskPriority.LOW, 1, 20, 45, TaskStatus.TODO));
        for (T t : kandyTasks) {
            TaskResponse task = tasks.create(pm, kandy.id(), new TaskRequest(t.title(), null, engineer.userId(),
                    t.m() != null ? t.m().id() : null, t.p(), t.w(), today.plusDays(t.startOffset()),
                    today.plusDays(t.dueOffset()), null));
            if (t.target() == TaskStatus.IN_PROGRESS || t.target() == TaskStatus.DONE) {
                task = tasks.transition(engineer, task.id(), new TaskTransitionRequest(TaskStatus.IN_PROGRESS, null, task.version()));
            }
            if (t.target() == TaskStatus.DONE) {
                tasks.transition(engineer, task.id(), new TaskTransitionRequest(TaskStatus.DONE, null, task.version()));
            }
        }
        TaskResponse survey = tasks.create(pm, colombo.id(), new TaskRequest("Site survey and setting out", null,
                engineer.userId(), null, TaskPriority.HIGH, 2, today.minusDays(28), today.minusDays(20), null));
        survey = tasks.transition(engineer, survey.id(), new TaskTransitionRequest(TaskStatus.IN_PROGRESS, null, survey.version()));
        tasks.transition(engineer, survey.id(), new TaskTransitionRequest(TaskStatus.DONE, null, survey.version()));
        tasks.create(pm, colombo.id(), new TaskRequest("Pile foundations", null, engineer.userId(), null,
                TaskPriority.CRITICAL, 5, today.minusDays(15), today.plusDays(25), null));
        tasks.create(pm, colombo.id(), new TaskRequest("Steel frame shop drawings approval", null, pm.userId(), null,
                TaskPriority.MEDIUM, 2, today.minusDays(5), today.minusDays(2), null));

        siteReports.create(engineer, kandy.id(), new SiteReportRequest(today.minusDays(1), "Sunny, 29°C", 34,
                "Poured ground-floor slab bays A-C. Rebar inspection for level 1 columns.", "Concrete delivery 40 minutes late."));
        siteReports.create(engineer, kandy.id(), new SiteReportRequest(today, "Light rain in the afternoon", 31,
                "Column formwork on grid lines 1-4. Curing of slab bays A-C.", null));

        // ---- Budgets and expenses
        Map<String, UUID> cat = categories.list(finance).stream()
                .collect(Collectors.toMap(BudgetCategoryResponse::name, BudgetCategoryResponse::id));
        budgets.replace(finance, kandy.id(), List.of(
                new AllocationItem(cat.get("Labour"), new BigDecimal("52000000.00")),
                new AllocationItem(cat.get("Materials"), new BigDecimal("78000000.00")),
                new AllocationItem(cat.get("Equipment"), new BigDecimal("21000000.00")),
                new AllocationItem(cat.get("Subcontractors"), new BigDecimal("26000000.00")),
                new AllocationItem(cat.get("Overheads"), new BigDecimal("8000000.00"))));
        budgets.replace(finance, colombo.id(), List.of(
                new AllocationItem(cat.get("Labour"), new BigDecimal("15000000.00")),
                new AllocationItem(cat.get("Materials"), new BigDecimal("32000000.00")),
                new AllocationItem(cat.get("Equipment"), new BigDecimal("9000000.00"))));

        approve(finance, submit(engineer, expenses.create(engineer, kandy.id(), new CreateExpenseRequest(cat.get("Materials"),
                "Ready-mix concrete, grade 30 (120 m³)", "Lanka Ready Mix", new BigDecimal("4380000.00"), today.minusDays(60)))));
        approve(finance, submit(engineer, expenses.create(engineer, kandy.id(), new CreateExpenseRequest(cat.get("Labour"),
                "Formwork crew – fortnight 1", "Kandy Labour Services", new BigDecimal("1250000.00"), today.minusDays(30)))));
        approve(finance, submit(engineer, expenses.create(engineer, kandy.id(), new CreateExpenseRequest(cat.get("Equipment"),
                "Tower crane hire – October", "Heavy Lift Lanka", new BigDecimal("2750000.00"), today.minusDays(10)))));
        submit(engineer, expenses.create(engineer, kandy.id(), new CreateExpenseRequest(cat.get("Materials"),
                "Reinforcement steel Y16 (8 t)", "Melwa Steel", new BigDecimal("2240000.00"), today.minusDays(2))));
        submit(engineer, expenses.create(engineer, kandy.id(), new CreateExpenseRequest(cat.get("Overheads"),
                "Site office electricity", "CEB", new BigDecimal("68500.00"), today.minusDays(1))));
        approve(finance, submit(engineer, expenses.create(engineer, colombo.id(), new CreateExpenseRequest(cat.get("Labour"),
                "Survey team", "GeoSurvey Lanka", new BigDecimal("480000.00"), today.minusDays(20)))));
        expenses.create(engineer, colombo.id(), new CreateExpenseRequest(cat.get("Materials"), "Draft: pile casings",
                null, new BigDecimal("910000.00"), today));

        // ---- Inventory
        MaterialResponse cement = materials.create(store, new CreateMaterialRequest("CEM-OPC-50", "Portland cement 50 kg", MaterialUnit.BAG, new BigDecimal("200")));
        MaterialResponse rebar = materials.create(store, new CreateMaterialRequest("STL-Y16", "Reinforcement bar Y16", MaterialUnit.TONNE, new BigDecimal("5")));
        MaterialResponse sand = materials.create(store, new CreateMaterialRequest("AGG-SAND", "River sand", MaterialUnit.M3, new BigDecimal("20")));
        MaterialResponse blocks = materials.create(store, new CreateMaterialRequest("BLK-150", "Cement block 150 mm", MaterialUnit.PIECE, new BigDecimal("1000")));
        inventory.receive(store, new ReceiptRequest(cement.id(), new BigDecimal("800"), "Opening stock"));
        inventory.receive(store, new ReceiptRequest(rebar.id(), new BigDecimal("12"), "Opening stock"));
        inventory.receive(store, new ReceiptRequest(sand.id(), new BigDecimal("45"), "Opening stock"));
        inventory.receive(store, new ReceiptRequest(blocks.id(), new BigDecimal("1500"), "Opening stock"));
        inventory.issue(store, new IssueRequest(cement.id(), kandy.id(), new BigDecimal("420"), "Slab bays A-C"));
        inventory.issue(store, new IssueRequest(rebar.id(), kandy.id(), new BigDecimal("8.5"), "Level 1 columns"));
        inventory.issue(store, new IssueRequest(sand.id(), colombo.id(), new BigDecimal("30"), "Pile caps"));
        inventory.issue(store, new IssueRequest(blocks.id(), kandy.id(), new BigDecimal("700"), "Basement walls"));

        // ---- Procurement: created by the storekeeper, approved by finance, partially received
        UUID supplierId = suppliers.create(store, new SupplierRequest("Melwa Steel", "Sales desk", "sales@melwa.example",
                "+94 11 234 5678", "Colombo 10", "VAT-114455", true, null)).id();
        suppliers.create(store, new SupplierRequest("Tokyo Cement", null, null, "+94 11 255 0000", "Colombo 03", null, true, null));
        PurchaseOrderResponse po = purchaseOrders.create(store, new PurchaseOrderRequest(supplierId, kandy.id(),
                today.plusDays(7), "Deliver to Kandy site gate 2", List.of(
                new PurchaseOrderLine(rebar.id(), new BigDecimal("10"), new BigDecimal("275000.0000")),
                new PurchaseOrderLine(cement.id(), new BigDecimal("500"), new BigDecimal("2450.0000"))), null));
        po = purchaseOrders.submit(store, po.id(), new StatusChangeRequest(po.version(), null));
        po = purchaseOrders.approve(finance, po.id(), new StatusChangeRequest(po.version(), null));
        purchaseOrders.receive(store, po.id(), "seed-receipt-0001", new ReceiveRequest("DN-55821", "First truck",
                List.of(new ReceiveLine(po.items().get(0).id(), new BigDecimal("6")))));

        // ---- Risks and safety
        risks.create(pm, kandy.id(), new RiskRequest("Monsoon delays to concrete works", "Heavy rain expected in November.",
                4, 4, "Schedule pours in the morning; covers on standby.", engineer.userId(), today.plusDays(30), null, null));
        risks.create(pm, kandy.id(), new RiskRequest("Steel price increase", null, 3, 3,
                "Lock in pricing with Melwa Steel for remaining tonnage.", pm.userId(), today.plusDays(14), null, null));
        risks.create(pm, colombo.id(), new RiskRequest("Unexpected ground conditions", null, 2, 5,
                "Additional boreholes before piling.", engineer.userId(), today.plusDays(10), null, null));
        safety.report(engineer, kandy.id(), new ReportIncidentRequest(today.minusDays(3).atStartOfDay().toInstant(ZoneOffset.UTC),
                "Level 1 scaffold, grid C", SafetyIncident.Severity.NEAR_MISS,
                "Unsecured plank found on scaffold platform before shift start."));
    }

    private void seedLanka() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        UUID orgId = auth.register(new RegisterRequest("Lanka Constructions", "Saman Kumara", "admin@lanka.test",
                DEMO_PASSWORD), SEED_CLIENT).me().activeOrganization().id();
        CurrentUser admin = as("admin@lanka.test", orgId);
        ProjectResponse p = projects.create(admin, new CreateProjectRequest("NEG-002", "Negombo Hotel Refurbishment",
                "Refurbishment of 80 rooms and lobby.", "Beachside Hotels", null, "Lewis Place, Negombo",
                today.minusDays(60), today.plusDays(120), new BigDecimal("45000000.00"), null));
        projects.transition(admin, p.id(), new TransitionProjectRequest(ProjectStatus.ACTIVE, null, version(admin, p.id())));
        tasks.create(admin, p.id(), new TaskRequest("Strip out floors 1-2", null, null, null, TaskPriority.HIGH, 3,
                today.minusDays(50), today.minusDays(10), null));
    }

    private void addMember(CurrentUser admin, String name, String email, Role role) {
        members.create(admin, new CreateMemberRequest(name, email, role, DEMO_PASSWORD));
        // Demo accounts skip the "change password at first login" step.
        transactions.executeWithoutResult(status -> {
            User user = userRepository.findByEmail(email).orElseThrow();
            user.changePassword(passwordEncoder.encode(DEMO_PASSWORD));
        });
    }

    private CurrentUser as(String email, UUID organizationId) {
        User user = userRepository.findByEmail(email).orElseThrow();
        OrganizationMembership membership = membershipRepository.findMembership(user.getId(), organizationId).orElseThrow();
        return new CurrentUser(user.getId(), organizationId, membership.getRole(), user.getEmail(), user.getFullName(), false);
    }

    private Long version(CurrentUser user, UUID projectId) {
        return projects.get(user, projectId).version();
    }

    private ExpenseResponse submit(CurrentUser user, ExpenseResponse expense) {
        return expenses.submit(user, expense.id());
    }

    private void approve(CurrentUser finance, ExpenseResponse expense) {
        expenses.approve(finance, expense.id(), new DecisionRequest(expense.version(), "Checked against invoice"));
    }
}
