package com.agentplatform.orchestrator.matching;

import com.agentplatform.orchestrator.job.Job;
import com.agentplatform.orchestrator.job.JobSearchRequest;
import com.agentplatform.orchestrator.job.JobSearchResult;
import com.agentplatform.orchestrator.job.JobSearchService;
import com.agentplatform.orchestrator.matching.CareerTrack;
import com.agentplatform.orchestrator.matching.CareerTrackEngine;
import com.agentplatform.orchestrator.matching.EducationMatchingEngine;
import com.agentplatform.orchestrator.matching.ExperienceMatchLevel;
import com.agentplatform.orchestrator.matching.ExperienceMatchingEngine;
import com.agentplatform.orchestrator.matching.ExplanationGenerator;
import com.agentplatform.orchestrator.matching.JobMatch;
import com.agentplatform.orchestrator.matching.JobMatchRequest;
import com.agentplatform.orchestrator.matching.JobMatchResult;
import com.agentplatform.orchestrator.matching.JobMatchingConfig;
import com.agentplatform.orchestrator.matching.LocationMatchingEngine;
import com.agentplatform.orchestrator.matching.RecommendationLevel;
import com.agentplatform.orchestrator.matching.RoleMatchingEngine;
import com.agentplatform.orchestrator.matching.SkillMatchingEngine;
import com.agentplatform.orchestrator.resume.CandidateProfile;
import com.agentplatform.orchestrator.resume.entity.CandidateProfileEntity;
import com.agentplatform.orchestrator.resume.persistence.CandidateProfilePersistenceService;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class JobMatchingService {
    private static final Logger log = LoggerFactory.getLogger(JobMatchingService.class);
    private static final String SUPPLIED_JOBS_SOURCE = "user-provided";
    private final JobSearchService jobSearchService;
    private final CandidateProfilePersistenceService persistenceService;
    private final SkillMatchingEngine skillMatchingEngine;
    private final RoleMatchingEngine roleMatchingEngine;
    private final LocationMatchingEngine locationMatchingEngine;
    private final ExperienceMatchingEngine experienceMatchingEngine;
    private final EducationMatchingEngine educationMatchingEngine;
    private final CareerTrackEngine careerTrackEngine;
    private final ExplanationGenerator explanationGenerator;
    private final JobMatchingConfig config;

    public JobMatchingService(JobSearchService jobSearchService, CandidateProfilePersistenceService persistenceService, SkillMatchingEngine skillMatchingEngine, RoleMatchingEngine roleMatchingEngine, LocationMatchingEngine locationMatchingEngine, ExperienceMatchingEngine experienceMatchingEngine, EducationMatchingEngine educationMatchingEngine, CareerTrackEngine careerTrackEngine, ExplanationGenerator explanationGenerator, JobMatchingConfig config) {
        this.jobSearchService = jobSearchService;
        this.persistenceService = persistenceService;
        this.skillMatchingEngine = skillMatchingEngine;
        this.roleMatchingEngine = roleMatchingEngine;
        this.locationMatchingEngine = locationMatchingEngine;
        this.experienceMatchingEngine = experienceMatchingEngine;
        this.educationMatchingEngine = educationMatchingEngine;
        this.careerTrackEngine = careerTrackEngine;
        this.explanationGenerator = explanationGenerator;
        this.config = config;
    }

    public JobMatchResult matchJobs(JobMatchRequest request) {
        String resultMsg;
        boolean live;
        String source;
        List<Job> candidateJobs;
        boolean suppliedJobs;
        String candidateName;
        Long candidateId;
        CandidateProfile candidate;
        if (request == null) {
            throw new IllegalArgumentException("JobMatchRequest must not be null");
        }
        long startTime = System.currentTimeMillis();
        if (request.candidateProfile() != null) {
            candidate = request.candidateProfile();
            candidateId = request.candidateProfileId();
            candidateName = candidate.name();
        } else {
            CandidateProfileEntity profileEntity = request.candidateProfileId() != null ? this.persistenceService.getByIdOrThrow(request.candidateProfileId()) : this.persistenceService.getLatestOrThrow();
            candidate = profileEntity.toDomain();
            candidateId = profileEntity.getId();
            candidateName = profileEntity.getName();
        }
        log.info("Starting job matching for candidate ID {}: keywords={}, location='{}', limit={}", new Object[]{candidateId, request.keywords(), request.location(), request.limit()});
        boolean bl = suppliedJobs = request.jobs() != null && !request.jobs().isEmpty();
        if (suppliedJobs) {
            candidateJobs = request.jobs();
            source = SUPPLIED_JOBS_SOURCE;
            live = false;
            resultMsg = "Matched supplied job listings against candidate profile.";
        } else {
            // No free-text keywords are collected from the UI any more. Passing the resolved
            // candidateId lets JobSearchService derive relevance keywords from the parsed
            // profile's skills, so discovery follows the resume. Explicit keywords, when a
            // caller still supplies them, continue to take precedence.
            JobSearchRequest searchReq = new JobSearchRequest(request.keywords(), request.location(), request.experience(), request.employmentType(), null, 100, null, candidateId);
            JobSearchResult searchResult = this.jobSearchService.search(searchReq);
            candidateJobs = searchResult.jobs();
            source = searchResult.source();
            live = searchResult.live();
            // Provenance is reported separately via `source`; the message must not claim a
            // "mock job catalog" for every non-live result, because non-live also covers
            // user-supplied listings and any offline source.
            resultMsg = live
                    ? "Live job matching completed successfully."
                    : "Matched job listings against candidate profile.";
        }
        if (candidateJobs.isEmpty()) {
            log.info("No jobs discovered for matching candidate ID {}", (Object)candidateId);
            return new JobMatchResult(candidateId, candidateName, 0, List.of(), source, live, "No job listings were found matching the initial search parameters.");
        }
        ArrayList<JobMatch> evaluatedMatches = new ArrayList<JobMatch>();
        for (Job job : candidateJobs) {
            if (job == null) continue;
            try {
                evaluatedMatches.add(this.evaluateJob(candidate, job));
            }
            catch (Exception e) {
                log.warn("Scoring failed for job '{}', falling back to heuristic keyword matching", (Object)job.title(), (Object)e);
                evaluatedMatches.add(this.heuristicMatch(candidate, job));
            }
        }
        List<JobMatch> filteredMatches = evaluatedMatches.stream()
        // Listings whose required skills the candidate barely covers are dropped here rather
        // than ranked as weak matches.
        .filter(this::meetsRequiredSkillOverlap)
        .filter(m -> {
            if (request.minScore() != null) {
                return m.matchScore() >= request.minScore();
            }
            return true;
        }).filter(m -> {
            if (request.trackFilter() != null && request.trackFilter() != CareerTrack.UNKNOWN) {
                return m.careerTrack() == request.trackFilter() || m.careerTrack() == CareerTrack.MIXED;
            }
            return true;
        }).toList();
        List<JobMatch> sortedMatches = this.rank(filteredMatches);
        int limit = request.limit() != null && request.limit() > 0 ? request.limit() : 20;
        List<JobMatch> limitedMatches = sortedMatches.stream().limit(limit).toList();
        long durationMs = System.currentTimeMillis() - startTime;
        int highestScore = sortedMatches.isEmpty() ? 0 : sortedMatches.get(0).matchScore();
        int lowestScore = sortedMatches.isEmpty() ? 0 : sortedMatches.get(sortedMatches.size() - 1).matchScore();
        double averageScore = filteredMatches.isEmpty() ? 0.0 : filteredMatches.stream().mapToInt(JobMatch::matchScore).average().orElse(0.0);
        long strongMatchCount = filteredMatches.stream().filter(m -> m.recommendation() == RecommendationLevel.EXCELLENT_MATCH || m.recommendation() == RecommendationLevel.STRONG_MATCH).count();
        log.info("Job matching completed in {} ms: candidateId={}, retrieved={}, evaluated={}, returned={}, highestScore={}, lowestScore={}, averageScore={}, strongMatches={}", new Object[]{durationMs, candidateId, candidateJobs.size(), evaluatedMatches.size(), limitedMatches.size(), highestScore, lowestScore, String.format("%.1f", averageScore), strongMatchCount});
        return new JobMatchResult(candidateId, candidateName, sortedMatches.size(), limitedMatches, source, live, resultMsg);
    }

    public JobMatchResult matchProfileAgainstJobs(CandidateProfile candidate, List<Job> jobs, Integer minScore, CareerTrack trackFilter, Integer limit) {
        if (candidate == null) {
            throw new IllegalArgumentException("CandidateProfile must not be null");
        }
        long startTime = System.currentTimeMillis();
        List<Job> jobList = jobs != null ? jobs : List.of();
        ArrayList<JobMatch> evaluatedMatches = new ArrayList<JobMatch>();
        for (Job job : jobList) {
            if (job == null) continue;
            evaluatedMatches.add(this.evaluateJob(candidate, job));
        }
        List<JobMatch> filteredMatches = evaluatedMatches.stream()
        .filter(this::meetsRequiredSkillOverlap)
        .filter(m -> {
            if (minScore != null) {
                return m.matchScore() >= minScore;
            }
            return true;
        }).filter(m -> {
            if (trackFilter != null && trackFilter != CareerTrack.UNKNOWN) {
                return m.careerTrack() == trackFilter || m.careerTrack() == CareerTrack.MIXED;
            }
            return true;
        }).toList();
        List<JobMatch> sortedMatches = this.rank(filteredMatches);
        int cap = limit != null && limit > 0 ? limit : 20;
        List<JobMatch> limitedMatches = sortedMatches.stream().limit(cap).toList();
        long durationMs = System.currentTimeMillis() - startTime;
        log.info("Inline job matching completed in {} ms: candidate='{}', evaluated={}, returned={}", new Object[]{durationMs, candidate.name(), evaluatedMatches.size(), limitedMatches.size()});
        return new JobMatchResult(null, candidate.name(), sortedMatches.size(), limitedMatches, SUPPLIED_JOBS_SOURCE, false, "Matched supplied job listings against candidate profile.");
    }

    private JobMatch evaluateJob(CandidateProfile candidate, Job job) {
        SkillMatchingEngine.SkillEvaluation skillEval = this.skillMatchingEngine.evaluate(candidate, job);
        RoleMatchingEngine.RoleEvaluation roleEval = this.roleMatchingEngine.evaluate(candidate, job);
        LocationMatchingEngine.LocationEvaluation locEval = this.locationMatchingEngine.evaluate(candidate, job);
        ExperienceMatchingEngine.ExperienceEvaluation expEval = this.experienceMatchingEngine.evaluate(candidate, job);
        CareerTrackEngine.CareerTrackEvaluation trackEval = this.careerTrackEngine.evaluate(candidate, job);
        EducationMatchingEngine.EducationEvaluation eduEval = this.educationMatchingEngine.evaluate(candidate, job);
        // Weighted factor score, unchanged: the sub-scores keep their configured weights.
        double baseScore = (skillEval.skillScore() * this.config.getSkillWeight()
                + roleEval.roleScore() * this.config.getRoleWeight()
                + locEval.locationScore() * this.config.getLocationWeight()
                + expEval.experienceScore() * this.config.getExperienceWeight()
                + trackEval.trackScore() * this.config.getTrackWeight()
                + eduEval.educationScore() * this.config.getEducationWeight()) * 100.0;

        // Career-track and location problems are applied multiplicatively on top of the
        // weighted score. Additive weights alone let strong education/experience/location
        // sub-scores carry a listing with almost no skill relevance to 40-50%, which
        // reads as a real match when it is not.
        boolean trackMismatch = isTrackMismatch(trackEval);
        boolean locationMismatch = isLocationMismatch(candidate, job, locEval);

        double running = baseScore;
        List<String> penaltyConcerns = new ArrayList<String>();
        List<String> penaltySteps = new ArrayList<String>();
        if (trackMismatch) {
            double penalty = penaltyFactor(this.config.getTrackMismatchPenalty(), "track-mismatch");
            running *= penalty;
            String detail = trackMismatchDetail(candidate, trackEval);
            penaltySteps.add("career-track mismatch (" + detail + ") cut it by "
                    + percent(1.0 - penalty));
            penaltyConcerns.add("Career-track mismatch: " + detail
                    + ". This reduced the score by " + percent(1.0 - penalty) + ".");
        }
        if (locationMismatch) {
            double penalty = penaltyFactor(this.config.getLocationMismatchPenalty(), "location-mismatch");
            running *= penalty;
            String detail = locationMismatchDetail(candidate, job);
            penaltySteps.add("location mismatch (" + detail + ") cut it by " + percent(1.0 - penalty));
            penaltyConcerns.add("Location mismatch: " + detail
                    + ". This reduced the score by " + percent(1.0 - penalty) + ".");
        }

        int matchScore = Math.max(0, Math.min(100, (int)Math.round(running)));
        RecommendationLevel recommendation = RecommendationLevel.fromScore(matchScore);
        String explanation = this.explanationGenerator.generate(candidate, job, matchScore, recommendation, skillEval.matchedRequiredSkills(), skillEval.missingRequiredSkills(), locEval.locationMatch(), expEval.experienceMatch(), trackEval.jobTrack());
        if (!penaltySteps.isEmpty()) {
            // State the arithmetic so the explanation matches the number actually shown.
            explanation = explanation + " Score adjustment: the weighted factors gave "
                    + Math.round(baseScore) + "%, then " + String.join(" and ", penaltySteps)
                    + ", giving the final " + matchScore + "%.";
        }
        List<String> strengths = this.explanationGenerator.generateStrengths(skillEval.matchedRequiredSkills(), skillEval.matchedPreferredSkills(), locEval.locationMatch(), roleEval.roleMatch(), expEval.experienceMatch(), eduEval.educationScore());
        List<String> generatedConcerns = this.explanationGenerator.generateConcerns(skillEval.missingRequiredSkills(), skillEval.missingPreferredSkills(), locEval.locationMatch(), roleEval.roleMatch(), job, expEval.experienceMatch());
        List<String> concerns = new ArrayList<String>(generatedConcerns != null ? generatedConcerns : List.<String>of());
        concerns.addAll(penaltyConcerns);
        return new JobMatch(job, matchScore, recommendation, skillEval.matchedRequiredSkills(), skillEval.missingRequiredSkills(), skillEval.matchedPreferredSkills(), skillEval.missingPreferredSkills(), locEval.locationMatch(), roleEval.roleMatch(), expEval.experienceMatch(), trackEval.jobTrack(), explanation, strengths, concerns, skillEval.skillScore(), roleEval.roleScore(), locEval.locationScore(), expEval.experienceScore(), trackEval.trackScore(), eduEval.educationScore());
    }

    /**
     * Whether the job is a genuine career-track mismatch.
     *
     * <p>Reuses {@link CareerTrackEngine}'s own judgement instead of introducing a second
     * classifier: it scores an incompatible track at 0.3 (software role vs hardware-only
     * candidate, or the reverse), a neutral/unknown track at 0.5-0.8, and an aligned one
     * at 0.85-1.0. Anything at or below 0.3 is therefore "wrong track".</p>
     */
    private boolean isTrackMismatch(CareerTrackEngine.CareerTrackEvaluation trackEval) {
        return trackEval != null && trackEval.trackScore() <= 0.3;
    }

    /**
     * Whether the location genuinely differs.
     *
     * <p>Deliberately narrower than {@code !locationMatch}. The engine also reports a
     * non-match when the listing states no location at all, and penalizing a job for not
     * publishing a location would be wrong. The penalty applies only when the job names a
     * place, the candidate names a place, and they do not correspond.</p>
     */
    private boolean isLocationMismatch(CandidateProfile candidate, Job job,
                                       LocationMatchingEngine.LocationEvaluation locEval) {
        if (locEval == null || locEval.locationMatch() || candidate == null || job == null) {
            return false;
        }
        boolean jobDeclaresLocation = job.location() != null && !job.location().isBlank();
        boolean candidateDeclaresLocation = (candidate.location() != null && !candidate.location().isBlank())
                || (candidate.preferredLocations() != null && !candidate.preferredLocations().isEmpty());
        return jobDeclaresLocation && candidateDeclaresLocation;
    }

    /**
     * Guards a configured penalty multiplier. A value outside (0, 1] is a misconfiguration;
     * it is ignored rather than silently zeroing or inflating every score.
     */
    private double penaltyFactor(double configured, String what) {
        if (configured <= 0.0 || configured > 1.0) {
            log.warn("Ignoring invalid {} penalty {} (must be within (0, 1])", (Object)what, (Object)configured);
            return 1.0;
        }
        return configured;
    }

    private static String percent(double fraction) {
        return Math.round(fraction * 100.0) + "%";
    }

    private String trackMismatchDetail(CandidateProfile candidate, CareerTrackEngine.CareerTrackEvaluation trackEval) {
        String jobTrack = trackEval != null && trackEval.jobTrack() != null
                ? trackEval.jobTrack().name().toLowerCase(Locale.ROOT)
                : "unknown";
        boolean hasSoftware = candidate != null && candidate.softwareSkills() != null
                && !candidate.softwareSkills().isEmpty();
        boolean hasHardware = candidate != null && candidate.hardwareSkills() != null
                && !candidate.hardwareSkills().isEmpty();
        String candidateTrack = hasSoftware && hasHardware ? "both software and hardware"
                : hasSoftware ? "software-focused"
                : hasHardware ? "hardware/embedded-focused"
                : "no clear technical track";
        return "the role reads as " + jobTrack + " while your profile is " + candidateTrack;
    }

    private String locationMismatchDetail(CandidateProfile candidate, Job job) {
        String jobLoc = job.location() != null ? job.location().trim() : "";
        List<String> preferred = candidate.preferredLocations() != null ? candidate.preferredLocations() : List.<String>of();
        String candidateLoc = !preferred.isEmpty() ? preferred.get(0)
                : (candidate.location() != null ? candidate.location().trim() : "");
        return "the role is in " + jobLoc + " while you are in " + candidateLoc;
    }

    /**
     * Required-skill coverage of an evaluated match, or {@code null} when the job declares
     * no required skills at all and coverage is therefore undefined.
     */
    private static Double requiredSkillOverlap(JobMatch match) {
        int matched = match.matchedRequiredSkills() != null ? match.matchedRequiredSkills().size() : 0;
        int missing = match.missingRequiredSkills() != null ? match.missingRequiredSkills().size() : 0;
        int total = matched + missing;
        return total == 0 ? null : (double)matched / (double)total;
    }

    /**
     * Whether a match clears the minimum required-skill overlap.
     *
     * <p>Jobs that declare no required skills are kept: several sources publish no skill
     * requirements, and filtering them on undefined coverage would discard entire sources
     * rather than weak matches.</p>
     */
    private boolean meetsRequiredSkillOverlap(JobMatch match) {
        Double overlap = requiredSkillOverlap(match);
        if (overlap == null) {
            return true;
        }
        double threshold = this.config.getMinRequiredSkillOverlap();
        if (threshold <= 0.0) {
            return true;
        }
        return overlap >= threshold;
    }

    private List<JobMatch> rank(List<JobMatch> matches) {
        return matches.stream().sorted(Comparator.comparingInt(JobMatch::matchScore).reversed().thenComparing(JobMatch::skillScore, Comparator.reverseOrder()).thenComparing(JobMatch::experienceScore, Comparator.reverseOrder()).thenComparing(j -> this.titleOrEmpty((JobMatch)j), Comparator.naturalOrder()).thenComparing(j -> this.companyOrEmpty((JobMatch)j), Comparator.naturalOrder())).toList();
    }

    private String titleOrEmpty(JobMatch match) {
        return match.job() != null && match.job().title() != null ? match.job().title() : "";
    }

    private String companyOrEmpty(JobMatch match) {
        return match.job() != null && match.job().company() != null ? match.job().company() : "";
    }

    private JobMatch heuristicMatch(CandidateProfile candidate, Job job) {
        HashSet<String> candidateSkills = new HashSet<String>();
        if (candidate.skills() != null) {
            candidateSkills.addAll(candidate.skills().stream().map(String::toLowerCase).toList());
        }
        if (candidate.softwareSkills() != null) {
            candidateSkills.addAll(candidate.softwareSkills().stream().map(String::toLowerCase).toList());
        }
        if (candidate.hardwareSkills() != null) {
            candidateSkills.addAll(candidate.hardwareSkills().stream().map(String::toLowerCase).toList());
        }
        List<String> requiredSkills = job.requiredSkills() != null ? job.requiredSkills() : List.of();
        List<String> preferredSkills = job.preferredSkills() != null ? job.preferredSkills() : List.of();
        long matchedRequired = requiredSkills.stream().filter(s -> s != null && candidateSkills.contains(s.toLowerCase())).count();
        long matchedPreferred = preferredSkills.stream().filter(s -> s != null && candidateSkills.contains(s.toLowerCase())).count();
        double skillScore = requiredSkills.isEmpty() ? 1.0 : (double)matchedRequired / (double)requiredSkills.size();
        double prefBonus = !preferredSkills.isEmpty() && matchedPreferred > 0L ? 0.1 * ((double)matchedPreferred / (double)preferredSkills.size()) : 0.0;
        int matchScore = Math.max(0, Math.min(100, (int)Math.round((skillScore + prefBonus) * 100.0)));
        RecommendationLevel recommendation = RecommendationLevel.fromScore(matchScore);
        String explanation = "Heuristic match based on keyword overlap.";
        List<String> strengths = matchedRequired > 0L ? List.of("Matches " + matchedRequired + " required skill(s) by keyword.") : List.of("No matching keywords found.");
        List<String> concerns = matchedRequired == 0L && !requiredSkills.isEmpty() ? List.of("No required skills matched.") : List.of();
        return new JobMatch(job, matchScore, recommendation, requiredSkills.stream().filter(s -> s != null && candidateSkills.contains(s.toLowerCase())).toList(), requiredSkills.stream().filter(s -> s != null && !candidateSkills.contains(s.toLowerCase())).toList(), preferredSkills.stream().filter(s -> s != null && candidateSkills.contains(s.toLowerCase())).toList(), preferredSkills.stream().filter(s -> s != null && !candidateSkills.contains(s.toLowerCase())).toList(), false, false, ExperienceMatchLevel.NO_MATCH, CareerTrack.UNKNOWN, explanation, strengths, concerns, skillScore, skillScore, 0.0, 0.0, 0.0, 0.0);
    }
}

