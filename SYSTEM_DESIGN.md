# Aegis IT Asset Management (ITAM) - Complete System Design Document

**Document Version:** 1.0.0  
**Target System:** Enterprise IT Asset Lifecycle, Support Ticketing & Automated Notification Platform  
**Architecture Style:** Modular Monolith (Spring Boot 3 + JPA) with Decoupled Vanilla SPA Frontend & Hybrid Cloud Tunneling  
**Author / Engineering Team:** Antigravity Pair-Programming Assistant & IT Engineering  

---

## 1. Executive Summary & System Objectives

Aegis IT Asset Management (ITAM) is a centralized, enterprise-grade hardware and software governance platform designed for modern organizations. It unifies physical asset tracking, employee hardware provisioning, IT support helpdesk ticketing, and automated email communications into a single interface.

### Core Problems Solved
1. **Hardware Loss & Ghost Assets:** Eliminates untracked hardware purchases, lost laptops, and missing equipment through serial-number and asset-tag tracking.
2. **Accountability Gaps:** Binds physical assets directly to employee profiles, departments, and purchase histories with full audit logging.
3. **Fragmented IT Helpdesk:** Replaces disjointed email threads and chat messages with a structured ticketing workflow tied directly to problematic assets and assigned technicians.
4. **Communication Delays:** Automates credential dispatch for new hires and real-time status updates for service desk tickets via an internal Outbox with real SMTP failover.
5. **Operational Visibility:** Provides executive dashboards with live financial depreciation estimates, equipment condition analytics, and real-time SOC security posture telemetry.

---

## 2. High-Level System Architecture

Aegis ITAM follows a **Modular Monolith** architecture pattern. The application is packaged as a single deployable unit backed by a relational database, with clearly separated presentation, business service, and data access boundaries.

```mermaid
graph TD
    subgraph Client_Layer ["Client Layer (Web / Mobile)"]
        BrowserAdmin["Admin Command Hub (Desktop)"]
        BrowserEmployee["Employee Self-Service Portal (Desktop / Mobile)"]
    end

    subgraph Edge_Network ["Edge Network & Ingress"]
        CloudflareTunnel["Cloudflare Edge SSL / Tunnel (*.trycloudflare.com)"]
        LocalProxy["Reverse Proxy / Port Forwarder (:3000)"]
    end

    subgraph Application_Core ["Aegis ITAM Application Core (:8085)"]
        StaticFrontend["Vanilla SPA Web Engine (HTML5 / CSS3 / ES6+)"]
        RESTControllers["Spring Boot REST API Controllers (16 Controllers)"]
        BusinessServices["Service Layer & Business Rules (11 Services)"]
        MailSubsystem["Email Engine (Outbox Pattern + Master ON/OFF Switch)"]
        AuditSubsystem["Activity & Audit Log Interceptor"]
        JPALayer["Spring Data JPA / Hibernate ORM"]
    end

    subgraph Auxiliary_Services ["Auxiliary & Simulation Engines"]
        NessusSOC["Python SOC Telemetry Simulator (:8090)"]
    end

    subgraph Data_Storage ["Persistent Storage Layer (:3306)"]
        MySQL[("MySQL 8 / MariaDB (itam_db)")]
    end

    subgraph External_Integrations ["External Mail Gateways"]
        SMTPServer["Corporate SMTP (Gmail, Outlook 365, Custom Relay)"]
    end

    BrowserAdmin -->|HTTPS / WSS| CloudflareTunnel
    BrowserEmployee -->|HTTPS / WSS| CloudflareTunnel
    CloudflareTunnel -->|Encrypted Wire| RESTControllers
    CloudflareTunnel -->|Static Assets| StaticFrontend
    BrowserAdmin -.->|Direct Local LAN| RESTControllers

    RESTControllers --> BusinessServices
    BusinessServices --> MailSubsystem
    BusinessServices --> AuditSubsystem
    BusinessServices --> JPALayer
    JPALayer -->|HikariCP / TCP| MySQL

    MailSubsystem -->|Encrypted TLS / Port 587| SMTPServer
    RESTControllers -.->|Proxy Stream| LocalProxy
    LocalProxy -.-> NessusSOC
```

