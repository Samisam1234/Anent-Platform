package com.agentplatform.memory.dialect;

import java.sql.Types;

import org.hibernate.boot.model.TypeContributions;
import org.hibernate.dialect.H2Dialect;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.type.descriptor.sql.internal.DdlTypeImpl;
import org.hibernate.type.descriptor.sql.spi.DdlTypeRegistry;

/**
 * H2 dialect that maps {@link Types#OTHER} (used by {@link com.agentplatform.memory.entity.VectorUserType})
 * to a plain {@code varbinary} column so the in-memory H2 profile can create the schema.
 *
 * <p>Embeddings are never read or written through H2 — the column only exists so
 * Hibernate's schema generation succeeds for {@code @DataJpaTest} contexts.</p>
 */
public class H2VectorDialect extends H2Dialect {

    @Override
    protected void registerColumnTypes(TypeContributions typeContributions, ServiceRegistry serviceRegistry) {
        super.registerColumnTypes(typeContributions, serviceRegistry);
        DdlTypeRegistry registry = typeContributions.getTypeConfiguration().getDdlTypeRegistry();
        registry.addDescriptor(Types.OTHER, new DdlTypeImpl(Types.OTHER, "varbinary", "varbinary", this));
    }
}
