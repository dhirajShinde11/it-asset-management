# Aegis ITAM — Enterprise IT Asset Management & Workforce Logistics Platform

[![Java](https://img.shields.io/badge/Java-21%20LTS-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**Aegis ITAM** is a fast, full-featured **IT Asset Management (ITAM)** and workforce logistics platform engineered for enterprise infrastructure teams. Built with **Java 21**, **Spring Boot 3.3.4**, **Spring Data JPA**, **H2 In-Memory Database** (production-ready for PostgreSQL/MySQL), and a responsive **Glassmorphic Dashboard UI**.

---

## 🌟 Platform Capabilities

### 1. 5-Tier Inventory & Equipment Architecture
* **💻 Hardware Assets**: Complete lifecycle management from procurement to decommissioning. Track asset tags, serial numbers, models, purchase cost, condition grading (`NEW`, `EXCELLENT`, `GOOD`, `FAIR`, `DAMAGED`, `DECOMMISSIONED`), and operational status (`AVAILABLE`, `ASSIGNED`, `UNDER_MAINTENANCE`, `RESERVED`, `RETIRED`).
* **🎧 Accessories & Peripherals**: Batch tracking for chargers, adapters, docking stations, monitors, and mice. Track checkout allocations to employees and return items back to available inventory.
* **🧴 Consumables & Supplies**: Dedicated tracking for one-way supplies (thermal paste, cables, cleaning kits, toner). Real-time stock decrements with automated low-stock threshold alerts.
* **⚡ Modular Internal Components**: Manage hardware components (RAM kits, NVMe SSDs, GPUs, CPUs) and assign/install them into host computers or unassign them back to stock.
* **📜 Software Licenses & SaaS**: Monitor software seats, active utilization ratios, expiration dates, license keys, and recurring subscription costs.

### 2. 🏷️ Printable QR Code & Barcode Equipment Labels
* Client-side **2D QR Code** and **Code-128 SVG Barcode** generation for all equipment tags.
* **1-Click Print Modal** formatted for standard **3.5" × 2"** physical thermal label printers and sticker sheets.

### 3. 🔍 Physical Audit & Health Verification
* Perform scheduled and ad-hoc physical inventory audits.
* Record audit locations, condition notes, auditor names, and automated next-audit schedules.

### 4. 📜 Live Custody & Activity Audit Trail
* Real-time logging of all lifecycle operations (`CREATED`, `CHECKOUT`, `CHECKIN`, `AUDIT`, `UPDATED`, `DELETED`).
* Full accountability recording asset tags, target employees, performing administrators, and timestamps.

### 5. 👥 Dual-Portal Architecture & 360° Employee Equipment Profile
* **IT Administrator Command Center**: Complete fleet control, inventory analytics, user onboarding, and system configurations.
* **360° User Equipment Profile**: Drawer view displaying assigned hardware, accessories, consumables, and activity history for any team member.
* **Employee Self-Service Hub**: Clean, dedicated view where employees see their assigned hardware, operational readiness, and service tickets.
* **Automated SMTP Onboarding**: Automatically delivers personalized welcome emails with direct login links upon user creation.

---

## 🛠️ Technology Stack

* **Backend**: Java 21 LTS, Spring Boot 3.3.4, Spring Data JPA, Hibernate, Spring Mail (SMTP), Springdoc OpenAPI / Swagger UI.
* **Database**: H2 (In-memory / File persistence) — compatible with PostgreSQL and MySQL.
* **Frontend**: HTML5, Modern CSS Glassmorphic Dark Design System, Vanilla JavaScript (zero Node/NPM build dependencies).
* **Label Generation**: Client-side QR Code (`qrcode.min.js`) + vector SVG Barcode renderer.
* **Build Tool**: Apache Maven (`mvnw` wrapper included).

---

## 🚀 Getting Started

### Prerequisites
* **Java 21 LTS** or later installed.
* **Git** installed.

### 1. Clone the Repository
```bash
git clone https://github.com/dhirajShinde11/it-asset-management.git
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
Using the Maven wrapper:
```powershell
# Windows
.\mvnw.cmd spring-boot:run

# Linux / macOS
./mvnw spring-boot:run
```

Or using Maven:
```powershell
mvn spring-boot:run
```

The application will launch on **`http://localhost:8085`**.

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
powershell -ExecutionPolicy Bypass -File .\test_full_system.ps1
```

Or run standard JUnit tests:
```bash
mvn test
```

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