---

## 3. Technology Stack & Component Specifications

| Tier | Technology | Rationale & Technical Characteristics |
| :--- | :--- | :--- |
| **Backend Framework** | Java 21, Spring Boot 3.3.4 | Modern LTS Java runtime, native virtual threads readiness, robust dependency injection, embedded Tomcat 10. |
| **Data Persistence** | Spring Data JPA, Hibernate 6.5 | Object-Relational Mapping (ORM), automated schema management (`ddl-auto=update`), derived repository queries. |
| **Connection Pool** | HikariCP | High-performance, zero-overhead JDBC connection pooling. |
| **Database** | MySQL 8.0+ / MariaDB | Relational ACID compliance, foreign key integrity, index optimization, JSON payload support. |
| **Frontend UI** | Vanilla HTML5, Modern CSS3, JavaScript ES6+ | Zero build step, sub-50ms initial load time, native browser performance, glassmorphism dark-theme aesthetics. |
| **Email Protocol** | Jakarta Mail, JavaMailSender | Standards-compliant MIME/HTML formatting, STARTTLS encryption, socket timeouts (10s fail-safe). |
| **Edge Tunneling** | Cloudflare Tunnel (`cloudflared`) | End-to-end encrypted Zero Trust tunneling; eliminates port-forwarding and dynamic DNS challenges. |
| **API Documentation** | Springdoc OpenAPI 3 / Swagger UI | Automated interactive API catalog exposed at `/swagger-ui.html` and `/v3/api-docs`. |

---

## 4. Database Schema & Entity Relationship Model

The database schema is organized around core IT resources, employee identities, operational tickets, communication outbox items, and audit records.

```mermaid
erDiagram
    APP_USERS ||--o{ ASSETS : "assigned to"
    APP_USERS ||--o{ TICKETS : "requests / assigned"
    APP_USERS ||--o{ ACTIVITY_LOGS : "performed by"
    ASSETS ||--o{ TICKETS : "referenced in"
    ASSETS ||--o{ MAINTENANCE_RECORDS : "maintains"
    ASSETS ||--o{ COMPONENT_ASSIGNMENTS : "houses"
    APP_USERS ||--o{ ACCESSORY_CHECKOUTS : "checks out"
    APP_USERS ||--o{ CONSUMABLE_CHECKOUTS : "consumes"

    APP_USERS {
        bigint id PK
        varchar full_name
        varchar email UK
        varchar password
        varchar role
        varchar department
        varchar phone
        varchar status
        datetime created_at
    }

    ASSETS {
        bigint id PK
        varchar asset_tag UK
        varchar name
        varchar serial_number UK
        varchar category
        varchar status
        varchar asset_condition
        varchar model
        varchar manufacturer
        decimal purchase_cost
        date purchase_date
        date warranty_expiry
        bigint assigned_user_id FK
        varchar location
        text notes
    }

    TICKETS {
        bigint id PK
        varchar ticket_number UK
        varchar title
        text description
        varchar category
        varchar priority
        varchar status
        varchar requester_name
        varchar requester_email
        varchar requester_department
        varchar assigned_to
        bigint asset_id FK
        text resolution_notes
        datetime created_at
        datetime updated_at
        datetime resolved_at
    }

    EMAIL_NOTIFICATIONS {
        bigint id PK
        varchar recipient_email
        varchar recipient_name
        varchar subject
        varchar email_type
        longtext content
        varchar temporary_password
        varchar portal_url
        varchar status
        datetime sent_at
    }

    SMTP_SETTINGS {
        bigint id PK
        varchar host
        int port
        varchar username
        varchar password
        varchar from_email
        varchar from_name
        boolean auth
        boolean start_tls
        boolean use_ssl
        boolean enabled
        datetime updated_at
    }

    ACTIVITY_LOGS {
        bigint id PK
        varchar action
        varchar entity_type
        varchar entity_name
        text details
        varchar performed_by
        datetime timestamp
    }
```

