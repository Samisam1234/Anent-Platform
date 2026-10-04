package com.agentplatform.orchestrator.job;

import com.agentplatform.orchestrator.matching.CareerTrack;
import com.agentplatform.orchestrator.matching.CareerTrackEngine;
import com.agentplatform.orchestrator.resume.CandidateProfile;
import com.agentplatform.orchestrator.resume.entity.CandidateProfileEntity;
import com.agentplatform.orchestrator.resume.persistence.CandidateProfilePersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;

/**
 * Orchestrates job discovery across configured {@link JobSource}s,
 * deduplicates listings via {@link JobDeduplicationService}, and applies
 * deterministic keyword/location/experience/type/date filters.
 *
 * <p>When a request carries a {@code candidateProfileId} and no explicit keywords, the
 * relevance keywords are derived from that stored profile's parsed skills. This is what
 * lets job discovery follow the uploaded resume: the profile's career track and top
 * skills are condensed into a single job-oriented query that is forwarded to the active
 * sources, and the same derived keywords drive the local relevance filter. Explicit
 * caller keywords always win over profile-derived ones.</p>
 */
@Service
public class JobSearchService {

    private static final Logger log = LoggerFactory.getLogger(JobSearchService.class);

    /** Upper bound on derived keywords so a skill-rich resume cannot over-narrow discovery. */
    private static final int MAX_DERIVED_KEYWORDS = 12;

    private final List<JobSource> jobSources;
    private final JobDeduplicationService deduplicationService;
    private final CandidateProfilePersistenceService candidateProfiles;
    private final JobRelevanceScorer relevanceScorer;
    private final NegativeJobFilter negativeJobFilter;
    private final CareerTrackEngine careerTrackEngine;

    /**
     * Exact job-id lookup cache, populated from the listings this service actually
     * returned.
     *
     * <p>Sources are queried with the caller's keywords, so a listing that came out of a
     * keyword search cannot in general be re-fetched by id alone: an aggregator or MCP
     * source that requires a query term has nothing to match against. Caching the exact
     * records that were returned makes {@link #findById(String)} resolve the job the user
     * just saw, including its {@code applicationUrl}, without a second network round trip
     * and without inventing a query the user never typed.</p>
     *
     * <p>The cache is <b>process-local and in-memory</b>: it holds no credentials and no
     * fabricated data — only immutable {@link Job} records exactly as a source published
     * them — and it is empty again after a restart. A cold cache falls back to the
     * existing per-source lookup, so behaviour degrades to what it was before rather than
     * to a wrong answer.</p>
     */
    private final JobIdCache idCache = new JobIdCache();

    /**
     * Test/standalone constructor: no candidate persistence, so profile-driven keyword
     * derivation is skipped and searches behave exactly as before.
     */
    public JobSearchService(List<JobSource> jobSources, JobDeduplicationService deduplicationService) {
        this(jobSources, deduplicationService, null);
    }

    /**
     * Convenience constructor that keeps the pre-existing three-argument call sites working.
     * The relevance components are stateless, so default instances are equivalent to the
     * Spring-managed ones.
     */
    public JobSearchService(List<JobSource> jobSources,
                            JobDeduplicationService deduplicationService,
                            CandidateProfilePersistenceService candidateProfiles) {
        this(jobSources, deduplicationService, candidateProfiles,
                new JobRelevanceScorer(), new NegativeJobFilter(), new CareerTrackEngine());
    }

    /**
     * Production constructor. {@code @Autowired} is explicit because the class declares
     * more than one constructor.
     */
    @Autowired
    public JobSearchService(List<JobSource> jobSources,
                            JobDeduplicationService deduplicationService,
                            CandidateProfilePersistenceService candidateProfiles,
                            JobRelevanceScorer relevanceScorer,
                            NegativeJobFilter negativeJobFilter,
                            CareerTrackEngine careerTrackEngine) {
        this.deduplicationService = deduplicationService != null
                ? deduplicationService
                : new JobDeduplicationService();
        this.candidateProfiles = candidateProfiles;
        this.relevanceScorer = relevanceScorer != null ? relevanceScorer : new JobRelevanceScorer();
        this.negativeJobFilter = negativeJobFilter != null ? negativeJobFilter : new NegativeJobFilter();
        this.careerTrackEngine = careerTrackEngine != null ? careerTrackEngine : new CareerTrackEngine();

        if (jobSources != null && !jobSources.isEmpty()) {
            this.jobSources = List.copyOf(jobSources);
        } else {
            // No silent fallback to the development catalog. An empty source list means
            // "no jobs available", which the UI renders as a professional empty state
            // rather than as fabricated listings.
            this.jobSources = List.of();
        }
    }

