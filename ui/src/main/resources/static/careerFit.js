/**
 * AI Job Agent — Career Fit snapshot (shared by resume.js and dashboard.js).
 *
 * Builds the dashboard's Career Fit section purely from the candidate profile the
 * server already returned for an uploaded resume. Nothing here is estimated, and no
 * numeric "career score" is produced — the platform has no calibrated career score,
 * so the section reports qualitative levels with the evidence behind each one:
 *
 *   Tracks  — CandidateProfile.careerTrackEvidence: the tracks the deterministic
 *             profile builder actually detected (weight >= 2.0), each with its
 *             weighted score and the canonical skills that contributed. Levels are
 *             the track's weight relative to the strongest detected track, so they
 *             rank real evidence instead of inventing a percentage.
 *   Roles   — CandidateProfile.preferredRoles, already inferred server-side from the
 *             detected tracks.
 *   Skills  — ResumeEvidence.evidenceStrength per canonical skill (STRONG / MEDIUM /
 *             WEAK), the strongest observation kept per skill.
 *
 * If a profile carries none of these (for example a scanned image where only a name
 * could be read) the snapshot says so rather than filling the section with guesses.
 */
(() => {
    'use strict';

    const STORAGE_KEY = 'agentplatform:careerFit';
    const MAX_ITEMS = 12;

    /** Relative weight of a track versus the strongest detected track → level. */
    function trackLevel(ratio) {
        if (ratio >= 0.75) return 'Strong';
        if (ratio >= 0.45) return 'Developing';
        return 'Emerging';
    }

    function unique(values) {
        const seen = new Set();
        const out = [];
        (values || []).forEach(value => {
            const text = String(value == null ? '' : value).trim();
            if (!text) return;
            const key = text.toLowerCase();
            if (seen.has(key)) return;
            seen.add(key);
            out.push(text);
        });
        return out;
    }

    /** Strongest evidence strength observed for each canonical skill. */
    function skillsByStrength(resumeEvidence) {
        const RANK = { STRONG: 3, MEDIUM: 2, WEAK: 1 };
        const best = new Map();
        (resumeEvidence || []).forEach(entry => {
            if (!entry) return;
            const skill = String(entry.canonicalSkill || '').trim();
            if (!skill) return;
            const rank = RANK[entry.evidenceStrength] || 1;
            const key = skill.toLowerCase();
            if (!best.has(key) || best.get(key).rank < rank) {
                best.set(key, { skill, rank });
            }
        });

        const buckets = { 3: [], 2: [], 1: [] };
        Array.from(best.values())
            .sort((a, b) => a.skill.localeCompare(b.skill))
            .forEach(item => buckets[item.rank].push(item.skill));
        return buckets;
    }

    /**
     * Derives the Career Fit snapshot from a server-returned CandidateProfile.
     * Returns null when the profile carries no track evidence and no skills, so the
     * dashboard can show an honest "not enough evidence" state.
     */
    function build(profile, candidateId, candidateName) {
        if (!profile) return null;

        const rawTracks = Array.isArray(profile.careerTrackEvidence) ? profile.careerTrackEvidence : [];
        const scored = rawTracks
            .filter(t => t && t.track)
            .map(t => ({
                label: String(t.track),
                score: Number.isFinite(t.score) ? t.score : 0,
                skills: unique(t.contributingSkills)
            }))
            .sort((a, b) => b.score - a.score || a.label.localeCompare(b.label));

        const strongest = scored.length ? scored[0].score : 0;
        const tracks = scored.map(t => ({
            label: t.label,
            level: trackLevel(strongest > 0 ? t.score / strongest : 0),
            skillCount: t.skills.length,
            skills: t.skills.slice(0, MAX_ITEMS)
        }));

        const buckets = skillsByStrength(profile.resumeEvidence);
        const roles = unique(profile.preferredRoles);

        if (!tracks.length && !buckets[3].length && !buckets[2].length && !buckets[1].length && !roles.length) {
            return null;
        }

        return {
            candidateId: candidateId != null ? String(candidateId) : null,
            candidateName: candidateName || profile.name || null,
            tracks: tracks,
            roles: roles.slice(0, MAX_ITEMS),
            strongSkills: buckets[3].slice(0, MAX_ITEMS),
            developingSkills: buckets[2].slice(0, MAX_ITEMS),
            limitedSkills: buckets[1].slice(0, MAX_ITEMS)
        };
    }

    /** Persists the snapshot; a profile with no usable evidence clears any stale one. */
    function save(profile, candidateId, candidateName) {
        const snapshot = build(profile, candidateId, candidateName);
        try {
            if (snapshot) {
                localStorage.setItem(STORAGE_KEY, JSON.stringify(snapshot));
            } else {
                localStorage.removeItem(STORAGE_KEY);
            }
        } catch (_) {
            // Storage may be unavailable (private mode / quota); the dashboard then
            // simply shows its "upload a resume" state. Not worth failing the upload.
        }
        return snapshot;
    }

    function load() {
        try {
            const raw = localStorage.getItem(STORAGE_KEY);
            if (!raw) return null;
            const parsed = JSON.parse(raw);
            return parsed && typeof parsed === 'object' ? parsed : null;
        } catch (_) {
            return null;
        }
    }

    function clear() {
        try {
            localStorage.removeItem(STORAGE_KEY);
        } catch (_) { /* nothing to clean up */ }
    }

    window.careerFit = {
        KEY: STORAGE_KEY,
        build: build,
        save: save,
        load: load,
        clear: clear
    };
})();