---

## 5. Subsystem Design & Workflow Sequences

### 5.1 Outgoing Mail Subsystem with Master ON/OFF Switch

To prevent accidental mail spamming, connection timeouts, or credential leaks during testing, the email subsystem implements the **Outbox Pattern** coupled with a **Master Control Guard**.

```mermaid
sequenceDiagram
    autonumber
    actor Admin as Admin / System Event
    participant EmailSvc as EmailService
    participant Repo as EmailNotificationRepository
    participant SmtpRepo as SmtpSettingRepository
    participant JavaMail as JavaMailSender
    participant SMTP as Real SMTP Relay (e.g. Gmail)

    Admin->>EmailSvc: sendWelcomeEmail(user, password) / sendTicketAlert(...)
    EmailSvc->>Repo: save(notification with status "DELIVERED_TO_OUTBOX")
    
    EmailSvc->>EmailSvc: isDemoOrPlaceholderEmail(recipient)?
    alt Is Demo / Placeholder (@example.com, @test.com)
        EmailSvc->>Repo: update status to "DEMO_SKIPPED"
        EmailSvc-->>Admin: Return notification (SMTP skipped safely)
    else Valid Recipient Address
        EmailSvc->>SmtpRepo: getSmtpSetting().isEnabled()
        alt Master Sending Switch is OFF (PAUSED)
            EmailSvc->>Repo: update status to "OUTBOX_PAUSED"
            EmailSvc-->>Admin: Email safely retained in Outbox without SMTP network calls
        else Master Sending Switch is ON (ACTIVE)
            EmailSvc->>EmailSvc: Check credentials (host, port, user, pass)
            alt Credentials Missing / Incomplete
                EmailSvc->>Repo: update status to "CONFIG_REQUIRED"
            else Credentials Valid
                EmailSvc->>JavaMail: send(MimeMessage) via TLS Port 587
                alt Success
                    JavaMail->>SMTP: SMTP Transmission
                    EmailSvc->>Repo: update status to "DELIVERED_TO_INBOX"
                else SMTP Exception / Timeout
                    EmailSvc->>Repo: update status to "SMTP Error: [message]"
                end
            end
            EmailSvc-->>Admin: Return delivery status
        end
    end
```

---

### 5.2 Asset Allocation & Lifecycle Transition Workflow

```mermaid
sequenceDiagram
    autonumber
    actor Admin as IT Administrator
    participant AssetCtrl as AssetController
    participant AssetSvc as AssetService
    participant UserSvc as UserService
    participant EmailSvc as EmailService
    participant AuditSvc as ActivityLogService

    Admin->>AssetCtrl: POST /api/assets/{id}/assign?userId={userId}
    AssetCtrl->>AssetSvc: assignAsset(assetId, userId)
    AssetSvc->>UserSvc: getUserById(userId)
    AssetSvc->>AssetSvc: Set asset.status = ASSIGNED, asset.assignedUser = user
    AssetSvc->>EmailSvc: sendAssetAssignedEmail(user, asset)
    EmailSvc->>EmailSvc: dispatchViaSmtp(notification)
    AssetSvc->>AuditSvc: logActivity("ASSIGNED", "ASSET", asset.tag, user.name)
    AssetSvc-->>AssetCtrl: Return updated Asset DTO
    AssetCtrl-->>Admin: HTTP 200 OK + UI updates badge to "ASSIGNED"
```

---

### 5.3 IT Support Ticket Lifecycle & Technician Alert Dispatch

