package com.constructflow.common.persistence;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Generates gap-tolerant, per-organization document numbers such as EXP-000042.
 *
 * <p>The upsert takes a row lock on (organization, sequence), so two concurrent callers can never
 * receive the same value. It joins the caller's transaction: if the business operation rolls back,
 * the number is released with it.
 */
@Service
public class OrgSequenceService {

    private final JdbcClient jdbcClient;

    public OrgSequenceService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String next(UUID organizationId, String sequenceName, String prefix) {
        Long value = jdbcClient.sql("""
                        insert into org_sequences (organization_id, name, next_value) values (:org, :name, 2)
                        on conflict (organization_id, name) do update set next_value = org_sequences.next_value + 1
                        returning next_value - 1
                        """)
                .param("org", organizationId)
                .param("name", sequenceName)
                .query(Long.class)
                .single();
        return "%s-%06d".formatted(prefix, value);
    }
}
