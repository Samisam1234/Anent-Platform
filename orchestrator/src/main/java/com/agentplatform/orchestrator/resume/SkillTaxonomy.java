package com.agentplatform.orchestrator.resume;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Centralized skill taxonomy mapping raw skill strings to canonical names
 * and their categories. Used by the deterministic resume parser and
 * {@link com.agentplatform.orchestrator.matching.SkillNormalizer}.
 *
 * <p>All operations are pure-text and deterministic — no external calls.</p>
 */
public final class SkillTaxonomy {

    private SkillTaxonomy() {}

    public enum Category {
        SOFTWARE,
        PROGRAMMING_AI,
        EMBEDDED,
        VLSI_FPGA,
        COMMUNICATION
    }

    private static final Map<String, String> ALIAS_TO_CANONICAL = new HashMap<>();
    private static final Map<String, Category> CANONICAL_TO_CATEGORY = new HashMap<>();

    static {
        // ── SOFTWARE ──
        reg(Category.SOFTWARE, "Java", "core java", "java 21", "java 17", "java 11", "java 8", "java se", "java ee");
        reg(Category.SOFTWARE, "Spring Boot", "springboot", "spring-boot", "spring boot framework", "springboot framework");
        reg(Category.SOFTWARE, "Spring", "spring framework", "spring mvc", "spring core");
        reg(Category.SOFTWARE, "Hibernate", "hibernate orm");
        reg(Category.SOFTWARE, "JPA", "java persistence api", "spring data jpa", "spring data");
        reg(Category.SOFTWARE, "Maven", "apache maven");
        reg(Category.SOFTWARE, "Gradle", "gradle build");
        reg(Category.SOFTWARE, "PostgreSQL", "postgres", "pgsql", "psql", "postgre sql");
        reg(Category.SOFTWARE, "Oracle SQL", "oracle", "oracle database", "pl/sql", "plsql");
        reg(Category.SOFTWARE, "SQL", "sql basics", "relational database", "rdbms", "ansi sql", "mysql");
        reg(Category.SOFTWARE, "HTML", "html5", "html 5");
        reg(Category.SOFTWARE, "CSS", "css3", "css 3");
        reg(Category.SOFTWARE, "JavaScript", "js", "ecmascript", "es6", "vanilla javascript", "es2015");
        reg(Category.SOFTWARE, "Git", "github", "gitlab", "version control");
        reg(Category.SOFTWARE, "Docker", "containerization", "containers");
        reg(Category.SOFTWARE, "REST API", "rest apis", "rest", "restful", "restful apis", "restful api");
        reg(Category.SOFTWARE, "JDBC", "java database connectivity");
        reg(Category.SOFTWARE, "Microservices", "microservice", "micro services", "micro-services");

        // ── PROGRAMMING / AI ──
        reg(Category.PROGRAMMING_AI, "Python", "python3", "python 3");
        reg(Category.PROGRAMMING_AI, "C", "c programming", "c-programming");
        reg(Category.PROGRAMMING_AI, "C++", "cpp", "c / c++", "c/c++");
        reg(Category.PROGRAMMING_AI, "Machine Learning", "ml", "machine-learning");
        reg(Category.PROGRAMMING_AI, "Artificial Intelligence", "ai");
        reg(Category.PROGRAMMING_AI, "Neural Networks", "deep learning", "neural network");
        reg(Category.PROGRAMMING_AI, "LangChain4j", "langchain", "lang chain4j");
        reg(Category.PROGRAMMING_AI, "Ollama");
        reg(Category.PROGRAMMING_AI, "Google Gemini", "gemini", "google ai");

        // ── EMBEDDED / ECE ──
        reg(Category.EMBEDDED, "Embedded C", "c (embedded)", "embedded c programming");
        reg(Category.EMBEDDED, "Embedded Systems", "embedded", "embedded system");
        reg(Category.EMBEDDED, "Arduino", "arduino/esp32", "esp32", "esp8266");
        reg(Category.EMBEDDED, "Microcontrollers", "microcontroller", "mcu", "microcontrollers (arm cortex)", "arm cortex", "arm cortex-m", "arm");
        reg(Category.EMBEDDED, "UART");
        reg(Category.EMBEDDED, "SPI");
        reg(Category.EMBEDDED, "I2C");
        reg(Category.EMBEDDED, "CAN", "can protocol");
        reg(Category.EMBEDDED, "MATLAB", "mat lab");
        reg(Category.EMBEDDED, "Simulink", "simulink");
        reg(Category.EMBEDDED, "Sensors", "sensor");
        reg(Category.EMBEDDED, "Digital Electronics", "digital logic", "digital design", "digital systems");
        reg(Category.EMBEDDED, "Analog Electronics", "analog circuits", "analog design");
        reg(Category.EMBEDDED, "Firmware", "firmware development", "embedded firmware", "bare-metal", "bare metal");
        reg(Category.EMBEDDED, "RTOS", "rtos (freertos)", "freertos", "real-time operating system", "real time operating system");
        reg(Category.EMBEDDED, "PCB Design", "pcb design basics (kicad/eagle)", "kicad", "eagle", "pcb schematics", "pcb layout");

        // ── VLSI / FPGA ──
        reg(Category.VLSI_FPGA, "Verilog", "verilog hdl", "verilog-hdl");
        reg(Category.VLSI_FPGA, "SystemVerilog", "system verilog", "system-verilog", "sv");
        reg(Category.VLSI_FPGA, "VHDL", "vhdl-hdl");
        reg(Category.VLSI_FPGA, "RTL Design", "rtl", "register transfer level", "rtl coding");
        reg(Category.VLSI_FPGA, "FPGA", "fpga development", "fpga design", "xilinx", "xilinx vivado", "vivado", "altera", "intel fpga");
        reg(Category.VLSI_FPGA, "Quartus Prime", "quartus");
        reg(Category.VLSI_FPGA, "ModelSim");
        reg(Category.VLSI_FPGA, "Questa", "questa sim");
        reg(Category.VLSI_FPGA, "Intel FPGA");
        reg(Category.VLSI_FPGA, "Synthesis", "logic synthesis", "synopsys design compiler", "design compiler");
        reg(Category.VLSI_FPGA, "Simulation", "simulation tools");
        reg(Category.VLSI_FPGA, "Digital Design");
        reg(Category.VLSI_FPGA, "ASIC", "asic design", "asic physical design");
        reg(Category.VLSI_FPGA, "SoC", "system on chip");
        reg(Category.VLSI_FPGA, "Static Timing Analysis", "sta", "timing closure (sta)", "timing closure", "timing analysis");
        reg(Category.VLSI_FPGA, "UVM", "universal verification methodology", "uvm verification");
        reg(Category.VLSI_FPGA, "Verification", "verification methodology");
        reg(Category.VLSI_FPGA, "VLSI", "basic vlsi", "vlsi design");
        reg(Category.VLSI_FPGA, "Physical Design", "floorplanning", "cts", "routing");

        // ── COMMUNICATION ──
        reg(Category.COMMUNICATION, "Communication Systems", "communication system", "comms");
        reg(Category.COMMUNICATION, "Wireless", "wireless communications");
        reg(Category.COMMUNICATION, "5G");
        reg(Category.COMMUNICATION, "DSP", "digital signal processing", "signal processing");
    }

