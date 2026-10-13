# Aegis ITAM — Snipe-IT Alternative for Enterprise IT Asset Management

[![Java](https://img.shields.io/badge/Java-21%20LTS-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

A modern, fast, full-featured **IT Asset Management (ITAM)** system with complete **Snipe-IT workflow parity**. Built with **Java 21**, **Spring Boot 3.3.4**, **Spring Data JPA**, **H2 In-Memory Database** (production-ready for PostgreSQL/MySQL), and a responsive **Glassmorphic Dashboard UI**.

---

## 🌟 Key Features

### 1. Snipe-IT 5-Tier Inventory Categorization
* **💻 Hardware Assets**: Complete lifecycle from procurement to disposal. Track asset tags, serial numbers, purchase cost, condition grading (`NEW`, `EXCELLENT`, `GOOD`, `FAIR`, `DAMAGED`, `DECOMMISSIONED`), and assignment status (`AVAILABLE`, `ASSIGNED`, `UNDER_MAINTENANCE`, `RESERVED`, `RETIRED`).
* **🎧 Accessories**: Track batches of accessories (chargers, mice, docks, headsets). Check out units to employees and check in back to available stock.
* **🧴 Consumables**: Track consumable supplies (thermal paste, cables, adapters, cleaning kits). One-way dispensing directly to team members with low-stock alerts.
* **⚡ Internal Components**: Track modular parts (RAM sticks, NVMe SSDs, GPUs, CPUs) and assign/install them into host computers or unassign back to stock.
* **📜 Software Licenses**: Monitor software seat utilization, expiration dates, vendor agreements, and license allocations.

### 2. 🏷️ QR Code & Barcode Asset Label Generator
* Instant client-side **2D QR Code** and **Code-128 SVG Barcode** sticker generation for every asset.
* **1-Click Print Modal** formatted for standard **3.5in × 2in** physical label printers and sheet stickers.

### 3. 🔍 Physical Auditing
* Perform on-site or scheduled asset audits.
* Record audit location, audit notes, and next scheduled audit date.

### 4. 📜 Real-Time Custody & Activity Audit Trail
* Automatic logging of all lifecycle events (`CREATED`, `CHECKOUT`, `CHECKIN`, `AUDIT`, `UPDATED`, `DELETED`).
* Full accountability recording item details, recipient user, performing administrator, and exact timestamps.

### 5. 👥 Role-Based Portals & User 360 Profile
* **Dedicated Full-Screen Login**: Seamless authentication for Administrators and Employees.
* **360° User Equipment Profile**: Drawer view aggregating all assigned hardware, accessories, consumables, and activity history for any employee.
* **Employee Self-Service View**: Employees see only their assigned assets and checkouts.
* **Automated SMTP Email Onboarding**: Generates credentials and sends personalized welcome emails with one-click direct login links.

---

## 🛠️ Technology Stack

* **Backend**: Java 21 LTS, Spring Boot 3.3.4, Spring Data JPA, Hibernate, Spring Mail (SMTP), Springdoc OpenAPI / Swagger UI.
* **Database**: H2 (In-memory / File persistence) — effortlessly migrates to PostgreSQL or MySQL.
* **Frontend**: HTML5, Vanilla CSS (Glassmorphism Dark Design System), Vanilla JavaScript (No NPM or Node build steps required).
* **Label Generation**: Client-side QR Code (`qrcode.min.js`) + SVG Barcode renderer.
* **Build Tool**: Apache Maven (`mvnw` wrapper included).

---

## 🚀 Getting Started

### Prerequisites
* **Java 21 LTS** or later installed.
* **Git** installed.

### 1. Clone the Repository
```bash
git clone https://github.com/<your-username>/it-asset-management.git
cd it-asset-management
```

### 2. Configure Email / SMTP (Optional)
To enable real SMTP onboarding emails:
1. Copy `secrets.properties.example` to `secrets.properties`:
   ```bash
   cp secrets.properties.example secrets.properties
   ```
2. Enter your email provider credentials (e.g. Gmail App Password).

### 3. Run the Application
Using the included Maven wrapper:
```bash
# Windows
.\mvnw.cmd spring-boot:run

# Linux / macOS
./mvnw spring-boot:run
```

Or with Maven:
```bash
mvn spring-boot:run
```

The application will start on **`http://localhost:8085`**.

---

## 🌐 Endpoints & URLs

| Service | URL | Default Credentials / Notes |
| :--- | :--- | :--- |
| **Aegis ITAM Web Application** | `http://localhost:8085/` | Admin: `dhiraj@bmconsulting.co` / `12345` |
| **Interactive Swagger API Docs** | `http://localhost:8085/swagger-ui.html` | Interactive REST explorer |
| **OpenAPI Specification** | `http://localhost:8085/v3/api-docs` | OpenAPI 3 JSON Schema |
| **H2 Web Console** | `http://localhost:8085/h2-console` | JDBC URL: `jdbc:h2:mem:itamdb` |

---

## 🧪 Testing

Run the automated verification suite:
```powershell
powershell -ExecutionPolicy Bypass -File .\test_snipeit_system.ps1
```

Or run standard JUnit tests:
```bash
mvn test
```

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