```mermaid
sequenceDiagram
    autonumber
    actor Employee as Organization User
    participant TicketCtrl as TicketController
    participant TicketSvc as TicketService
    participant EmailSvc as EmailService
    actor Tech as Assigned Technician

    Employee->>TicketCtrl: POST /api/tickets (Title, Description, Priority, AssetId)
    TicketCtrl->>TicketSvc: createTicket(dto)
    TicketSvc->>TicketSvc: Generate ticket_number "TICK-XXXX", status "OPEN"
    TicketSvc->>EmailSvc: sendTicketCreatedNotification(ticket) -> Requester Inbox
    TicketSvc->>EmailSvc: sendTicketTechnicianAlert(ticket, techEmail, techName)
    EmailSvc-->>Tech: Push horizontal notification card with direct ticket link
    TicketSvc-->>TicketCtrl: Return created Ticket
    TicketCtrl-->>Employee: HTTP 201 Created (Ticket live on dashboard)
```

---

## 6. Security, Authentication & Role-Based Access Control (RBAC)

Aegis ITAM implements a defense-in-depth security model:

```mermaid
flowchart TD
    Request[Incoming HTTP Request] --> CORS[CORS Filter - Allow Local & Tunneled Origins]
    CORS --> AuthRouter{Endpoint Type}
    
    AuthRouter -->|Public: /index.html, /css, /js| StaticHandler[Serve Static Resource]
    AuthRouter -->|Auth: /api/auth/login| LoginHandler[Validate Credentials against itam_db]
    AuthRouter -->|Employee Portal: /api/my/*| EmpHandler[Verify Requester Email Ownership]
    AuthRouter -->|Admin Management: /api/assets, /api/users, etc.| AdminHandler[Verify Admin Role Authorization]

    LoginHandler --> SuccessCheck{Password Match?}
    SuccessCheck -->|Yes| SessionToken[Return User Profile + Masked Credentials]
    SuccessCheck -->|No| AuditFail[Log Failed Auth Event to Console & Return HTTP 401]

    AdminHandler --> AuditLog[Record Action in activity_logs Table]
```

### Key Security Safeguards
1. **Password Masking:** The SMTP controller and user details API responses permanently mask raw passwords (`••••••••`) across network responses.
2. **Demo Anti-Spam Guard:** The email dispatcher strictly inspects recipient addresses against demo domains (`@example.com`, `@test.com`, `@company.com`). Real SMTP traffic is never dispatched to dummy addresses.
3. **Immutable Audit Trail:** All database modifications (CREATE, UPDATE, DELETE, CHECKOUT, ASSIGN) are recorded in the `activity_logs` table with the performing administrator's identity.

---

## 7. Cloud Deployment & Production Blueprint

While the application currently runs locally and can be accessed via Cloudflare Tunnel, the production blueprint ensures 24/7 availability independently of local hardware.

```mermaid
graph LR
    subgraph GitHub_Repo ["Source Code Management"]
        GitPush["git push origin main"]
    end

    subgraph CI_CD ["Continuous Deployment Engine"]
        BuildTrigger["Render / Railway Webhook"]
        MavenBuild["Maven Multi-Stage Build & Package"]
        ContainerGen["Lightweight Container (Java 21 JRE)"]
    end

    subgraph Cloud_Hosting ["Managed Cloud Platform (Render / Railway)"]
        AppInstance["Aegis ITAM Service Instance (Port 8085)"]
        ManagedDB[("Managed Cloud MySQL / MariaDB Database")]
    end

    subgraph Users ["Global Access"]
        PublicUsers["End Users (Custom Domain / SSL)"]
    end

    GitPush --> BuildTrigger
    BuildTrigger --> MavenBuild
    MavenBuild --> ContainerGen
    ContainerGen --> AppInstance
    AppInstance <-->|Encrypted Wire / JDBC| ManagedDB
    PublicUsers <-->|HTTPS 443| AppInstance
```

### Production Environment Variables Configuration

