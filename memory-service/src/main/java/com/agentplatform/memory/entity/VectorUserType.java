package com.agentplatform.memory.entity;

import com.pgvector.PGvector;
import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * Custom Hibernate UserType for pgvector vector type.
 * Converts between Java float[] and PostgreSQL vector type via PGvector.
 */
public class VectorUserType implements UserType<float[]> {

    @Override
    public int getSqlType() {
        return Types.OTHER;
    }

    @Override
    public Class<float[]> returnedClass() {
        return float[].class;
    }

    @Override
    public boolean equals(float[] x, float[] y) throws HibernateException {
        if (x == y) return true;
        if (x == null || y == null) return false;
        if (x.length != y.length) return false;
        for (int i = 0; i < x.length; i++) {
            if (x[i] != y[i]) return false;
        }
        return true;
    }

    @Override
    public int hashCode(float[] x) throws HibernateException {
        int result = 1;
        for (float v : x) {
            result = 31 * result + Float.hashCode(v);
        }
        return result;
    }

    @Override
    public float[] nullSafeGet(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner)
            throws HibernateException, SQLException {
        Object value = rs.getObject(position);
        if (value == null) {
            return null;
        }
        if (value instanceof PGvector pgVector) {
            return pgVector.toArray();
        }
        if (value instanceof String str) {
            // Parse PostgreSQL vector text format: "[1,2,3]"
            String cleaned = str.replace("[", "").replace("]", "").trim();
            if (cleaned.isEmpty()) {
                return new float[0];
            }
            String[] parts = cleaned.split(",");
            float[] result = new float[parts.length];
            for (int i = 0; i < parts.length; i++) {
                result[i] = Float.parseFloat(parts[i].trim());
            }
            return result;
        }
        return null;
    }

    @Override
    public void nullSafeSet(PreparedStatement st, float[] value, int index, SharedSessionContractImplementor session)
            throws HibernateException, SQLException {
        if (value == null) {
            st.setNull(index, Types.OTHER);
        } else {
            PGvector pgVector = new PGvector(value);
            st.setObject(index, pgVector);
        }
    }

    @Override
    public float[] deepCopy(float[] value) throws HibernateException {
        if (value == null) {
            return null;
        }
        float[] copy = new float[value.length];
        System.arraycopy(value, 0, copy, 0, value.length);
        return copy;
    }

    @Override
    public boolean isMutable() {
        return true;
    }

    @Override
    public Serializable disassemble(float[] value) throws HibernateException {
        return deepCopy(value);
    }

    @Override
    public float[] assemble(Serializable cached, Object owner) throws HibernateException {
        return deepCopy((float[]) cached);
    }

    @Override
    public float[] replace(float[] original, float[] target, Object owner) throws HibernateException {
        return deepCopy(original);
    }
}