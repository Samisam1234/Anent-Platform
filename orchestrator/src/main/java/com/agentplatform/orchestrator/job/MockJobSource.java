package com.agentplatform.orchestrator.job;

import com.agentplatform.orchestrator.job.Job;
import com.agentplatform.orchestrator.job.JobSearchRequest;
import com.agentplatform.orchestrator.job.JobSource;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MockJobSource
implements JobSource {
    private static final Logger log = LoggerFactory.getLogger(MockJobSource.class);
    public static final String SOURCE_NAME = "MOCK_SOURCE";
    private final List<Job> mockCatalog = List.of(new Job("mock-sw-001", "Java Developer", "TechNova Solutions", "Hyderabad, India", "Looking for a motivated Java Developer to build high-throughput microservices using Spring Boot, REST APIs, and PostgreSQL. Opportunity to work on distributed cloud systems.", List.of("Java", "Spring Boot", "REST APIs", "PostgreSQL", "Git"), List.of("Docker", "Kafka", "AWS", "JUnit"), "Fresher / 0-1 years", "FULL_TIME", "2026-08-25", "MOCK_SOURCE", null, "MOCK", null), new Job("mock-sw-002", "Backend Engineer - Spring Boot", "CloudScale Systems", "Hyderabad, India", "Join our core backend engineering team building enterprise SaaS applications. Responsible for designing clean API contracts, database schema design, and microservices architecture.", List.of("Java 21", "Spring Boot", "Microservices", "Hibernate/JPA", "SQL"), List.of("Redis", "Kubernetes", "CI/CD", "Maven"), "1-3 years", "FULL_TIME", "2026-08-26", "MOCK_SOURCE", null, "MOCK", null), new Job("mock-sw-003", "Java Full Stack Developer", "InnoByte Technologies", "Hyderabad, India", "Develop end-to-end web applications with modern frontend architectures and Java Spring Boot backend services. Experience with responsive UIs and robust REST APIs required.", List.of("Java", "Spring Boot", "JavaScript", "HTML5", "CSS3", "SQL"), List.of("React", "TypeScript", "Docker", "PostgreSQL"), "2-4 years", "FULL_TIME", "2026-08-24", "MOCK_SOURCE", null, "MOCK", null), new Job("mock-sw-004", "Senior Backend Developer (Java & Distributed Systems)", "Aegis Data Labs", "Hyderabad, India", "Lead the architecture and implementation of distributed event-driven microservices processing millions of daily transactions. Strong background in concurrency and performance tuning.", List.of("Java", "Spring Boot", "Kafka", "Distributed Systems", "PostgreSQL"), List.of("Ollama/AI Integration", "AWS", "gRPC", "Observability"), "5+ years", "FULL_TIME", "2026-08-22", "MOCK_SOURCE", null, "MOCK", null), new Job("mock-sw-005", "Software Engineer - Java Trainee / Intern", "Nexus Software Labs", "Hyderabad, India", "Exciting opportunity for fresh graduates with strong core Java fundamentals, object-oriented design skills, and passion for backend web development.", List.of("Core Java", "OOP", "Data Structures", "SQL Basics"), List.of("Spring Boot", "Git", "Maven"), "Fresher", "INTERNSHIP", "2026-08-27", "MOCK_SOURCE", null, "MOCK", null), new Job("mock-sw-006", "Cloud Java Software Engineer", "SkyPeak Technologies", "Bengaluru, India", "Build cloud-native serverless and containerized microservices utilizing Java, Spring Cloud, and AWS serverless infrastructure.", List.of("Java", "Spring Boot", "AWS", "Docker", "REST"), List.of("Terraform", "DynamoDB", "CloudWatch"), "3-5 years", "FULL_TIME", "2026-08-20", "MOCK_SOURCE", null, "MOCK", null), new Job("mock-sw-007", "Backend Platform Engineer", "QuantumStream Networks", "Remote", "Develop real-time data ingestion pipelines and API gateway services in Java/Spring ecosystem. Fully remote role across India.", List.of("Java", "Spring Boot", "Reactive Streams", "Redis"), List.of("RabbitMQ", "Micrometer", "Prometheus"), "2-4 years", "CONTRACT", "2026-08-23", "MOCK_SOURCE", null, "MOCK", null), new Job("mock-hw-001", "VLSI Design Engineer", "SiliconCraft Semi", "Hyderabad, India", "Responsible for RTL design, logic synthesis, and static timing analysis for next-generation SoC designs. Deep knowledge of Verilog/SystemVerilog required.", List.of("VLSI", "RTL Design", "Verilog", "SystemVerilog", "Logic Synthesis"), List.of("STA", "Synopsys Design Compiler", "Innovus", "Perl/Python"), "1-3 years", "FULL_TIME", "2026-08-26", "MOCK_SOURCE", null, "MOCK", null), new Job("mock-hw-002", "RTL Design & Verification Engineer", "Apex Microelectronics", "Hyderabad, India", "Design digital blocks and write comprehensive verification testbenches using UVM and SystemVerilog. Simulate and debug multi-clock domain digital systems.", List.of("RTL Design", "Verilog", "SystemVerilog", "UVM", "Digital Design"), List.of("FPGA", "VCS/ModelSim", "Protocol Knowledge (AXI/AHB)", "C/C++"), "2-4 years", "FULL_TIME", "2026-08-25", "MOCK_SOURCE", null, "MOCK", null), new Job("mock-hw-003", "FPGA Development Engineer", "CorePulse Technologies", "Hyderabad, India", "Implement high-speed digital signal processing and communication interfaces on Xilinx/Intel FPGA platforms. RTL synthesis, timing closure, and lab board bring-up.", List.of("FPGA", "Verilog", "VHDL", "Xilinx Vivado", "Timing Closure"), List.of("DSP Algorithms", "PCIe", "Ethernet PHY", "Oscilloscopes/Logic Analyzers"), "1-3 years", "FULL_TIME", "2026-08-24", "MOCK_SOURCE", null, "MOCK", null), new Job("mock-hw-004", "Embedded Systems & Firmware Engineer", "AeroChip Devices", "Hyderabad, India", "Develop low-level bare-metal and RTOS-based firmware for ARM Cortex-M microcontrollers. Interface with sensors via I2C, SPI, UART, and CAN protocols.", List.of("Embedded C", "Microcontrollers (ARM Cortex)", "RTOS (FreeRTOS)", "I2C/SPI/UART", "Firmware"), List.of("C++", "BLE/WiFi Stacks", "Hardware Debugging", "JTAG"), "0-2 years", "FULL_TIME", "2026-08-27", "MOCK_SOURCE", null, "MOCK", null), new Job("mock-hw-005", "Graduate Hardware Engineer (VLSI / Digital Design)", "Vanguard Semiconductors", "Hyderabad, India", "Entry-level role for Electronics and Communication Engineering (ECE) graduates passionate about semiconductor design, digital logic, and hardware description languages.", List.of("Digital Electronics", "Verilog", "Basic VLSI", "C Programming"), List.of("Linux", "Shell Scripting", "SystemVerilog Basics"), "Fresher", "FULL_TIME", "2026-08-26", "MOCK_SOURCE", null, "MOCK", null), new Job("mock-hw-006", "Senior ASIC Physical Design Engineer", "Optima Silicon Labs", "Hyderabad, India", "Lead block-level and chip-level physical design implementation from Netlist to GDSII, including floorplanning, CTS, routing, and signoff timing closure.", List.of("Physical Design", "Floorplanning", "CTS", "Routing", "Timing Closure (STA)"), List.of("Cadence Innovus", "Synopsys ICC2", "Tcl/Python", "DRC/LVS"), "5+ years", "FULL_TIME", "2026-08-21", "MOCK_SOURCE", null, "MOCK", null), new Job("mock-hw-007", "Embedded Hardware & IoT Intern", "SmartPulse Electronics", "Hyderabad, India", "Hands-on internship designing PCB schematics, breadboard prototyping, and writing microcontroller C code for connected IoT edge devices.", List.of("Embedded C", "PCB Design Basics (KiCad/Eagle)", "Sensors & Microcontrollers", "Arduino/ESP32"), List.of("Soldering", "Python", "MQTT"), "Fresher / Intern", "INTERNSHIP", "2026-08-27", "MOCK_SOURCE", null, "MOCK", null));

    @Override
    public String getSourceName() {
        return SOURCE_NAME;
    }

    @Override
    public boolean isLive() {
        return false;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public List<Job> search(JobSearchRequest request) {
        log.debug("MockJobSource providing {} raw development catalog listings", (Object)this.mockCatalog.size());
        return this.mockCatalog;
    }
}

