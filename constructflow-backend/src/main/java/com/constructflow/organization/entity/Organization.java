package com.constructflow.organization.entity;

import com.constructflow.common.persistence.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;

/** A tenant: one construction company. Every business record belongs to exactly one organization. */
@Entity
@Table(name = "organizations")
public class Organization extends TimestampedEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false, length = 3)
    private String currencyCode;

    @Column(nullable = false)
    private String timezone;

    /** Project managers may approve expenses up to this amount; larger ones need finance or an admin. */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal expenseApprovalThreshold;

    @Version
    private Long version;

    protected Organization() {
    }

    public Organization(String name, String slug, String currencyCode, String timezone, BigDecimal expenseApprovalThreshold) {
        this.name = name.trim();
        this.slug = slug;
        this.currencyCode = currencyCode;
        this.timezone = timezone;
        this.expenseApprovalThreshold = expenseApprovalThreshold;
    }

    public void updateSettings(String name, String currencyCode, String timezone, BigDecimal expenseApprovalThreshold) {
        this.name = name.trim();
        this.currencyCode = currencyCode;
        this.timezone = timezone;
        this.expenseApprovalThreshold = expenseApprovalThreshold;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public String getTimezone() {
        return timezone;
    }

    public BigDecimal getExpenseApprovalThreshold() {
        return expenseApprovalThreshold;
    }

    public Long getVersion() {
        return version;
    }
}
