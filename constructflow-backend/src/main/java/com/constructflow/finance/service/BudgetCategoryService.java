package com.constructflow.finance.service;

import com.constructflow.common.exception.ConflictException;
import com.constructflow.common.exception.ForbiddenException;
import com.constructflow.common.exception.NotFoundException;
import com.constructflow.finance.dto.FinanceDtos.BudgetCategoryResponse;
import com.constructflow.finance.entity.BudgetCategory;
import com.constructflow.finance.repository.BudgetCategoryRepository;
import com.constructflow.organization.entity.Organization;
import com.constructflow.security.CurrentUser;
import com.constructflow.security.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class BudgetCategoryService {

    static final List<String> DEFAULT_CATEGORIES =
            List.of("Labour", "Materials", "Equipment", "Subcontractors", "Overheads");

    private final BudgetCategoryRepository repository;

    public BudgetCategoryService(BudgetCategoryRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void createDefaults(Organization organization) {
        DEFAULT_CATEGORIES.forEach(name -> repository.save(new BudgetCategory(organization.getId(), name)));
    }

    @Transactional(readOnly = true)
    public List<BudgetCategoryResponse> list(CurrentUser user) {
        return repository.findByOrganizationIdOrderByNameAsc(user.organizationId()).stream()
                .map(c -> new BudgetCategoryResponse(c.getId(), c.getName(), c.isActive()))
                .toList();
    }

    /** Categories are shared by every project, so only admins and finance officers define them. */
    @Transactional
    public BudgetCategoryResponse create(CurrentUser user, String name) {
        if (!user.isAdmin() && user.role() != Role.FINANCE_OFFICER) {
            throw new ForbiddenException("Only administrators and finance officers can create budget categories.");
        }
        if (repository.existsByOrganizationIdAndNameIgnoreCase(user.organizationId(), name.trim())) {
            throw new ConflictException("DUPLICATE_CATEGORY", "A category with this name already exists.");
        }
        BudgetCategory category = repository.save(new BudgetCategory(user.organizationId(), name));
        return new BudgetCategoryResponse(category.getId(), category.getName(), category.isActive());
    }

    @Transactional(readOnly = true)
    public BudgetCategory requireActive(UUID organizationId, UUID categoryId) {
        BudgetCategory category = repository.findByIdAndOrganizationId(categoryId, organizationId)
                .orElseThrow(() -> new NotFoundException("Budget category"));
        if (!category.isActive()) {
            throw new ConflictException("CATEGORY_INACTIVE", "This budget category is no longer in use.");
        }
        return category;
    }
}
