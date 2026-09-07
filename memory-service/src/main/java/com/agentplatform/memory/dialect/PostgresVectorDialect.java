package com.agentplatform.memory.dialect;

import java.sql.Types;

import org.hibernate.boot.model.TypeContributions;
import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.type.descriptor.sql.internal.DdlTypeImpl;
import org.hibernate.type.descriptor.sql.spi.DdlTypeRegistry;

/**
 * PostgreSQL dialect that maps {@link Types#OTHER} (used by {@link com.agentplatform.memory.entity.VectorUserType})
 * to the pgvector {@code vector(768)} column type.
 *
 * <p>Without this, Hibernate 6.6 emits {@code embedding OTHER} which PostgreSQL rejects.</p>
 */
public class PostgresVectorDialect extends PostgreSQLDialect {

    @Override
    protected void registerColumnTypes(TypeContributions typeContributions, ServiceRegistry serviceRegistry) {
        super.registerColumnTypes(typeContributions, serviceRegistry);
        DdlTypeRegistry registry = typeContributions.getTypeConfiguration().getDdlTypeRegistry();
        registry.addDescriptor(Types.OTHER, new DdlTypeImpl(Types.OTHER, "vector(768)", "vector", this));
    }
}
