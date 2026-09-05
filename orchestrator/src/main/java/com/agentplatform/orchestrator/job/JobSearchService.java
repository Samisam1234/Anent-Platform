package com.agentplatform.orchestrator.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;

/**
 * Orchestrates job discovery across configured {@link JobSource}s,
 * deduplicates listings via {@link JobDeduplicationService}, and applies
 * deterministic keyword/location/experience/type/date filters.
 */
@Service
public class JobSearchService {

    private static final Logger log = LoggerFactory.getLogger(JobSearchService.class);

    private final List<JobSource> jobSources;
    private final JobDeduplicationService deduplicationService;

    public JobSearchService(List<JobSource> jobSources, JobDeduplicationService deduplicationService) {
        this.deduplicationService = deduplicationService != null
                ? deduplicationService
                : new JobDeduplicationService();

        if (jobSources != null && !jobSources.isEmpty()) {
            this.jobSources = List.copyOf(jobSources);
        } else {
            this.jobSources = List.of(new MockJobSource());
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
        log.info("Starting job search: keywords={}, location='{}', experience='{}', type='{}', date='{}', limit={}",
                request.keywords(), request.location(), request.experience(),
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
        for (JobSource source : activeSources) {
            try {
                List<Job> jobs = source.search(request);
                if (jobs != null) {
                    rawListings.addAll(jobs);
                }
                sourceNames.add(source.getSourceName());
                if (source.isLive()) {
                    anyLive = true;
                }
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

        List<Job> filtered = deduplicated.stream()
                .filter(JobSearchService::hasMinimalQuality)
                .filter(job -> matchesKeywords(job, request.keywords()))
                .filter(job -> matchesLocation(job, request.location()))
                .filter(job -> matchesSource(job, request.source()))
                .filter(job -> matchesExperience(job, request.experience()))
                .filter(job -> matchesEmploymentType(job, request.employmentType()))
                .filter(job -> matchesDatePosted(job, request.datePosted()))
                .toList();
        int afterFilterCount = filtered.size();

        int limit = request.limit() != null && request.limit() > 0 ? request.limit() : 20;
        List<Job> limitedResults = filtered.stream().limit(limit).toList();

        long durationMs = System.currentTimeMillis() - startTime;
        String combinedSourceName = String.join(", ", sourceNames);
        String message = anyLive
                ? "Live job search completed successfully."
                : "Live job source not configured. Returning development mock data.";
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

    private boolean matchesKeywords(Job job, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return true;
        }
        List<String> cleanKeywords = keywords.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(k -> !k.isEmpty())
                .map(String::toLowerCase)
                .toList();
        if (cleanKeywords.isEmpty()) {
            return true;
        }
        String title = job.title() != null ? job.title().toLowerCase() : "";
        String desc = job.description() != null ? job.description().toLowerCase() : "";
        String company = job.company() != null ? job.company().toLowerCase() : "";
        List<String> skills = new ArrayList<>();
        if (job.requiredSkills() != null) {
            job.requiredSkills().forEach(s -> skills.add(s.toLowerCase()));
        }
        if (job.preferredSkills() != null) {
            job.preferredSkills().forEach(s -> skills.add(s.toLowerCase()));
        }
        for (String keyword : cleanKeywords) {
            if (title.contains(keyword) || desc.contains(keyword) || company.contains(keyword)) {
                return true;
            }
            if (skills.stream().anyMatch(skill -> skill.contains(keyword) || skill.equalsIgnoreCase(keyword))) {
                return true;
            }
        }
        return false;
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