    /**
     * Finds a job by its globally unique id across all available sources.
     *
     * @param id the job id (must not be blank)
     * @return an {@link Optional} containing the matching job, or empty if not found
     */
    public Optional<Job> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        // A hit here is a listing this service already returned under exactly this id, so
        // the match is by identity, never by similarity: a different job is never returned
        // for the requested id.
        Job cached = idCache.get(id);
        if (cached != null) {
            return Optional.of(cached);
        }
        for (JobSource source : jobSources) {
            try {
                if (!source.isAvailable()) {
                    continue;
                }
                List<Job> jobs = source.search(JobSearchRequest.of(List.of(), null, null, null, null, 100));
                if (jobs == null) {
                    continue;
                }
                Optional<Job> match = jobs.stream().filter(job -> job != null && id.equals(job.id())).findFirst();
                if (match.isPresent()) {
                    idCache.put(match.get());
                    return match;
                }
            } catch (Exception ex) {
                log.warn("Failed to look up job by id '{}' from source '{}': {}",
                        id, source.getSourceName(), ex.getMessage());
            }
        }
        return Optional.empty();
    }

    public JobSearchResult search(JobSearchRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("JobSearchRequest must not be null");
        }
        if (request.limit() != null && (request.limit() < 1 || request.limit() > 100)) {
            throw new IllegalArgumentException("Limit must be between 1 and 100");
        }
        long startTime = System.currentTimeMillis();

        // The stored profile drives both relevance keywords and career-track filtering, so
        // it is resolved once. Explicit keywords always win over profile-derived ones — and
        // when the caller supplies their own keywords, that is an expression of intent that
        // supersedes the resume, so the profile's discipline must not then be used to veto
        // what the caller explicitly asked for.
        CandidateProfile profile = resolveProfile(request);
        boolean explicitKeywords = request.keywords() != null && !request.keywords().isEmpty();
        List<String> relevanceKeywords = explicitKeywords
                ? request.keywords()
                : deriveProfileKeywords(profile);
        Set<CareerTrack> candidateTracks = explicitKeywords
                ? Set.of()
                : careerTrackEngine.classifyCandidate(profile);

        log.info("Starting job search: keywords={}, tracks={}, location='{}', experience='{}', type='{}', date='{}', limit={}",
                relevanceKeywords, candidateTracks, request.location(), request.experience(),
                request.employmentType(), request.datePosted(), request.limit());

        List<JobSource> activeSources = jobSources.stream().filter(JobSource::isAvailable).toList();
        if (activeSources.isEmpty()) {
            log.warn("No active JobSource available");
            return new JobSearchResult(List.of(), 0, "NONE", false,
                    "No job sources are currently available.");
        }

        List<Job> rawListings = new ArrayList<>();
        List<String> sourceNames = new ArrayList<>();
        boolean anyLive = false;
        int failedSources = 0;

        // Propagate derived keywords to downstream sources when no explicit keywords were provided.
        // For profile-driven searches, build a concise job-oriented query from the profile's
        // track and top skills, instead of passing all 12 skills as individual keywords.
        // A blank derivation (anonymous search with nothing to derive) means there is no
        // query to forward — pass the original request so no empty keyword is pushed upstream.
        String derivedQuery = buildJobOrientedQuery(profile, candidateTracks, relevanceKeywords);
        JobSearchRequest effectiveRequest = explicitKeywords || derivedQuery.isBlank() ? request
                : new JobSearchRequest(List.of(derivedQuery),
                        request.location(), request.experience(),
                        request.employmentType(), request.datePosted(), request.limit(), request.source(),
                        request.candidateProfileId());

        for (JobSource source : activeSources) {
            try {
                List<Job> jobs = source.search(effectiveRequest);
                if (jobs != null) {
                    rawListings.addAll(jobs);
                    // A live source only counts as live if it actually contributed listings.
                    // An enabled-but-unreachable public API must not make the UI claim live
                    // data while every row really came from the mock catalog.
                    if (source.isLive() && !jobs.isEmpty()) {
                        anyLive = true;
                    }
                }
                sourceNames.add(source.getSourceName());
            } catch (Exception ex) {
                failedSources++;
                log.error("Error retrieving jobs from source '{}': {}", source.getSourceName(), ex.getMessage(), ex);
            }
        }
        if (failedSources == activeSources.size()) {
            log.warn("All configured job sources failed to respond — returning an empty safe response");
            return new JobSearchResult(List.of(), 0, "NONE", false,
                    "All configured job sources failed to respond. Returning no results.");
        }

        int rawCount = rawListings.size();
        List<Job> deduplicated = deduplicationService.deduplicate(rawListings);
        int afterDedupCount = deduplicated.size();

        // Pipeline order: retrieval → normalization → dedup → career-track relevance →
        // negative exclusions → keyword relevance → location → experience → employment
        // type → date. Track and exclusion run before keyword relevance so that an
        // off-discipline listing is discarded on what it IS rather than on whether one of
        // its words happens to appear in the candidate's skill list.
        List<Job> trackRelevant = deduplicated.stream()
                .filter(JobSearchService::hasMinimalQuality)
                .filter(job -> isTrackRelevant(job, candidateTracks))
                .toList();

        List<Job> notExcluded = trackRelevant.stream()
                .filter(job -> {
                    String reason = negativeJobFilter.exclusionReason(job, candidateTracks);
                    if (reason != null) {
                        log.debug("Excluded job id={} title='{}' on negative rule '{}'",
                                job.id(), job.title(), reason);
                        return false;
                    }
                    return true;
                })
                .toList();

        List<Job> filtered = notExcluded.stream()
                .filter(job -> relevanceScorer.isRelevant(job, relevanceKeywords))
                .filter(job -> matchesLocation(job, request.location()))
                .filter(job -> matchesSource(job, request.source()))
                .filter(job -> matchesExperience(job, request.experience()))
                .filter(job -> matchesEmploymentType(job, request.employmentType()))
                .filter(job -> matchesDatePosted(job, request.datePosted()))
                .toList();
        int afterFilterCount = filtered.size();

        if (afterFilterCount < notExcluded.size()) {
            log.info("Relevance filtering removed {} listing(s) that did not meet the keyword threshold",
                    notExcluded.size() - afterFilterCount);
        }
        if (notExcluded.size() < trackRelevant.size()) {
            log.info("Negative rules removed {} non-engineering listing(s)",
                    trackRelevant.size() - notExcluded.size());
        }

        int limit = request.limit() != null && request.limit() > 0 ? request.limit() : 20;
        List<Job> limitedResults = filtered.stream().limit(limit).toList();

        // Cache exactly what the caller is about to see. Populating from the final list —
        // after normalization, dedup and filtering — means findById() hands back the same
        // record the UI was shown, with the same id and the same applicationUrl a source
        // published (dedup keeps the first record's id and merges in a URL a later record
        // supplied, so caching earlier would cache a listing the user never received).
        idCache.putAll(limitedResults);

        long durationMs = System.currentTimeMillis() - startTime;
        String combinedSourceName = String.join(", ", sourceNames);
        boolean liveConfigured = activeSources.stream().anyMatch(JobSource::isLive);
        // Describe what was actually returned rather than what might have been configured:
        // the development wording appears only when mock rows really are in the result
        // (job-sources.mock.enabled=true), never in the normal live-only flow.
        boolean anyMock = limitedResults.stream()
                .anyMatch(job -> MockJobSource.SOURCE_NAME.equals(job.source()));
        String message;
        if (anyLive) {
            message = "Live job search completed successfully.";
        } else if (anyMock) {
            message = liveConfigured
                    ? "The live job source returned no listings, so results fall back to the development mock catalog."
                    : "Live job source not configured. Returning development mock data.";
        } else {
            message = liveConfigured
                    ? "No live jobs are currently available for these preferences."
                    : "No live job source is configured.";
        }
        log.info("Job search complete in {} ms: sources=[{}], raw={}, afterDedup={}, afterFilter={}, returned={}",
                durationMs, combinedSourceName, rawCount, afterDedupCount, afterFilterCount, limitedResults.size());
        return new JobSearchResult(limitedResults, afterFilterCount, combinedSourceName, anyLive, message);
    }

    /**
     * Basic result-quality guard (survives dedup/normalization): rejects listings that
     * cannot be usefully surfaced — a null/blank id or a blank title. Malformed external
     * data is dropped here so it never reaches the frontend, without crashing the search.
     */
    private static boolean hasMinimalQuality(Job job) {
        if (job == null) {
            return false;
        }
        if (job.id() == null || job.id().isBlank()) {
            return false;
        }
        return job.title() != null && !job.title().isBlank();
    }

    private boolean matchesSource(Job job, String requestedSource) {
        if (requestedSource == null || requestedSource.trim().isEmpty()
                || "all".equalsIgnoreCase(requestedSource.trim())
                || "any".equalsIgnoreCase(requestedSource.trim())) {
            return true;
        }
        String query = requestedSource.trim().toLowerCase();
        String jobSource = job.source() != null ? job.source().toLowerCase() : "";
        return jobSource.equals(query);
    }

    /**
     * Returns the keywords that decide which discovered listings are relevant.
     *
     * <p>Explicit caller keywords are used verbatim (backwards compatible). When none are
     * supplied but the request names a stored candidate profile, the profile's parsed
     * skills become the relevance keywords — this is the profile-driven discovery that
     * replaces the removed free-text "Keywords &amp; Skills" field. When neither is
     * available, an empty list is returned and nothing is filtered out.</p>
     */
    /**
     * Loads the stored candidate profile the request refers to, or {@code null} when the
     * request is anonymous, the id is absent, the profile cannot be found, or persistence
     * is unavailable. Every downstream relevance decision treats {@code null} as "no
     * profile signal" rather than as an error, so an anonymous search still works.
     */
    private CandidateProfile resolveProfile(JobSearchRequest request) {
        Long profileId = request.candidateProfileId();
        if (candidateProfiles == null || profileId == null) {
            return null;
        }
        try {
            return candidateProfiles.findById(profileId)
                    .map(CandidateProfileEntity::toDomain)
                    .orElseGet(() -> {
                        log.debug("Candidate profile {} not found — searching without profile relevance", profileId);
                        return null;
                    });
        } catch (Exception ex) {
            log.warn("Could not load candidate profile {} for relevance filtering: {}",
                    profileId, ex.getMessage());
            return null;
        }
    }

    /**
     * Whether a listing is in a discipline the candidate actually targets.
     *
     * <p>Conservative by design. It only rejects when <em>both</em> sides are specific and
     * they belong to different families — a VLSI candidate against a clearly software role,
     * or the reverse. Listings whose discipline cannot be determined are left for the
     * negative rules and keyword relevance to judge, because unclassifiable wording is not
     * evidence that a role is wrong; and a search with no known candidate discipline has no
     * basis to exclude anything on track.</p>
     */
    private boolean isTrackRelevant(Job job, Set<CareerTrack> candidateTracks) {
        if (candidateTracks == null || candidateTracks.isEmpty()) {
            return true;
        }
        CareerTrack jobTrack = careerTrackEngine.classifyJob(job);
        if (jobTrack == CareerTrack.UNKNOWN || jobTrack == CareerTrack.MIXED) {
            return true;
        }
        for (CareerTrack candidateTrack : candidateTracks) {
            if (candidateTrack == null || !candidateTrack.isSpecific()) {
                continue;
            }
            if (candidateTrack.family() == jobTrack.family()) {
                return true;
            }
        }
        boolean candidateIsSpecific = candidateTracks.stream().anyMatch(CareerTrack::isSpecific);
        return !candidateIsSpecific;
    }

    /**
     * Builds relevance keywords from a parsed profile: canonical skills first, then the
     * track-specific skill lists and inferred preferred roles. Order is stable and the
     * list is capped so a skill-rich resume cannot over-narrow discovery.
     */
    static List<String> deriveProfileKeywords(CandidateProfile profile) {
        if (profile == null) {
            return List.of();
        }
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        addAll(keywords, profile.skills());
        addAll(keywords, profile.softwareSkills());
        addAll(keywords, profile.hardwareSkills());
        addAll(keywords, profile.preferredRoles());
        return keywords.stream().limit(MAX_DERIVED_KEYWORDS).toList();
    }

    /**
     * Builds a concise job-oriented search query from the candidate's profile and career tracks.
     * <p>
     * Instead of passing all 12 raw skills as individual keywords (which makes poor search queries),
     * this constructs a concise job-oriented role query like "Embedded Systems Engineer" or
     * "Software Engineer" based on the candidate's detected career tracks and top skills. The result
     * is a role phrase employers actually publish in posting titles, because the live search tools
     * match the query as a phrase.
     * </p>
     */
    static String buildJobOrientedQuery(CandidateProfile profile, Set<CareerTrack> candidateTracks,
                                        List<String> relevanceKeywords) {
        if (profile == null) {
            return relevanceKeywords.isEmpty() ? "" : relevanceKeywords.get(0);
        }

        // Build a role-oriented query based on the strongest career track signal
        String rolePrefix = "";
        if (candidateTracks != null && !candidateTracks.isEmpty()) {
            for (CareerTrack track : candidateTracks) {
                switch (track) {
                    case EMBEDDED -> { return "Embedded Systems Engineer"; }
                    case VLSI_FPGA -> { return "VLSI FPGA Engineer"; }
                    case AI_ML -> { return "Machine Learning Engineer"; }
                    case SOFTWARE -> { rolePrefix = "Software Engineer"; }
                    case HARDWARE -> { rolePrefix = "Hardware Engineer"; }
                    default -> {}
                }
            }
        }

        // If no specific track prefix, infer from skills
        if (rolePrefix.isEmpty()) {
            List<String> topSkills = relevanceKeywords.stream().limit(3).toList();
            if (!topSkills.isEmpty()) {
                rolePrefix = String.join(" ", topSkills.subList(0, Math.min(2, topSkills.size()))) + " Engineer";
            } else {
                rolePrefix = "Engineer";
            }
        }

        // No skill is appended to the role phrase. The live search tools match `keyword`
        // as a phrase, so "Software Engineer Docker" retrieves nothing (verified against
        // openings-mcp google/amazon/apple/meta search tools, all 0 results) where the
        // role phrase alone retrieves real listings. The candidate's skills are still
        // applied locally by the relevance filter, which is where per-skill judgement
        // belongs — they must not narrow the upstream query itself.
        return rolePrefix;
    }

    private static void addAll(Set<String> target, List<String> values) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                target.add(value.trim());
            }
        }
    }

    private boolean matchesLocation(Job job, String requestedLocation) {
        if (requestedLocation == null || requestedLocation.trim().isEmpty()
                || "all".equalsIgnoreCase(requestedLocation.trim())
                || "any".equalsIgnoreCase(requestedLocation.trim())) {
            return true;
        }
        String queryLoc = requestedLocation.trim().toLowerCase();
        String jobLoc = job.location() != null ? job.location().toLowerCase() : "";
        return jobLoc.contains(queryLoc) || queryLoc.contains(jobLoc);
    }

    private boolean matchesExperience(Job job, String requestedExperience) {
        if (requestedExperience == null || requestedExperience.trim().isEmpty()
                || "all".equalsIgnoreCase(requestedExperience.trim())
                || "any".equalsIgnoreCase(requestedExperience.trim())) {
            return true;
        }
        String queryExp = requestedExperience.trim().toLowerCase();
        String jobExp = job.experienceRequirement() != null ? job.experienceRequirement().toLowerCase() : "";
        if (jobExp.contains(queryExp) || queryExp.contains(jobExp)) {
            return true;
        }
        Set<ExperienceCategory> queryCategories = categorizeExperience(queryExp);
        Set<ExperienceCategory> jobCategories = categorizeExperience(jobExp);
        return queryCategories.stream().anyMatch(jobCategories::contains);
    }

    private Set<ExperienceCategory> categorizeExperience(String text) {
        Set<ExperienceCategory> categories = new HashSet<>();
        if (text == null || text.isBlank()) {
            return categories;
        }
        String lower = text.toLowerCase();
        if (lower.contains("fresh") || lower.contains("entry") || lower.contains("0-1")
                || lower.contains("0-2") || lower.contains("intern") || lower.contains("graduate")
                || lower.contains("trainee") || lower.contains("0 years")) {
            categories.add(ExperienceCategory.FRESHER_ENTRY);
        }
        if (lower.contains("junior") || lower.contains("1-3") || lower.contains("0-2")
                || lower.contains("1-2") || lower.contains("associate") || lower.contains("1 year")
                || lower.contains("2 years")) {
            categories.add(ExperienceCategory.JUNIOR);
        }
        if (lower.contains("mid") || lower.contains("2-4") || lower.contains("3-5")
                || lower.contains("2-5") || lower.contains("3 years") || lower.contains("4 years")
                || lower.contains("3+")) {
            categories.add(ExperienceCategory.MID);
        }
        if (lower.contains("senior") || lower.contains("lead") || lower.contains("staff")
                || lower.contains("principal") || lower.contains("5+") || lower.contains("5-8")
                || lower.contains("6+") || lower.contains("7+") || lower.contains("8+")) {
            categories.add(ExperienceCategory.SENIOR);
        }
        return categories;
    }

    private boolean matchesEmploymentType(Job job, String requestedType) {
        if (requestedType == null || requestedType.trim().isEmpty()
                || "all".equalsIgnoreCase(requestedType.trim())
                || "any".equalsIgnoreCase(requestedType.trim())) {
            return true;
        }
        String queryType = requestedType.trim().toLowerCase().replaceAll("[^a-z]", "");
        String jobType = job.employmentType() != null ? job.employmentType().toLowerCase().replaceAll("[^a-z]", "") : "";
        return jobType.contains(queryType) || queryType.contains(jobType);
    }

    private boolean matchesDatePosted(Job job, String dateFilter) {
        if (dateFilter == null || dateFilter.trim().isEmpty()
                || "all".equalsIgnoreCase(dateFilter.trim())
                || "any".equalsIgnoreCase(dateFilter.trim())) {
            return true;
        }
        if (job.postingDate() == null || job.postingDate().isBlank()) {
            return true;
        }
        String filter = dateFilter.trim().toLowerCase();
        int maxDays;
        switch (filter) {
            case "24h": case "today": case "1d": case "1 day": case "past 24 hours":
                maxDays = 1;
                break;
            case "week": case "7d": case "7 days": case "past week":
                maxDays = 7;
                break;
            case "month": case "30d": case "30 days": case "past month":
                maxDays = 30;
                break;
            default:
                maxDays = -1;
        }
        if (maxDays == -1) {
            return job.postingDate().equalsIgnoreCase(dateFilter.trim());
        }
        try {
            LocalDate postDate = LocalDate.parse(job.postingDate().trim());
            LocalDate today = LocalDate.now();
            long daysBetween = ChronoUnit.DAYS.between(postDate, today);
            return daysBetween >= 0L && daysBetween <= maxDays;
        } catch (DateTimeParseException e) {
            log.debug("Unparseable job posting date '{}', skipping date filter", job.postingDate());
            return true;
        }
    }

    private enum ExperienceCategory {
        FRESHER_ENTRY,
        JUNIOR,
        MID,
        SENIOR
    }
}