$headers = @{"Content-Type" = "application/json"}
$prompt = @"
You are a professional resume parser.
Extract structured information from the resume text provided below.

Return ONLY a valid JSON object — no explanations, no markdown, no code fences.
Use empty strings "" for missing scalar fields.
Use empty arrays [] for missing list fields.

Required JSON structure:
{
  "name": "Full Name",
  "email": "email@example.com",
  "phone": "+1-555-000-0000",
  "location": "City, Country",
  "education": ["Degree at Institution (Year)", "..."],
  "skills": ["skill1", "skill2"],
  "experience": ["Job Title at Company (duration): brief description", "..."],
  "internships": ["Intern Title at Company (duration): brief description", "..."],
  "projects": ["Project Name: brief description", "..."],
  "certifications": ["Certification Name (Issuer, Year)", "..."],
  "softwareSkills": ["Java", "Spring Boot", "Docker", "..."],
  "hardwareSkills": ["Arduino", "Raspberry Pi", "..."],
  "preferredRoles": ["Backend Engineer", "..."],
  "preferredLocations": ["Remote", "New York", "..."]
}

Resume text:
---
MOHAMMAD ABDUL SAMIUDDIN
Hyderabad, India  |  sami7.dolls@gmail.com  |  9676687067  |  github.com/Samisam1234  |  linkedin.com/in/samiuddin
PROFESSIONAL SUMMARY
Electronics & Communication Engineering graduate with Java Full Stack training from QSpiders. Proficient in Core Java, Spring Boot, REST APIs, SQL, and Web Technologies. Built 3 real-world projects spanning backend API systems, hardware-software neural network simulation, and responsive front-end UI design. Strong OOP foundation with hands-on Agile and clean architecture experience — ready to contribute to scalable, production-ready IT solutions.
TECHNICAL SKILLS
Languages: Core Java, Advanced Java, C (Basics)
Frameworks: Spring Boot, Hibernate, JDBC, Servlets/JSP
Frontend: HTML5, CSS3, JavaScript, CSS Flexbox, Responsive Design
Databases: MySQL, PostgreSQL, SQL Queries, Joins, Constraints, ER Modelling
Tools: Git, GitHub, Maven, Postman, Eclipse, IntelliJ IDEA
Embedded: Arduino, Sensors Interfacing, Microcontrollers
Concepts: OOPs, REST API Design, SDLC, Agile, Clean Architecture, Problem Solving
PROJECTS
VLSI Neural Network Simulator  |  Java | OOP | Hardware Simulation	2025
Architected a hardware-accurate neural network simulator in Java with 5 modular units: MemoryUnit, AdderUnit, MultiplierUnit, ActivationUnit, and ControlUnit — each simulating real VLSI chip behaviour independently.
Built a 3-layer network (2 inputs, 3 hidden neurons, 1 output) with sigmoid activation; correctly solved all 4 XOR input combinations — tracked metrics: 9 adder ops, 6 multiplier ops, and nanosecond propagation delay per layer.
Applied OOP principles — encapsulation, modularisation, single-responsibility — across 8 Java classes in 2 packages (hardware, neuralnetwork), bridging ECE and software engineering domains.
Hospital Management System  |  Java | Spring Boot | PostgreSQL | REST API	2024
Developed 10+ RESTful API endpoints using Spring Boot (GET, POST, PUT, DELETE) with JSON responses to manage 3 core modules: hospital, doctor, and patient records.
Implemented full CRUD with Spring Data JPA and PostgreSQL across 4+ relational tables; designed 3-layer architecture (Controller, DAO, Repository) ensuring clean separation of concerns.
Validated 5+ API flows in Postman; documented ER diagrams and complete database schema ensuring scalable data design and easy future team handover.
PizzaHut — Responsive Navbar  |  HTML5 | CSS3 | JavaScript	2024
Built a fully responsive navbar across 3 screen breakpoints (mobile <768px, tablet 768–1024px, desktop >1024px) using HTML5, CSS Flexbox, and 4 CSS media query breakpoints.
Implemented hamburger menu toggle with smooth slide-in/out animation using pure vanilla JavaScript DOM manipulation — zero external libraries. Maintained separate, modular HTML, CSS, and JS files.
CERTIFICATIONS
Java Full Stack Development	QSpiders
Core Java, Advanced Java, SQL, Spring Boot, Hibernate, and real-time project implementation.
Python for Data Science	Kaggle Learn
Python programming fundamentals — data structures, logic, and syntax for data science applications.
Ethical Hacking Fundamentals	SkillUp by Simplilearn
Vulnerability assessment methodologies and network security protocols for ethical hacking.
EDUCATION
Bachelor of Engineering — Electronics & Communication Engineering  |  Vignan Institute of Technology and Science, Hyderabad	2020 – 2024
KEY STRENGTHS
Object-Oriented Programming  •  REST API Development  •  Database Design & SQL  •  Clean Code Practices  •  Hardware-Software Co-Design  •  Agile & SDLC  •  Fast Learner  •  Team Collaboration  •  Problem Solving  •  Responsive UI Development
---
Return ONLY the JSON object. Nothing else.
"@

$promptJson = $prompt | ConvertTo-Json -Compress
$body = '{"model":"llama3.2:3b","messages":[{"role":"user","content":' + $promptJson + '}],"stream":false}'
$r = Invoke-WebRequest -Uri "http://localhost:11434/api/chat" -Method POST -Headers @{"Content-Type"="application/json"} -Body $body -UseBasicParsing -TimeoutSec 300
$r.Content