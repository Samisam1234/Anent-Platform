package com.agentplatform.orchestrator.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 12.9 value-set check for {@link ApplicationStatus}.
 */
@DisplayName("ApplicationStatus - Phase 12.9 status model")
class ApplicationStatusTest {

    @Test
    @DisplayName("enum value set is exactly the six legacy statuses plus EMAIL_SENT")
    void valueSetHasSevenValues() {
        assertEquals(
                EnumSet.of(
                        ApplicationStatus.DRAFT,
                        ApplicationStatus.GENERATED,
                        ApplicationStatus.UNDER_REVIEW,
                        ApplicationStatus.APPROVED_FOR_APPLICATION,
                        ApplicationStatus.REJECTED,
                        ApplicationStatus.ARCHIVED,
                        ApplicationStatus.EMAIL_SENT),
                EnumSet.allOf(ApplicationStatus.class));
    }

    @Test
    @DisplayName("EMAIL_SENT carries a status string mirroring its name")
    void emailSentStatusString() {
        assertEquals("EMAIL_SENT", ApplicationStatus.EMAIL_SENT.getStatus());
        assertEquals(ApplicationStatus.EMAIL_SENT.name(), ApplicationStatus.EMAIL_SENT.getStatus());
    }

    @Test
    @DisplayName("all legacy values remain present and unrenamed")
    void legacyValuesPreserved() {
        Set<String> names = Set.of("DRAFT", "GENERATED", "UNDER_REVIEW",
                "APPROVED_FOR_APPLICATION", "REJECTED", "ARCHIVED");
        for (ApplicationStatus status : ApplicationStatus.values()) {
            assertTrue(names.contains(status.name()) || status == ApplicationStatus.EMAIL_SENT,
                    "unexpected status value: " + status);
        }
    }
}