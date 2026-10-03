package com.agentplatform.ui.dto;

import com.agentplatform.orchestrator.tailoring.ResumeTailoringAnalysis;
import com.agentplatform.orchestrator.tailoring.TailoredResumeDraft;

/**
 * Transport shape returned by {@code POST /api/v1/resume/tailor}. Mirrors the frontend's
 * existing request/response shape: the deterministic gap + tailoring analysis and the
 * deterministic tailored draft, computed per request and never persisted.
 *
 * @param analysis the deterministic tailoring analysis (gap + recommendations)
 * @param draft    the deterministic tailored resume draft
 */
public record ResumeTailoringResponse(
        ResumeTailoringAnalysis analysis,
        TailoredResumeDraft draft
) {
}