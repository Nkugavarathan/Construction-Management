package com.constructflow.organization.entity;

import com.constructflow.common.persistence.TimestampedEntity;
import com.constructflow.security.Role;
import com.constructflow.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Links a user to an organization with exactly one organization-scoped role. */
@Entity
@Table(name = "organization_memberships")
public class OrganizationMembership extends TimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MembershipStatus status;

    @Version
    private Long version;

    protected OrganizationMembership() {
    }

    public OrganizationMembership(Organization organization, User user, Role role) {
        this.organization = organization;
        this.user = user;
        this.role = role;
        this.status = MembershipStatus.ACTIVE;
    }

    public boolean isActive() {
        return status == MembershipStatus.ACTIVE;
    }

    public void changeRole(Role role) {
        this.role = role;
    }

    public void changeStatus(MembershipStatus status) {
        this.status = status;
    }

    public Organization getOrganization() {
        return organization;
    }

    public User getUser() {
        return user;
    }

    public Role getRole() {
        return role;
    }

    public MembershipStatus getStatus() {
        return status;
    }

    public Long getVersion() {
        return version;
    }
}
