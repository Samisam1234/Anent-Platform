package com.agentplatform.orchestrator.document;

/**
 * A rendered tailored-resume document ready to be returned to the client.
 *
 * <p>Produced per-request by a {@link ResumeDocumentGenerator}; never persisted and never
 * cached. {@code bytes} holds the fully rendered binary document, {@code contentType} the
 * exact MIME type to return, and {@code filename} the safe attachment filename.</p>
 *
 * @param bytes       fully rendered binary document
 * @param contentType exact MIME type of the document
 * @param filename    safe {@code Content-Disposition} attachment filename
 */
public record GeneratedResumeDocument(
        byte[] bytes,
        String contentType,
        String filename
) {
}