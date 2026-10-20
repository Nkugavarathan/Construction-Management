package com.constructflow.procurement.entity;

import com.constructflow.common.persistence.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.UUID;

@Entity
@Table(name = "suppliers")
public class Supplier extends TimestampedEntity {

    @Column(nullable = false, updatable = false)
    private UUID organizationId;

    @Column(nullable = false)
    private String name;

    private String contactName;
    private String email;
    private String phone;
    private String address;
    private String taxId;

    @Column(nullable = false)
    private boolean active;

    @Version
    private Long version;

    protected Supplier() {
    }

    public Supplier(UUID organizationId, String name) {
        this.organizationId = organizationId;
        this.name = name;
        this.active = true;
    }

    public void update(String name, String contactName, String email, String phone, String address, String taxId,
                       boolean active) {
        this.name = name;
        this.contactName = contactName;
        this.email = email;
        this.phone = phone;
        this.address = address;
        this.taxId = taxId;
        this.active = active;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getName() {
        return name;
    }

    public String getContactName() {
        return contactName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getAddress() {
        return address;
    }

    public String getTaxId() {
        return taxId;
    }

    public boolean isActive() {
        return active;
    }

    public Long getVersion() {
        return version;
    }
}
