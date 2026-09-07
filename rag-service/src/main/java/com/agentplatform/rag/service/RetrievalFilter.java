package com.agentplatform.rag.service;

/**
 * Optional constraints applied to a vector retrieval. All fields are nullable;
 * a null field is not applied. {@link #none()} and the named factories cover
 * the common cases.
 */
public record RetrievalFilter(String source,
                              String contentType,
                              String metadataKey,
                              String metadataValue) {

    public static RetrievalFilter none() {
        return new RetrievalFilter(null, null, null, null);
    }

    public static RetrievalFilter bySource(String source) {
        return new RetrievalFilter(source, null, null, null);
    }

    public static RetrievalFilter byContentType(String contentType) {
        return new RetrievalFilter(null, contentType, null, null);
    }

    /**
     * Filters chunks whose {@code metadata} JSON object has the given key/value
     * pair. Both must be non-null to be applied.
     */
    public static RetrievalFilter metadata(String key, String value) {
        return new RetrievalFilter(null, null, key, value);
    }
}