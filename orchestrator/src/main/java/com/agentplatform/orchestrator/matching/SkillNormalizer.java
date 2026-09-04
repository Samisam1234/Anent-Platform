package com.agentplatform.orchestrator.matching;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class SkillNormalizer {
    private static final Map<String, String> CANONICAL_MAP = new HashMap<String, String>();

    private SkillNormalizer() {
    }

    private static void putCanonical(String canonical, String ... variations) {
        String cleanCanonical = SkillNormalizer.clean(canonical);
        CANONICAL_MAP.put(cleanCanonical, cleanCanonical);
        for (String var : variations) {
            CANONICAL_MAP.put(SkillNormalizer.clean(var), cleanCanonical);
        }
    }

    public static String clean(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toLowerCase(Locale.ROOT).replaceAll("[\\[\\](){},;:!?'\"]", "").replaceAll("\\s+", " ");
    }

    public static String canonicalize(String raw) {
        String cleaned = SkillNormalizer.clean(raw);
        if (cleaned.isEmpty()) {
            return "";
        }
        if (CANONICAL_MAP.containsKey(cleaned)) {
            return CANONICAL_MAP.get(cleaned);
        }
        String alphaNum = cleaned.replaceAll("[^a-z0-9]", "");
        for (Map.Entry<String, String> entry : CANONICAL_MAP.entrySet()) {
            String entryAlpha = entry.getKey().replaceAll("[^a-z0-9]", "");
            if (!entryAlpha.equals(alphaNum) || entryAlpha.isEmpty()) continue;
            return entry.getValue();
        }
        return cleaned;
    }

    public static boolean isMatch(String candidateSkill, String jobSkill) {
        if (candidateSkill == null || jobSkill == null) {
            return false;
        }
        String candClean = SkillNormalizer.clean(candidateSkill);
        String jobClean = SkillNormalizer.clean(jobSkill);
        if (candClean.isEmpty() || jobClean.isEmpty()) {
            return false;
        }
        if (candClean.equalsIgnoreCase(jobClean)) {
            return true;
        }
        String candCanon = SkillNormalizer.canonicalize(candClean);
        String jobCanon = SkillNormalizer.canonicalize(jobClean);
        if (!candCanon.isEmpty() && candCanon.equals(jobCanon)) {
            return true;
        }
        return (candClean.contains(jobClean) || jobClean.contains(candClean)) && candClean.length() >= 3 && jobClean.length() >= 3;
    }

    static {
        SkillNormalizer.putCanonical("postgresql", "postgres", "pgsql", "psql", "postgre sql");
        SkillNormalizer.putCanonical("sql", "sql basics", "relational database", "rdbms", "ansi sql");
        SkillNormalizer.putCanonical("spring boot", "springboot", "spring-boot", "spring framework", "spring");
        SkillNormalizer.putCanonical("hibernate/jpa", "hibernate", "jpa", "spring data jpa", "spring data");
        SkillNormalizer.putCanonical("rest apis", "rest api", "rest", "restful", "restful apis", "restful api");
        SkillNormalizer.putCanonical("microservices", "microservice", "micro services", "micro-services");
        SkillNormalizer.putCanonical("kafka", "apache kafka");
        SkillNormalizer.putCanonical("redis", "redis cache");
        SkillNormalizer.putCanonical("java", "core java", "java 21", "java 17", "java 11", "java 8", "java se", "java ee");
        SkillNormalizer.putCanonical("javascript", "js", "ecmascript", "es6", "vanilla javascript");
        SkillNormalizer.putCanonical("typescript", "ts");
        SkillNormalizer.putCanonical("python", "python3", "python 3");
        SkillNormalizer.putCanonical("c++", "cpp", "c / c++", "c/c++");
        SkillNormalizer.putCanonical("c", "c programming", "c-programming");
        SkillNormalizer.putCanonical("html5", "html", "html 5");
        SkillNormalizer.putCanonical("css3", "css", "css 3");
        SkillNormalizer.putCanonical("react", "react.js", "reactjs");
        SkillNormalizer.putCanonical("node.js", "nodejs", "node");
        SkillNormalizer.putCanonical("docker", "containerization", "containers");
        SkillNormalizer.putCanonical("kubernetes", "k8s");
        SkillNormalizer.putCanonical("git", "github", "gitlab", "version control");
        SkillNormalizer.putCanonical("maven", "apache maven");
        SkillNormalizer.putCanonical("ci/cd", "cicd", "ci-cd", "ci cd", "continuous integration");
        SkillNormalizer.putCanonical("aws", "amazon web services", "amazon aws");
        SkillNormalizer.putCanonical("vlsi", "basic vlsi", "vlsi design", "asic design");
        SkillNormalizer.putCanonical("rtl design", "rtl", "register transfer level", "rtl coding");
        SkillNormalizer.putCanonical("verilog", "verilog hdl", "verilog-hdl");
        SkillNormalizer.putCanonical("systemverilog", "system verilog", "system-verilog", "sv");
        SkillNormalizer.putCanonical("uvm", "universal verification methodology", "uvm verification");
        SkillNormalizer.putCanonical("fpga", "fpga development", "xilinx", "xilinx vivado", "vivado", "altera", "intel fpga");
        SkillNormalizer.putCanonical("vhdl", "vhdl-hdl");
        SkillNormalizer.putCanonical("embedded c", "c (embedded)", "embedded c programming");
        SkillNormalizer.putCanonical("microcontrollers", "microcontroller", "mcu", "microcontrollers (arm cortex)", "arm cortex", "arm cortex-m", "arm");
        SkillNormalizer.putCanonical("rtos", "rtos (freertos)", "freertos", "real-time operating system", "real time operating system");
        SkillNormalizer.putCanonical("sta", "static timing analysis", "timing closure (sta)", "timing closure", "timing analysis");
        SkillNormalizer.putCanonical("physical design", "asic physical design", "floorplanning", "cts", "routing");
        SkillNormalizer.putCanonical("logic synthesis", "synthesis", "synopsys design compiler", "design compiler");
        SkillNormalizer.putCanonical("i2c/spi/uart", "i2c", "spi", "uart", "can", "can protocol", "serial communication");
        SkillNormalizer.putCanonical("digital electronics", "digital logic", "digital design", "digital systems");
        SkillNormalizer.putCanonical("firmware", "firmware development", "embedded firmware", "bare-metal");
        SkillNormalizer.putCanonical("pcb design", "pcb design basics (kicad/eagle)", "kicad", "eagle", "pcb schematics", "pcb layout");
        SkillNormalizer.putCanonical("arduino/esp32", "arduino", "esp32", "esp8266", "sensors & microcontrollers");
        SkillNormalizer.putCanonical("oop", "oops", "object oriented programming", "object-oriented design");
        SkillNormalizer.putCanonical("data structures", "data structures and algorithms", "dsa");
    }
}