| Variable Name | Description | Example / Recommended Value |
| :--- | :--- | :--- |
| `SERVER_PORT` | HTTP port for incoming traffic | `8085` or dynamic `$PORT` |
| `SPRING_DATASOURCE_URL` | Cloud MySQL connection string | `jdbc:mysql://db.host:3306/itam_db?useSSL=true` |
| `SPRING_DATASOURCE_USERNAME` | Database master user | `admin` |
| `SPRING_DATASOURCE_PASSWORD` | Secure database secret | `[Secret]` |
| `SPRING_MAIL_HOST` | Outgoing SMTP host | `smtp.gmail.com` |
| `SPRING_MAIL_PORT` | SMTP port | `587` |
| `SPRING_MAIL_USERNAME` | Authorized sender account | `it-support@organization.com` |
| `SPRING_MAIL_PASSWORD` | App-specific SMTP secret | `[Secret App Password]` |
| `ITAM_MAIL_ENABLED` | Master switch boot default | `true` or `false` |

---

## 8. REST API Endpoints Catalog

| Domain | Method | Endpoint | Description |
| :--- | :--- | :--- | :--- |
| **Authentication** | `POST` | `/api/auth/login` | Verify credentials, determine role (ADMIN vs EMPLOYEE). |
| **Assets** | `GET` | `/api/assets` | Retrieve all hardware assets with search/filters. |
| **Assets** | `POST` | `/api/assets` | Register a new asset tag and specifications. |
| **Assets** | `POST` | `/api/assets/{id}/assign` | Assign hardware to an employee and trigger email. |
| **Assets** | `POST` | `/api/assets/{id}/unassign` | Return hardware to unassigned inventory. |
| **Tickets** | `GET` | `/api/tickets` | Query support desk tickets with status filters. |
| **Tickets** | `POST` | `/api/tickets` | Raise new ticket, notify requester & assigned tech. |
| **Tickets** | `PUT` | `/api/tickets/{id}` | Update status, priority, or resolution notes. |
| **Users** | `GET` | `/api/users` | List organization personnel and assignment counts. |
| **Users** | `POST` | `/api/users` | Onboard employee, generate password, dispatch email. |
| **Employee Portal** | `GET` | `/api/my/overview?email=..` | Scoped employee data: hardware, software, tickets. |
| **Email Outbox** | `GET` | `/api/emails` | Fetch all historical notification records. |
| **Email Outbox** | `DELETE`| `/api/emails` | Purge outbox history. |
| **SMTP Switch** | `GET` | `/api/smtp/status` | Read master sending state (`enabled: true/false`). |
| **SMTP Switch** | `POST` | `/api/smtp/toggle` | 1-click master ON/OFF toggle for outgoing email. |
| **SMTP Config** | `POST` | `/api/smtp/settings` | Save host, port, credentials, and encryption flags. |
| **SMTP Test** | `POST` | `/api/smtp/test` | Transmit instantaneous test email via live SMTP. |
| **Audit Logs** | `GET` | `/api/activity-logs` | Retrieve chronological administrative audit trail. |
| **Dashboard** | `GET` | `/api/dashboard/summary` | Consolidated counts, financial totals, and KPIs. |

---

## 9. Non-Functional Requirements & Scalability Strategies

1. **Performance & Sub-Second Latency:**
   - **GZIP Compression:** Spring Boot automatically compresses all text, HTML, and JSON responses above 1 KB (`server.compression.enabled=true`), reducing network payload by ~80%.
   - **Client-Side In-Memory Cache:** The frontend caches asset and ticket lists in `App.cachedAssets`, enabling instantaneous search and filtering without round-trip database queries.
2. **Reliability & Fault Tolerance:**
   - **Outbox Isolation:** If the external SMTP server goes down or rejects credentials, the primary business action (creating a ticket or onboarding an employee) still succeeds without failing the HTTP request.
   - **Connection Pool Resilience:** HikariCP automatically validates connections on borrowing, discarding stale socket connections cleanly.
3. **Auditability & Compliance:**
   - Every administrative action is linked with a timestamp and user ID, providing ISO 27001 / SOC 2 asset compliance audit trails.
