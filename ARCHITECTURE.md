# Open Integration Engine - High-Level Architecture Documentation

**Version:** 1.0
**Date:** 2025-11-14
**Status:** Active Development

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [System Overview](#system-overview)
3. [Architectural Principles](#architectural-principles)
4. [High-Level Architecture](#high-level-architecture)
5. [Component Architecture](#component-architecture)
6. [Data Flow Architecture](#data-flow-architecture)
7. [Deployment Architecture](#deployment-architecture)
8. [Technology Stack](#technology-stack)
9. [Security Architecture](#security-architecture)
10. [Integration Patterns](#integration-patterns)
11. [Scalability & Performance](#scalability--performance)
12. [Extension Architecture](#extension-architecture)

---

## Executive Summary

The **Open Integration Engine (OIE)** is an enterprise-grade, open-source healthcare data integration platform designed to facilitate seamless interoperability between disparate healthcare information systems. Built on a robust multi-tier architecture, OIE provides message transformation, routing, filtering, and monitoring capabilities while maintaining HIPAA compliance and supporting all major healthcare data standards.

### Key Architectural Characteristics

- **Multi-tier Architecture**: Separation of concerns across presentation, business logic, and data layers
- **Plugin-based Extensibility**: Modular design supporting custom connectors, data types, and transformations
- **Message-Oriented Middleware**: Asynchronous message processing with persistent queuing
- **Standards-Compliant**: Native support for HL7 v2/v3, FHIR, DICOM, EDI/X12, NCPDP
- **Platform-Independent**: Java-based cross-platform deployment (Linux, Windows, macOS)
- **High Availability**: Distributed deployment support with connection pooling and failover

---

## System Overview

### Purpose & Scope

OIE serves as a central integration hub that:
- **Receives** healthcare messages from source systems (EHRs, LIS, RIS, PACS, etc.)
- **Transforms** messages between different formats and standards
- **Routes** messages to appropriate destination systems based on business rules
- **Monitors** message flow, system health, and integration performance
- **Stores** message history for audit, compliance, and troubleshooting

### Target Users

1. **Integration Engineers**: Design and configure message channels
2. **Healthcare IT Administrators**: Monitor system health and manage configurations
3. **Application Developers**: Build custom connectors and transformations
4. **Compliance Officers**: Audit message trails and access logs
5. **System Integrators**: Deploy and maintain integration infrastructure

---

## Architectural Principles

### 1. Modularity
- Loosely coupled components with well-defined interfaces
- Plugin architecture for extensibility
- Independent deployment of channels

### 2. Reliability
- Persistent message queuing (store-and-forward)
- Transactional message processing
- Automatic retry mechanisms
- Comprehensive error handling and logging

### 3. Security
- Role-based access control (RBAC)
- End-to-end encryption (TLS/SSL)
- Audit logging for all operations
- HIPAA/GDPR compliance features

### 4. Performance
- Multi-threaded message processing
- Connection pooling
- Asynchronous I/O
- Configurable batch processing

### 5. Interoperability
- Standards-based protocols (HL7, FHIR, DICOM)
- Multiple transport mechanisms (HTTP, TCP, File, SMTP, JMS)
- Format-agnostic transformation engine

### 6. Maintainability
- Configuration-driven channel design
- Centralized administration
- Comprehensive monitoring and alerting
- Version control integration

---

## High-Level Architecture

### System Context Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        External Healthcare Ecosystem                     │
│                                                                           │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐ │
│  │   EHR    │  │   LIS    │  │   RIS    │  │   PACS   │  │  Other   │ │
│  │ Systems  │  │ Systems  │  │ Systems  │  │ Systems  │  │ Systems  │ │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘ │
│       │             │             │             │             │         │
└───────┼─────────────┼─────────────┼─────────────┼─────────────┼─────────┘
        │             │             │             │             │
        │ HL7/FHIR    │ HL7         │ DICOM       │ DICOM       │ Various
        ▼             ▼             ▼             ▼             ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                    Open Integration Engine (OIE)                         │
│                                                                           │
│  ┌────────────────────────────────────────────────────────────────────┐ │
│  │                     Presentation Layer                              │ │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐             │ │
│  │  │ Desktop GUI  │  │  Web Admin   │  │  REST API    │             │ │
│  │  │  (Swing)     │  │  (JSP)       │  │  (JAX-RS)    │             │ │
│  │  └──────────────┘  └──────────────┘  └──────────────┘             │ │
│  └────────────────────────────────────────────────────────────────────┘ │
│                                  │                                       │
│  ┌────────────────────────────────────────────────────────────────────┐ │
│  │                     Business Logic Layer                            │ │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐             │ │
│  │  │  Channel     │  │  User        │  │  Extension   │             │ │
│  │  │  Controller  │  │  Controller  │  │  Controller  │             │ │
│  │  └──────────────┘  └──────────────┘  └──────────────┘             │ │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐             │ │
│  │  │  Engine      │  │  Config      │  │  Alert       │             │ │
│  │  │  Controller  │  │  Controller  │  │  Controller  │             │ │
│  │  └──────────────┘  └──────────────┘  └──────────────┘             │ │
│  └────────────────────────────────────────────────────────────────────┘ │
│                                  │                                       │
│  ┌────────────────────────────────────────────────────────────────────┐ │
│  │                  Message Processing Engine (Donkey)                 │ │
│  │                                                                      │ │
│  │  ┌──────────────────────────────────────────────────────────────┐  │ │
│  │  │  Source → Filter → Transform → Route → Destinations          │  │ │
│  │  └──────────────────────────────────────────────────────────────┘  │ │
│  │                                                                      │ │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐                │ │
│  │  │  Connector  │  │   Queue     │  │  Script     │                │ │
│  │  │  Framework  │  │   Manager   │  │  Engine     │                │ │
│  │  └─────────────┘  └─────────────┘  └─────────────┘                │ │
│  └────────────────────────────────────────────────────────────────────┘ │
│                                  │                                       │
│  ┌────────────────────────────────────────────────────────────────────┐ │
│  │                     Data Persistence Layer                          │ │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐             │ │
│  │  │  Configuration│ │   Message    │  │   Audit      │             │ │
│  │  │  Database     │  │   Store      │  │   Logs       │             │ │
│  │  └──────────────┘  └──────────────┘  └──────────────┘             │ │
│  │         (Derby, MySQL, PostgreSQL, Oracle, SQL Server)             │ │
│  └────────────────────────────────────────────────────────────────────┘ │
└───────────────────────────────────────────────────────────────────────────┘
```

---

## Component Architecture

### 1. Presentation Layer

#### 1.1 Desktop Client (Swing Application)
**Purpose**: Rich desktop administration interface for advanced users

**Key Components**:
- `MirthLauncher.java` - Application bootstrap and classpath management
- Channel Editor - Visual channel designer with drag-and-drop
- Message Browser - Search and view historical messages
- Dashboard - Real-time system monitoring
- User Management - RBAC configuration

**Technology**: Java Swing, JNLP (Java Web Start compatible)

**Communication**: REST API over HTTPS

#### 1.2 Web Admin Interface (JSP/Stripes)
**Purpose**: Browser-based administration for lightweight access

**Key Components**:
- `LoginActionBean` - User authentication
- `DashboardActionBean` - System statistics
- `ChannelActionBean` - Channel management
- Stripes Framework - MVC action handling

**Technology**: JSP, Stripes Framework, HTML5/CSS/JavaScript

**Web Server**: Embedded Eclipse Jetty

#### 1.3 REST API (JAX-RS)
**Purpose**: Programmatic access for automation and integration

**Key Components**:
- `MirthServlet` - Base servlet with authentication/authorization
- 15+ specialized servlets (Channel, Message, User, Alert, etc.)
- Swagger/OpenAPI documentation

**Technology**: Jersey (JAX-RS implementation), Jackson (JSON)

**Security**: HTTP Basic Auth, session tokens, RBAC

**Location**: `/server/src/com/mirth/connect/server/api/servlets/`

---

### 2. Business Logic Layer (Controllers)

#### 2.1 Controller Architecture
Controllers implement business logic and orchestrate operations across the system.

**Core Controllers**:

| Controller | Responsibility | Location |
|------------|----------------|----------|
| `EngineController` | Channel deployment, lifecycle management | `server/controllers/EngineController.java` |
| `ChannelController` | Channel CRUD operations, metadata | `server/controllers/ChannelController.java` |
| `ConfigurationController` | System configuration, server settings | `server/controllers/ConfigurationController.java` |
| `UserController` | User authentication, session management | `server/controllers/UserController.java` |
| `ExtensionController` | Plugin installation, management | `server/controllers/ExtensionController.java` |
| `AlertController` | Alert creation, notification dispatch | `server/controllers/AlertController.java` |
| `EventController` | Event logging, audit trail | `server/controllers/EventController.java` |
| `ScriptController` | JavaScript execution, code templates | `server/controllers/ScriptController.java` |

**Design Pattern**: Singleton pattern with dependency injection (Google Guice)

**Transaction Management**: MyBatis-managed database transactions

---

### 3. Message Processing Engine (Donkey)

#### 3.1 Donkey Architecture Overview
Donkey is the high-performance message queuing and processing engine at the heart of OIE.

**Location**: `/donkey/src/main/java/com/mirth/connect/donkey/`

**Key Responsibilities**:
- Message ingestion and queuing
- Asynchronous message processing
- Persistent message storage
- Retry and error handling
- Performance statistics

#### 3.2 Channel Processing Pipeline

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Channel Pipeline                             │
│                                                                       │
│  ┌──────────────┐                                                    │
│  │   Source     │  Receives raw message from external system         │
│  │  Connector   │  (HTTP, TCP, File, Database, etc.)                │
│  └──────┬───────┘                                                    │
│         │                                                             │
│         ▼                                                             │
│  ┌──────────────┐                                                    │
│  │ Preprocessing│  Global JavaScript executed before processing      │
│  │   Script     │  Access to raw message data                        │
│  └──────┬───────┘                                                    │
│         │                                                             │
│         ▼                                                             │
│  ┌──────────────┐                                                    │
│  │   Source     │  Filters and transforms source message             │
│  │   Filter/    │  JavaScript rules, XSLT, visual mapping            │
│  │  Transform   │                                                    │
│  └──────┬───────┘                                                    │
│         │                                                             │
│         ▼                                                             │
│  ┌──────────────┐                                                    │
│  │   Message    │  Persistent storage of message and metadata        │
│  │    Queue     │  Store-and-forward capability                      │
│  └──────┬───────┘                                                    │
│         │                                                             │
│         ▼                                                             │
│  ┌──────────────────────────────────────────────────────────┐       │
│  │            Destination Router (1 to N)                    │       │
│  └──────┬────────────────────────┬──────────────────────────┘       │
│         │                        │                                   │
│         ▼                        ▼                                   │
│  ┌─────────────┐          ┌─────────────┐                           │
│  │Destination 1│          │Destination N│  Parallel processing       │
│  │  Filter/    │   ...    │  Filter/    │  Each destination:         │
│  │ Transform   │          │ Transform   │  - Independent filtering   │
│  └──────┬──────┘          └──────┬──────┘  - Transformation         │
│         │                        │          - Response handling      │
│         ▼                        ▼                                   │
│  ┌─────────────┐          ┌─────────────┐                           │
│  │Destination 1│          │Destination N│                           │
│  │  Connector  │          │  Connector  │                           │
│  └──────┬──────┘          └──────┬──────┘                           │
│         │                        │                                   │
│         ▼                        ▼                                   │
│  ┌──────────────────────────────────────────────────────────┐       │
│  │             Response Transformer (Optional)               │       │
│  │  Process responses from destinations back to source       │       │
│  └────────────────────────────────────────────────────────── ┘       │
│         │                                                             │
│         ▼                                                             │
│  ┌──────────────┐                                                    │
│  │Postprocessing│  Global JavaScript executed after all destinations │
│  │   Script     │  Cleanup, logging, notifications                   │
│  └──────────────┘                                                    │
└─────────────────────────────────────────────────────────────────────┘
```

#### 3.3 Core Donkey Components

**Channel (`Channel.java`)**
- Orchestrates the entire message flow
- Manages connector lifecycle
- Coordinates preprocessing/postprocessing
- Maintains channel statistics

**Source Connector (`SourceConnector.java`)**
- Receives messages from external systems
- Implements protocol-specific message ingestion
- Creates initial message object
- Invokes channel pipeline

**Destination Connector (`DestinationConnector.java`)**
- Sends transformed messages to target systems
- Manages destination-specific queuing
- Handles responses and acknowledgments
- Supports retry logic

**Filter/Transformer Executor (`FilterTransformerExecutor.java`)**
- Executes JavaScript transformation scripts
- Applies filtering rules
- Manages message context (channel map, connector map)
- Supports iterative message processing

**Queue Handler (`QueueHandler.java`)**
- Persistent message queuing
- Database-backed storage
- Message state transitions (pending → processing → sent/error)
- Automatic retry scheduling

**Script Engine Integration**
- Mozilla Rhino JavaScript engine (ES6 support)
- Pre-compiled script caching
- Access to Java classes and utilities
- Sandboxed execution environment

---

### 4. Connector Framework

#### 4.1 Connector Architecture

**Base Connector Interface**:
- All connectors implement `ConnectorInterface`
- Lifecycle methods: `onDeploy()`, `onUndeploy()`, `onStart()`, `onStop()`
- Message processing: `send()`, `receive()`

**Connector Types**:

1. **Source Connectors (Receivers)**
   - Listen for incoming messages
   - Polling-based or event-driven
   - Examples: HTTP Listener, TCP Listener, File Reader, Database Reader

2. **Destination Connectors (Dispatchers)**
   - Send messages to external systems
   - Request-response or fire-and-forget
   - Examples: HTTP Sender, TCP Sender, File Writer, Database Writer

#### 4.2 Built-in Connectors

| Connector | Type | Protocols | Use Cases |
|-----------|------|-----------|-----------|
| **HTTP** | Both | HTTP, HTTPS, REST, SOAP | Web services, RESTful APIs, FHIR endpoints |
| **TCP** | Both | TCP, MLLP (HL7), LLP | HL7 v2 messaging, custom TCP protocols |
| **Database (JDBC)** | Both | JDBC | Database polling, ETL, data warehousing |
| **File** | Both | Local FS, FTP, SFTP | File-based integration, batch processing |
| **SMTP** | Destination | SMTP, SMTPS | Email notifications, alerts |
| **JMS** | Both | JMS, ActiveMQ, etc. | Message-oriented middleware integration |
| **DICOM (DIMSE)** | Both | DICOM C-STORE | Medical imaging (PACS integration) |
| **WebService (SOAP)** | Destination | SOAP 1.1/1.2 | Legacy web services |
| **WebSocket** | Both | WebSocket | Real-time bidirectional communication |
| **JavaScript** | Destination | N/A | Custom scripted destinations |
| **Document** | Destination | N/A | PDF/Word document generation |
| **VM (Virtual Memory)** | Both | Internal | Inter-channel message routing |

**Location**: `/server/src/com/mirth/connect/connectors/`

#### 4.3 Connector Properties Model

Each connector has a properties class defining configuration:
- Connection parameters (host, port, URL)
- Authentication credentials
- Protocol-specific settings (frame mode, charset, timeout)
- Data handling options (binary, compression)

**Example**: `HttpReceiverProperties.java`, `TcpDispatcherProperties.java`

---

### 5. Data Type Framework

#### 5.1 Data Type Plugin Architecture

**Purpose**: Parse, serialize, and validate healthcare message formats

**Plugin Interface**:
- `DataTypeServerPlugin` - Server-side operations
- `DataTypeClientPlugin` - Client-side UI integration
- `SerializerProvider` - Message serialization/deserialization
- `BatchAdaptorProvider` - Batch message splitting

#### 5.2 Supported Data Types

| Data Type | Format | Parser | Use Case |
|-----------|--------|--------|----------|
| **HL7 v2.x** | Pipe-delimited (ER7) | HAPI HL7 | ADT, ORM, ORU messages |
| **HL7 v3** | XML | Custom XML parser | CDA documents, modern HL7 |
| **DICOM** | Binary | DCM4CHE2 | Medical images, PACS |
| **EDI/X12** | Segment-based | Custom EDI parser | Insurance claims, eligibility |
| **NCPDP** | NCPDP SCRIPT | Custom parser | Pharmacy claims |
| **XML** | XML | JDOM2, Xerces | General XML documents |
| **JSON** | JSON | Jackson | FHIR, REST APIs |
| **Delimited** | CSV, TSV, custom | Custom parser | Flat files, spreadsheets |
| **Raw** | Any | Pass-through | Binary data, unknown formats |

**Location**: `/server/src/com/mirth/connect/plugins/datatypes/`

#### 5.3 Message Transformation Flow

```
Raw Message (bytes)
     ↓
[Data Type Parser]
     ↓
Normalized XML/JSON (internal format)
     ↓
[Transformation Scripts]
     ↓
Transformed XML/JSON
     ↓
[Data Type Serializer]
     ↓
Output Message (bytes)
```

**Key Capability**: Bi-directional transformation between any supported formats
- HL7v2 → JSON (FHIR-compatible)
- DICOM → XML
- EDI → HL7v2
- CSV → HL7v2
- Custom scripted transformations

---

### 6. Data Persistence Layer

#### 6.1 Database Architecture

**ORM Framework**: Apache MyBatis 3.1.1

**Database Support**:
- Apache Derby (embedded, default)
- MySQL / MariaDB
- PostgreSQL
- Oracle Database
- Microsoft SQL Server

**Connection Pooling**: HikariCP 2.5.1
- High performance
- Automatic connection validation
- Configurable pool size and timeout

#### 6.2 Database Schema Design

**Configuration Database** (Mirth Schema):

| Table | Purpose | Key Columns |
|-------|---------|-------------|
| `CHANNEL` | Channel definitions | id, name, revision, data (serialized XML) |
| `CONFIGURATION` | Server configuration | category, name, value |
| `USER` | User accounts | id, username, password (encrypted), salt |
| `PERSON` | User profiles | id, username, first_name, last_name |
| `ALERT` | Alert definitions | id, name, enabled, expression |
| `EVENT` | Audit log | id, date_created, level, outcome, user_id |
| `CODE_TEMPLATE` | Reusable code snippets | id, name, code, library_id |
| `CODE_TEMPLATE_LIBRARY` | Code template organization | id, name, revision |

**Message Database** (Donkey Schema):

| Table | Purpose | Key Columns |
|-------|---------|-------------|
| `d_message` | Message metadata | id, channel_id, message_id, received_date |
| `d_message_content` | Message content | message_id, content_type, content, data_type |
| `d_connector_message` | Per-destination tracking | id, message_id, metadata_id, status, send_attempts |
| `d_metadata` | Custom metadata columns | name, type, column_name |
| `d_statistics` | Channel statistics | channel_id, received, sent, error, filtered |

**Location**: `/server/dbconf/` and `/donkey/donkeydbconf/`

#### 6.3 MyBatis Mapper Architecture

SQL queries are externalized in XML mapper files:
- Database-specific mappers (Derby, MySQL, PostgreSQL, Oracle, SQL Server)
- Dynamic SQL generation
- Parameterized queries (SQL injection prevention)
- Result mapping to POJOs

**Example Mappers**:
- `channel-mysql.xml` - Channel CRUD operations
- `message-postgres.xml` - Message retrieval and filtering
- `user-oracle.xml` - User authentication queries

---

## Data Flow Architecture

### Message Flow Sequence Diagram

```
External System    Source        Donkey         Destination    External System
     │            Connector      Engine         Connector            │
     │                │             │                │               │
     │──Message──────>│             │                │               │
     │                │             │                │               │
     │                │──Ingest────>│                │               │
     │                │             │                │               │
     │                │             │──Parse/────────│               │
     │                │             │  Validate      │               │
     │                │             │                │               │
     │                │             │──Filter/───────│               │
     │                │             │  Transform     │               │
     │                │             │                │               │
     │                │             │──Store in──────│               │
     │                │             │  Queue         │               │
     │                │             │                │               │
     │                │             │──Route────────>│               │
     │                │             │                │               │
     │                │             │                │──Send────────>│
     │                │             │                │               │
     │                │             │                │<──Response────│
     │                │             │                │               │
     │                │             │<──Response─────│               │
     │                │             │  Transform     │               │
     │                │             │                │               │
     │<──Response─────│<────────────│                │               │
     │                │             │                │               │
```

### Message State Transitions

```
┌─────────────┐
│   RECEIVED  │  Initial state when message arrives
└──────┬──────┘
       │
       ▼
┌─────────────┐
│  FILTERING  │  Filter rules evaluated
└──────┬──────┘
       │
       ├──────> FILTERED (if filtered out, end)
       │
       ▼
┌─────────────┐
│TRANSFORMING │  Transformation scripts executed
└──────┬──────┘
       │
       ▼
┌─────────────┐
│   QUEUED    │  Message stored in persistent queue
└──────┬──────┘
       │
       ▼
┌─────────────┐
│   PENDING   │  Waiting for destination connector
└──────┬──────┘
       │
       ▼
┌─────────────┐
│   SENDING   │  Transmission in progress
└──────┬──────┘
       │
       ├──────> SENT (successful delivery)
       │
       ├──────> ERROR (failure, may retry)
       │         │
       │         └─> QUEUED (retry scheduled)
       │
       └──────> CANCELED (manually stopped)
```

---

## Deployment Architecture

### 1. Single-Server Deployment

**Topology**: All components on one server

```
┌─────────────────────────────────────┐
│        Application Server           │
│                                     │
│  ┌─────────────────────────────┐   │
│  │   OIE Server Process        │   │
│  │   (Java Application)        │   │
│  │                             │   │
│  │  - Jetty Web Server         │   │
│  │  - Donkey Engine            │   │
│  │  - Controllers              │   │
│  │  - Connectors               │   │
│  └─────────────────────────────┘   │
│              │                      │
│              ▼                      │
│  ┌─────────────────────────────┐   │
│  │   Embedded Derby Database   │   │
│  └─────────────────────────────┘   │
└─────────────────────────────────────┘
```

**Use Case**: Development, small deployments, testing

**Pros**: Simple setup, low resource requirements
**Cons**: Single point of failure, limited scalability

---

### 2. Distributed Deployment

**Topology**: Separate application and database servers

```
┌─────────────────────────────────────┐
│     Application Server 1            │
│  ┌─────────────────────────────┐   │
│  │   OIE Server Instance 1     │   │
│  │   (Active)                  │   │
│  └──────────────┬──────────────┘   │
└─────────────────┼───────────────────┘
                  │
┌─────────────────┼───────────────────┐
│     Application Server 2            │
│  ┌──────────────▼──────────────┐   │
│  │   OIE Server Instance 2     │   │
│  │   (Standby/Load Balanced)   │   │
│  └──────────────┬──────────────┘   │
└─────────────────┼───────────────────┘
                  │
                  ▼
┌─────────────────────────────────────┐
│      Database Server (HA)           │
│  ┌─────────────────────────────┐   │
│  │  MySQL/PostgreSQL/Oracle    │   │
│  │  with Replication           │   │
│  └─────────────────────────────┘   │
└─────────────────────────────────────┘
```

**Use Case**: Production environments, high availability

**Pros**: Scalability, high availability, separate resource management
**Cons**: Complex configuration, network latency

---

### 3. Container Deployment (Docker)

```
┌─────────────────────────────────────────────────┐
│              Docker Host                        │
│                                                 │
│  ┌──────────────┐        ┌──────────────┐      │
│  │ OIE Container│        │ DB Container │      │
│  │              │        │              │      │
│  │ - Java 8+    │◄──────►│ - PostgreSQL │      │
│  │ - OIE Server │        │   or MySQL   │      │
│  │ - Port 8080  │        │              │      │
│  └──────────────┘        └──────────────┘      │
│                                                 │
│  ┌──────────────────────────────────────────┐  │
│  │       Docker Network Bridge              │  │
│  └──────────────────────────────────────────┘  │
└─────────────────────────────────────────────────┘
```

**Use Case**: Modern cloud deployments, microservices

**Pros**: Portability, easy scaling, infrastructure as code
**Cons**: Requires container orchestration knowledge

---

### 4. Network Architecture

```
                        ┌──────────────────┐
                        │   Load Balancer  │
                        │   (Optional)     │
                        └────────┬─────────┘
                                 │
                ┌────────────────┼────────────────┐
                │                │                │
                ▼                ▼                ▼
         ┌────────────┐   ┌────────────┐   ┌────────────┐
         │ OIE Node 1 │   │ OIE Node 2 │   │ OIE Node N │
         └─────┬──────┘   └─────┬──────┘   └─────┬──────┘
               │                │                │
               └────────────────┼────────────────┘
                                │
                                ▼
                    ┌───────────────────────┐
                    │  Database Cluster     │
                    │  (Shared or Sharded)  │
                    └───────────────────────┘

External Systems ◄──────────────► Firewall ◄──────────► OIE Nodes
(HL7, FHIR, etc)                   (DMZ)
```

**Security Zones**:
- **DMZ**: Load balancer, reverse proxy
- **Application Tier**: OIE server instances (private network)
- **Data Tier**: Database servers (restricted access)
- **External Zone**: Healthcare systems, internet-facing APIs

---

## Technology Stack

### Core Technologies

| Layer | Technology | Version | Purpose |
|-------|------------|---------|---------|
| **Runtime** | Java (JDK) | 8+ | Application platform |
| **Build** | Apache Ant | 1.10.14 | Build automation |
| **Web Server** | Eclipse Jetty | 9.4.x | Embedded HTTP server |
| **Web Framework** | Stripes Framework | 1.6.x | MVC for web admin |
| **REST Framework** | Jersey (JAX-RS) | 2.x | REST API implementation |
| **ORM** | Apache MyBatis | 3.1.1 | Database mapping |
| **DI Container** | Google Guice | 4.1.0 | Dependency injection |
| **Logging** | Apache Log4j | 2.17.2 | Logging framework |
| **JavaScript Engine** | Mozilla Rhino | 1.7.13 | JavaScript execution (ES6) |
| **Connection Pooling** | HikariCP | 2.5.1 | JDBC connection pool |
| **Scheduler** | Quartz | 2.3.2 | Job scheduling |

### Healthcare Libraries

| Library | Purpose |
|---------|---------|
| **HAPI HL7** | HL7 v2 parsing and generation |
| **DCM4CHE2** | DICOM protocol and image handling |
| **mirth-vocab** | HL7 vocabulary definitions |

### Utility Libraries

| Library | Purpose |
|---------|---------|
| **Apache Commons** | Utilities (IO, Collections, Lang, Codec, etc.) |
| **Jackson** | JSON processing |
| **JDOM2** | XML processing |
| **Bouncy Castle** | Cryptography (TLS, encryption) |
| **Velocity** | Template engine |
| **JSch** | SSH/SFTP support |
| **zip4j** | Archive handling |

---

## Security Architecture

### 1. Authentication & Authorization

#### Authentication Methods

1. **Database Authentication** (Default)
   - Username/password stored in database
   - Password hashing with salt (PBKDF2)
   - Session-based authentication
   - Configurable password policies

2. **LDAP/Active Directory** (Plugin)
   - External directory integration
   - Single Sign-On (SSO) capable
   - Group-based role mapping

3. **Multi-Factor Authentication (MFA)** (Plugin)
   - TOTP (Time-based One-Time Password)
   - SMS-based verification
   - Hardware token support

#### Authorization Model

**Role-Based Access Control (RBAC)**:
- User → Roles → Permissions
- Channel-level access control
- Operation-level permissions (read, write, deploy, etc.)
- API endpoint authorization

**Permission Granularity**:
- View channels
- Edit channels
- Deploy/undeploy channels
- View messages
- Process messages
- Export messages
- Manage users
- Manage system configuration

### 2. Network Security

**TLS/SSL Configuration**:
- HTTPS for web interfaces (Jetty SSL/TLS)
- TLS 1.2 and 1.3 support
- Configurable cipher suites
- Certificate management (JKS keystore)

**Connector Security**:
- HTTPS for HTTP connector
- SSL/TLS for TCP connector
- FTPS/SFTP for File connector
- SMTPS for SMTP connector
- Authenticated DICOM connections

### 3. Data Security

**Encryption**:
- Passwords encrypted in database (PBE)
- Sensitive configuration encrypted
- Message encryption at rest (plugin)
- TLS for data in transit

**Audit Logging**:
- All user actions logged
- Message access tracking (PHI audit)
- Configuration change history
- Failed authentication attempts
- Event severity levels (INFO, WARNING, ERROR)

### 4. HIPAA Compliance Features

- **PHI Access Logging**: Track who accessed which messages
- **Message Retention Policies**: Automatic pruning with configurable retention
- **Encryption Support**: End-to-end encryption capability
- **User Authentication**: Strong password policies
- **Audit Trails**: Comprehensive event logging
- **Access Controls**: Role-based restrictions
- **Data Integrity**: Database transactions, message checksums

**Location**: `/server/src/com/mirth/connect/server/util/` (encryption utilities)

---

## Integration Patterns

### 1. Point-to-Point Integration

```
System A ──────► OIE Channel ──────► System B
```

**Use Case**: Direct interface between two systems
**Example**: Lab system (LIS) → OIE → EHR

---

### 2. Publish-Subscribe (Broadcast)

```
                    ┌──────► System B
System A ──────► OIE ──────► System C
                    └──────► System D
```

**Use Case**: Distribute one message to multiple systems
**Example**: ADT feed to registration, billing, and lab systems

---

### 3. Content-Based Routing

```
                    ┌──────► System B (Lab Orders)
System A ──────► OIE ──┼──────► System C (Radiology Orders)
                    └──────► System D (Pharmacy Orders)
```

**Use Case**: Route based on message content
**Example**: Route orders to appropriate department systems

---

### 4. Message Transformation

```
System A ──────► OIE ──────► System B
(HL7 v2)      (Transform)    (FHIR JSON)
```

**Use Case**: Format conversion between systems
**Example**: Legacy HL7 v2 → Modern FHIR

---

### 5. Aggregation

```
System A ──────►
System B ──────► OIE ──────► Aggregated Message ──────► System D
System C ──────►
```

**Use Case**: Combine multiple messages into one
**Example**: Aggregate lab results from multiple instruments

---

### 6. Enrichment

```
System A ──────► OIE ──────► Enhanced Message ──────► System C
                  │
                  ▼
              Database B
           (Lookup additional data)
```

**Use Case**: Add data from external sources
**Example**: Enrich patient demographics from master patient index

---

### 7. Store and Forward

```
System A ──────► OIE Queue ──────► System B (when available)
                   (Persistent)
```

**Use Case**: Reliable delivery despite system downtime
**Example**: Queue messages when downstream system is offline

---

## Scalability & Performance

### 1. Threading Model

**Channel Threading**:
- Each channel has dedicated thread pool
- Configurable thread count per channel
- Prevents channel interference (one slow channel doesn't affect others)

**Connector Threading**:
- Source connectors: Single listener thread or poll thread
- Destination connectors: Pooled threads for parallel sending

**Configuration**:
- `mirth.properties`: Thread pool sizing
- Channel-specific thread overrides

### 2. Message Queuing

**Queue Types**:

1. **In-Memory Queues** (Fast, non-persistent)
   - Use for low-latency, high-throughput scenarios
   - Risk of message loss on server crash

2. **Database Queues** (Persistent, reliable)
   - Survives server restarts
   - Supports store-and-forward
   - Higher latency than in-memory

**Queue Configuration**:
- Per-channel queue settings
- Store-and-forward enable/disable
- Max queue size
- Queue overflow behavior

### 3. Connection Pooling

**Database Connection Pooling (HikariCP)**:
- Reusable connections for database operations
- Configurable min/max pool size
- Connection validation and timeout
- Prevents connection exhaustion

**HTTP Connection Pooling**:
- Reusable HTTP connections for HTTP dispatchers
- Keep-alive support
- Connection timeout configuration

### 4. Performance Optimization

**Message Batching**:
- Process multiple messages in batch
- Reduces per-message overhead
- Configurable batch size

**Caching**:
- Compiled script caching
- Code template caching
- Channel configuration caching

**Database Optimization**:
- Indexed message queries
- Partitioned message tables (by date, channel)
- Bulk insert operations

### 5. Monitoring & Tuning

**Performance Metrics**:
- Messages per second (throughput)
- Message processing time (latency)
- Queue depth
- Error rate
- Memory usage
- Database connection pool utilization

**JVM Tuning**:
- Heap size configuration (`*.vmoptions` files)
- Garbage collection tuning
- Thread stack size

**Location**: `/server/conf/*.vmoptions`

---

## Extension Architecture

### 1. Extension Types

| Extension Type | Purpose | Example |
|----------------|---------|---------|
| **Connector Plugin** | New communication protocols | Custom REST API, proprietary protocol |
| **Data Type Plugin** | New message formats | Custom XML schema, proprietary format |
| **Auth Plugin** | Authentication methods | SAML, OAuth 2.0, Custom SSO |
| **Codec Plugin** | Encoding/compression | Custom encryption, compression |
| **Service Plugin** | Background services | Custom monitoring, external integration |

### 2. Extension Lifecycle

```
┌───────────────┐
│   Develop     │  Implement plugin interfaces
│   Extension   │  Package as JAR with plugin.xml
└───────┬───────┘
        │
        ▼
┌───────────────┐
│   Install     │  Upload via REST API or web UI
│   Extension   │  Extract to extensions directory
└───────┬───────┘
        │
        ▼
┌───────────────┐
│    Load       │  Server reads plugin.xml
│   Extension   │  Initializes plugin classes
└───────┬───────┘
        │
        ▼
┌───────────────┐
│    Use in     │  Available in channel configuration
│   Channels    │  Runtime execution
└───────────────┘
```

### 3. Extension API

**Key Interfaces**:
- `ConnectorInterface` - Connector plugins
- `DataTypeServerPlugin` - Data type plugins
- `ServicePlugin` - Background service plugins
- `AuthenticationPlugin` - Auth providers

**Extension Metadata** (`plugin.xml`):
```xml
<pluginMetaData path="extensionname">
  <name>Extension Name</name>
  <author>Author Name</author>
  <version>1.0.0</version>
  <pluginClass>com.example.MyPlugin</pluginClass>
  <dependencies>
    <dependency>
      <name>Dependency Name</name>
      <version>2.0.0</version>
    </dependency>
  </dependencies>
</pluginMetaData>
```

**Location**: `/server/src/com/mirth/connect/plugins/` (built-in plugins)

---

## Conclusion

The Open Integration Engine architecture embodies the following key principles:

1. **Modularity**: Clean separation of concerns with pluggable components
2. **Reliability**: Persistent queuing, retry mechanisms, and transactional processing
3. **Scalability**: Multi-threaded processing, connection pooling, and distributed deployment
4. **Security**: Multi-layered security with authentication, authorization, encryption, and audit
5. **Interoperability**: Standards-based healthcare protocols and extensible data type framework
6. **Maintainability**: Configuration-driven design with comprehensive monitoring and logging

This architecture enables healthcare organizations to build robust, compliant, and scalable integration solutions while maintaining the flexibility to adapt to evolving requirements and technologies.

---

**Document Version History**:
- 1.0 (2025-11-14): Initial architecture documentation

**References**:
- Source code: `/home/user/interop-engine/`
- Build configuration: `/server/mirth-build.xml`
- Database schemas: `/server/dbconf/`, `/donkey/donkeydbconf/`
- Plugin documentation: `/server/docs/`