    private static void reg(Category category, String canonical, String... aliases) {
        String cleanCanonical = canonical.trim();
        CANONICAL_TO_CATEGORY.put(cleanCanonical, category);
        ALIAS_TO_CANONICAL.put(clean(cleanCanonical), cleanCanonical);
        for (String alias : aliases) {
            ALIAS_TO_CANONICAL.put(clean(alias), cleanCanonical);
        }
    }

    /**
     * Normalizes a raw skill string to its canonical name.
     *
     * @param raw raw skill string; may be {@code null}
     * @return canonical name, or cleaned lowercase form if unknown; empty string for null/blank input
     */
    public static String normalize(String raw) {
        if (raw == null) return "";
        String cleaned = clean(raw);
        if (cleaned.isEmpty()) return "";
        return ALIAS_TO_CANONICAL.getOrDefault(cleaned, cleaned);
    }

    /**
     * Normalizes a collection of raw skill strings, deduplicates, and returns sorted canonical names.
     *
     * @param rawSkills raw skills; may be {@code null}
     * @return deduplicated sorted list of canonical skill names
     */
    public static List<String> normalizeAll(Collection<String> rawSkills) {
        if (rawSkills == null) return List.of();
        Set<String> canonical = new LinkedHashSet<>();
        for (String raw : rawSkills) {
            String n = normalize(raw);
            if (!n.isEmpty()) canonical.add(n);
        }
        List<String> result = new ArrayList<>(canonical);
        Collections.sort(result, String.CASE_INSENSITIVE_ORDER);
        return List.copyOf(result);
    }

    /**
     * Finds all taxonomy skills mentioned anywhere in the given text.
     *
     * @param text text to search; may be {@code null}
     * @return list of canonical skill names found, sorted alphabetically
     */
    public static List<String> findMatches(String text) {
        if (text == null || text.isBlank()) return List.of();
        String lower = text.toLowerCase(Locale.ROOT);
        Set<String> found = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : ALIAS_TO_CANONICAL.entrySet()) {
            String alias = entry.getKey();
            if (alias.length() < 2) continue;
            if (containsAlias(lower, alias)) {
                found.add(entry.getValue());
            }
        }
        List<String> result = new ArrayList<>(found);
        Collections.sort(result, String.CASE_INSENSITIVE_ORDER);
        return List.copyOf(result);
    }

    /**
     * Whether {@code lowerText} contains {@code alias} as a skill reference.
     *
     * <p>Multi-word aliases (e.g. "Spring Boot") use a plain substring check.
     * Single-word aliases (e.g. "CAN", "C") use word-boundary matching to avoid
     * false positives like "candidate" matching the "CAN" protocol alias or
     * "css" matching "C".</p>
     */
    private static boolean containsAlias(String lowerText, String alias) {
        if (alias.contains(" ")) {
            return lowerText.contains(alias);
        }
        // Word-boundary match for single-word aliases (allowing simple plurals),
        // e.g. "microservices" matches alias "microservice"; "candidate" does NOT match "can".
        return Pattern.compile("(?<![a-z0-9/+#])" + Pattern.quote(alias) + "s?(?![a-z0-9/+#])")
                .matcher(lowerText)
                .find();
    }

    /**
     * Returns the category for a canonical skill name.
     *
     * @param canonicalSkill canonical skill name
     * @return the category, or {@code null} if the skill is not in the taxonomy
     */
    public static Category getCategory(String canonicalSkill) {
        if (canonicalSkill == null) return null;
        return CANONICAL_TO_CATEGORY.get(canonicalSkill.trim());
    }

    /**
     * Returns all canonical skill names in the given category.
     */
    public static List<String> skillsInCategory(Category category) {
        if (category == null) return List.of();
        return CANONICAL_TO_CATEGORY.entrySet().stream()
                .filter(e -> e.getValue() == category)
                .map(Map.Entry::getKey)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    /**
     * Returns an unmodifiable view of all known canonical skill names.
     */
    public static Set<String> allCanonicalSkills() {
        return Collections.unmodifiableSet(CANONICAL_TO_CATEGORY.keySet());
    }

    private static String clean(String s) {
        if (s == null) return "";
        return s.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\[\\](){},;:!?'\"]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
