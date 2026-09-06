package com.agentplatform.orchestrator.advisor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies {@link ApplicationAdvisorRequest} validation.
 */
@DisplayName("ApplicationAdvisorRequest — validation")
class ApplicationAdvisorRequestTest {

    @Test
    @DisplayName("null request is rejected")
    void nullRequestRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ApplicationAdvisorRequest.validate(null));
    }

    @Test
    @DisplayName("null candidateId is rejected")
    void nullCandidateIdRejected() {
        ApplicationAdvisorRequest req = new ApplicationAdvisorRequest(null, "job-1");
        assertThrows(IllegalArgumentException.class,
                () -> ApplicationAdvisorRequest.validate(req));
    }

    @Test
    @DisplayName("zero candidateId is rejected")
    void zeroCandidateIdRejected() {
        ApplicationAdvisorRequest req = new ApplicationAdvisorRequest(0L, "job-1");
        assertThrows(IllegalArgumentException.class,
                () -> ApplicationAdvisorRequest.validate(req));
    }

    @Test
    @DisplayName("negative candidateId is rejected")
    void negativeCandidateIdRejected() {
        ApplicationAdvisorRequest req = new ApplicationAdvisorRequest(-1L, "job-1");
        assertThrows(IllegalArgumentException.class,
                () -> ApplicationAdvisorRequest.validate(req));
    }

    @Test
    @DisplayName("blank jobId is rejected")
    void blankJobIdRejected() {
        ApplicationAdvisorRequest req = new ApplicationAdvisorRequest(1L, "   ");
        assertThrows(IllegalArgumentException.class,
                () -> ApplicationAdvisorRequest.validate(req));
    }

    @Test
    @DisplayName("null jobId is rejected")
    void nullJobIdRejected() {
        ApplicationAdvisorRequest req = new ApplicationAdvisorRequest(1L, null);
        assertThrows(IllegalArgumentException.class,
                () -> ApplicationAdvisorRequest.validate(req));
    }

    @Test
    @DisplayName("oversized jobId is rejected")
    void oversizedJobIdRejected() {
        String tooLong = "x".repeat(ApplicationAdvisorRequest.MAX_JOB_ID_LENGTH + 1);
        ApplicationAdvisorRequest req = new ApplicationAdvisorRequest(1L, tooLong);
        assertThrows(IllegalArgumentException.class,
                () -> ApplicationAdvisorRequest.validate(req));
    }

    @Test
    @DisplayName("valid request passes validation")
    void validRequestPasses() {
        ApplicationAdvisorRequest req = new ApplicationAdvisorRequest(1L, "job-123");
        ApplicationAdvisorRequest.validate(req); // must not throw
    }

    @Test
    @DisplayName("fromDomain creates a valid request")
    void fromDomainCreatesValidRequest() {
        ApplicationAdvisorRequest req = ApplicationAdvisorRequest.fromDomain(42L, "job-42");
        assertEquals(42L, req.candidateId());
        assertEquals("job-42", req.jobId());
        ApplicationAdvisorRequest.validate(req); // must not throw
    }
}

