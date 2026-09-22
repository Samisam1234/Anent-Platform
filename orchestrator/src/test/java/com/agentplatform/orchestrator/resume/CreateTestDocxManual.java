package com.agentplatform.orchestrator.resume;

import org.apache.poi.xwpf.usermodel.*;
import org.junit.jupiter.api.Test;

import java.io.FileOutputStream;
import java.io.IOException;

class CreateTestDocxManual {

    @Test
    void createTestDocx() throws IOException {
        String docxPath = "E:/AI Agent/agent-platform/Samiuddin_IT_B.Tech.docx";
        
        try (XWPFDocument doc = new XWPFDocument();
             FileOutputStream out = new FileOutputStream(docxPath)) {
            
            // Title / Name
            XWPFParagraph titlePara = doc.createParagraph();
            titlePara.setAlignment(ParagraphAlignment.LEFT);
            XWPFRun titleRun = titlePara.createRun();
            titleRun.setText("MOHAMMAD ABDUL SAMIUDDIN");
            titleRun.setBold(true);
            titleRun.setFontSize(16);
            
            // Contact info
            XWPFParagraph contactPara = doc.createParagraph();
            XWPFRun contactRun = contactPara.createRun();
            contactRun.setText("Hyderabad, India  |  sami7.dolls@gmail.com  |  9676687067  |  github.com/Samisam1234  |  linkedin.com/in/samiuddin");
            contactRun.setFontSize(11);
            
            // Professional Summary heading
            addHeading(doc, "PROFESSIONAL SUMMARY");
            
            // Professional Summary content
            XWPFParagraph summaryPara = doc.createParagraph();
            XWPFRun summaryRun = summaryPara.createRun();
            summaryRun.setText("Electronics & Communication Engineering graduate with Java Full Stack training from QSpiders. Proficient in Core Java, Spring Boot, REST APIs, SQL, and Web Technologies. Built 3 real-world projects spanning backend API systems, hardware-software neural network simulation, and responsive front-end UI design. Strong OOP foundation with hands-on Agile and clean architecture experience — ready to contribute to scalable, production-ready IT solutions.");
            summaryRun.setFontSize(11);
            
            // Technical Skills heading
            addHeading(doc, "TECHNICAL SKILLS");
            
            // Skills content
            String[] skillsLines = {
                "Languages: Core Java, Advanced Java, C (Basics)",
                "Frameworks: Spring Boot, Hibernate, JDBC, Servlets/JSP",
                "Frontend: HTML5, CSS3, JavaScript, CSS Flexbox, Responsive Design",
                "Databases: MySQL, PostgreSQL, SQL Queries, Joins, Constraints, ER Modelling",
                "Tools: Git, GitHub, Maven, Postman, Eclipse, IntelliJ IDEA",
                "Embedded: Arduino, Sensors Interfacing, Microcontrollers",
                "Concepts: OOPs, REST API Design, SDLC, Agile, Clean Architecture, Problem Solving"
            };
            
            for (String line : skillsLines) {
                XWPFParagraph p = doc.createParagraph();
                XWPFRun r = p.createRun();
                r.setText(line);
                r.setFontSize(11);
            }
            
            // Projects heading
            addHeading(doc, "PROJECTS");
            
            // Project 1
            addProject(doc, "VLSI Neural Network Simulator  |  Java | OOP | Hardware Simulation\t2025", 
                "Architected a hardware-accurate neural network simulator in Java with 5 modular units: MemoryUnit, AdderUnit, MultiplierUnit, ActivationUnit, and ControlUnit — each simulating real VLSI chip behaviour independently.\n" +
                "Built a 3-layer network (2 inputs, 3 hidden neurons, 1 output) with sigmoid activation; correctly solved all 4 XOR input combinations — tracked metrics: 9 adder ops, 6 multiplier ops, and nanosecond propagation delay per layer.\n" +
                "Applied OOP principles — encapsulation, modularisation, single-responsibility — across 8 Java classes in 2 packages (hardware, neuralnetwork), bridging ECE and software engineering domains.");
            
            // Project 2
            addProject(doc, "Hospital Management System  |  Java | Spring Boot | PostgreSQL | REST API\t2024",
                "Developed 10+ RESTful API endpoints using Spring Boot (GET, POST, PUT, DELETE) with JSON responses to manage 3 core modules: hospital, doctor, and patient records.\n" +
                "Implemented full CRUD with Spring Data JPA and PostgreSQL across 4+ relational tables; designed 3-layer architecture (Controller, DAO, Repository) ensuring clean separation of concerns.\n" +
                "Validated 5+ API flows in Postman; documented ER diagrams and complete database schema ensuring scalable data design and easy future team handover.");
            
            // Project 3
            addProject(doc, "PizzaHut — Responsive Navbar  |  HTML5 | CSS3 | JavaScript\t2024",
                "Built a fully responsive navbar across 3 screen breakpoints (mobile <768px, tablet 768–1024px, desktop >1024px) using HTML5, CSS Flexbox, and 4 CSS media query breakpoints.\n" +
                "Implemented hamburger menu toggle with smooth slide-in/out animation using pure vanilla JavaScript DOM manipulation — zero external libraries. Maintained separate, modular HTML, CSS, and JS files.");
            
            // Certifications heading
            addHeading(doc, "CERTIFICATIONS");
            
            String[] certs = {
                "Java Full Stack Development\tQSpiders",
                "Core Java, Advanced Java, SQL, Spring Boot, Hibernate, and real-time project implementation.",
                "Python for Data Science\tKaggle Learn",
                "Python programming fundamentals — data structures, logic, and syntax for data science applications.",
                "Ethical Hacking Fundamentals\tSkillUp by Simplilearn",
                "Vulnerability assessment methodologies and network security protocols for ethical hacking."
            };
            
            for (String cert : certs) {
                XWPFParagraph p = doc.createParagraph();
                XWPFRun r = p.createRun();
                r.setText(cert);
                r.setFontSize(11);
            }
            
            // Education heading
            addHeading(doc, "EDUCATION");
            
            XWPFParagraph eduPara = doc.createParagraph();
            XWPFRun eduRun = eduPara.createRun();
            eduRun.setText("Bachelor of Engineering — Electronics & Communication Engineering  |  Vignan Institute of Technology and Science, Hyderabad\t2020 – 2024");
            eduRun.setFontSize(11);
            
            // Key Strengths heading
            addHeading(doc, "KEY STRENGTHS");
            
            String[] strengths = {
                "Object-Oriented Programming  •  REST API Development  •  Database Design & SQL  •  Clean Code Practices  •  Hardware-Software Co-Design  •  Agile & SDLC  •  Fast Learner  •  Team Collaboration  •  Problem Solving  •  Responsive UI Development"
            };
            
            for (String s : strengths) {
                XWPFParagraph p = doc.createParagraph();
                XWPFRun r = p.createRun();
                r.setText(s);
                r.setFontSize(11);
            }
            
            doc.write(out);
            System.out.println("Test DOCX created at: " + docxPath);
        }
    }
    
    private static void addHeading(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun r = p.createRun();
        r.setText(text);
        r.setBold(true);
        r.setFontSize(14);
    }
    
    private static void addProject(XWPFDocument doc, String title, String description) {
        XWPFParagraph titlePara = doc.createParagraph();
        XWPFRun titleRun = titlePara.createRun();
        titleRun.setText(title);
        titleRun.setBold(true);
        titleRun.setFontSize(12);
        
        XWPFParagraph descPara = doc.createParagraph();
        XWPFRun descRun = descPara.createRun();
        descRun.setText(description);
        descRun.setFontSize(11);
    }
}