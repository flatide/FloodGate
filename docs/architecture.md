# FloodGate 2 — Architecture Design Document

**Version:** 1.0
**Date:** February 2026
**License:** MIT (FLATIDE LC.)

---

## 1. Executive Summary

FloodGate is a **metadata-driven data integration engine** that routes, transforms, and transfers data between heterogeneous systems — databases (Oracle, MySQL, PostgreSQL, MSSQL, DB2, Tibero, MariaDB, Greenplum), files, FTP, and SFTP — without hardcoded logic. All pipeline behavior is defined declaratively in four metadata tables. A single HTTP request triggers a multi-stage pipeline that reads from sources, applies transformation rules, and writes to targets, with optional concurrency and asynchronous spooling.

### Design Philosophy

- **Zero-code integration**: Every pipeline is a JSON document, not compiled code.
- **Streaming-first**: Data flows through bounded queues and subscriptions, never requiring full materialization.
- **Pluggable everything**: Connectors, handlers, functions, security providers, and metadata backends are all interface-based extension points.
- **Singleton coordination**: All cross-cutting managers (`MetaManager`, `ConfigurationManager`, `LoggingManager`, etc.) are Kotlin `object` singletons, eliminating dependency injection overhead while maintaining global state consistency.

---

## 2. System Context

```
                        ┌──────────────────────────────────────────────────────┐
                        │                   FloodGate 2                        │
                        │                                                      │
  Clients              │   ┌──────────┐     ┌──────────┐     ┌────────────┐  │   External Systems
  ───────              │   │   Hub    │     │   Core   │     │ Connectors │  │   ────────────────
                        │   │  Layer   │────▶│  Engine  │────▶│   Layer    │──│──▶ Oracle, MySQL,
  HTTP POST /api/*  ───│──▶│          │     │          │     │            │  │    PostgreSQL, ...
  GET/POST /meta/*  ───│──▶│ Spring   │     │ Pipeline │     │ DB / File  │──│──▶ File Systems
  WS /ws/transfer   ───│──▶│ Boot     │     │ Engine   │     │ FTP / SFTP │──│──▶ FTP Servers
  SSE /transfer/*   ◀──│──│          │     │          │     │            │  │
                        │   └──────────┘     └──────────┘     └────────────┘  │
                        │         │                                   ▲        │
  Dashboard UI      ───│──▶ Static HTML/JS                          │        │
  (index.html)          │         │          ┌─────────────┐         │        │
                        │         └─────────▶│  H2 / RDBMS │─────────┘        │
                        │                    │  (Metadata)  │                  │
                        └────────────────────┴─────────────┴──────────────────┘
```

### Deployment Model

FloodGate ships as a single Spring Boot executable JAR. It embeds an H2 file database for metadata storage by default, but can connect to any JDBC-compatible database as its metadata backend. No external message broker, cache, or container runtime is required.

| Component | Technology |
|-----------|------------|
| Language | Kotlin 2.3.0 (JVM target 17) |
| Framework | Spring Boot 2.7.3 |
| Build | Maven 3.x |
| Metadata DB | H2 (embedded, file-mode) |
| Connection Pooling | HikariCP |
| FTP | Apache Commons-Net 3.9 |
| SFTP | JSch 0.1.55 |
| JSON | Jackson Databind |
| Logging | Log4j 2 |

---

## 3. Package Architecture

```
com.flatide.floodgate
├── hub/                          ← Web Application Layer (Spring Boot)
│   ├── Floodgate2Application     ← @SpringBootApplication entry point
│   ├── FloodgateInitializer      ← ApplicationRunner: bridges Spring → Core
│   ├── PipelineController        ← POST /api/{name} — pipeline execution
│   ├── MetaController            ← CRUD for metadata tables + health check
│   ├── TransferController        ← SSE progress + REST status for file transfers
│   ├── FileTransferWebSocketHandler ← WebSocket binary streaming
│   └── WebSocketConfig           ← @EnableWebSocket registration
│
└── core/                         ← Engine Library (framework-independent)
    ├── Floodgate                 ← Bootstrap: loads metadata, registers handlers
    ├── ConfigurationManager      ← Hierarchical config access (dot-notation)
    ├── ConfigBase                ← Abstract config storage
    ├── FloodgateConstants        ← All config key constants
    │
    ├── agent/                    ← Pipeline Processing
    │   ├── Context               ← Base context with {KEY} evaluation
    │   ├── AgentContext           ← (Legacy singleton, deprecated)
    │   ├── ChannelAgent           ← Orchestrates target execution
    │   ├── ChannelJob             ← Callable: one target/flow execution
    │   │
    │   ├── flow/                 ← Flow Orchestration
    │   │   ├── Flow              ← Module-chain iterator
    │   │   ├── FlowContext        ← Per-flow state + module navigation
    │   │   ├── FlowTag            ← Flow/module metadata field names
    │   │   ├── module/
    │   │   │   ├── Module         ← Three-phase execution (Before/Process/After)
    │   │   │   └── ModuleContext   ← Per-module state
    │   │   ├── rule/
    │   │   │   ├── MappingRule     ← Column-mapping collection
    │   │   │   ├── MappingRuleItem ← Single source→target mapping
    │   │   │   └── FunctionProcessor ← DB-specific function evaluation
    │   │   └── stream/
    │   │       ├── FGInputStream        ← Abstract subscription-based stream
    │   │       ├── FGSharableInputStream ← Shareable variant
    │   │       ├── FGBlockingInputStream ← Blocking variant
    │   │       ├── Payload              ← Subscription cursor
    │   │       └── carrier/
    │   │           ├── Carrier           ← Interface: data transport
    │   │           ├── container/
    │   │           │   └── JSONContainer ← In-memory Map wrapper
    │   │           └── pipe/
    │   │               ├── JSONPipe      ← Jackson streaming JSON
    │   │               ├── ListPipe      ← In-memory List
    │   │               └── BytePipe      ← Raw binary stream
    │   │
    │   ├── connector/            ← I/O Adapters
    │   │   ├── Connector          ← Interface: full lifecycle
    │   │   ├── ConnectorBase      ← Abstract base
    │   │   ├── ConnectorFactory   ← Factory: JDBC/FILE/FTP/SFTP
    │   │   ├── ConnectorDB        ← JDBC with HikariCP + batch ops
    │   │   ├── ConnectorFile      ← File output with templates
    │   │   ├── ConnectorFTP       ← Apache Commons-Net FTP
    │   │   ├── ConnectorSFTP      ← JSch SFTP
    │   │   ├── ConnectorTag       ← Connector metadata field names
    │   │   ├── StreamingConnectorAdapter ← Raw OutputStream for transfers
    │   │   └── function/
    │   │       ├── FloodgateFunctionManager ← Custom function registry
    │   │       ├── FloodgateAbstractFunction ← Function interface
    │   │       └── DefaultEmbedFunction      ← Built-in functions
    │   │
    │   ├── handler/              ← Lifecycle Callbacks
    │   │   ├── FloodgateHandlerManager  ← Dispatches events to handlers
    │   │   ├── FloodgateAbstractHandler ← Handler interface
    │   │   └── FileLogHandler           ← Built-in file logger
    │   │
    │   ├── meta/                 ← Metadata Management
    │   │   ├── MetaManager        ← Central cache + persistence
    │   │   ├── MetaTable          ← In-memory table cache
    │   │   └── APIInfo            ← API metadata model
    │   │
    │   ├── template/             ← Document Formatting
    │   │   ├── DocumentTemplate   ← Header/Body/Footer generator
    │   │   ├── TemplateNode       ← AST node hierarchy
    │   │   └── TemplateParser     ← Template DSL parser
    │   │
    │   ├── transfer/             ← File Transfer Sessions
    │   │   ├── TransferSession        ← Per-transfer state + queue
    │   │   └── TransferProgressManager ← Active session registry
    │   │
    │   ├── logging/
    │   │   └── LoggingManager     ← Log datasource management
    │   │
    │   └── spool/                ← Async Execution
    │       ├── SpoolingManager    ← Background job queue
    │       └── SpoolJob           ← Async flow execution
    │
    └── system/                   ← Infrastructure
        ├── datasource/
        │   ├── FDataSource        ← Interface: metadata storage backend
        │   ├── FDataSourceDB      ← JDBC-backed (HikariCP + JdbcTemplate)
        │   ├── FDataSourceFile    ← File-backed (JSON files)
        │   └── FDataSourceDefault ← In-memory (HashMap)
        ├── security/
        │   ├── FloodgateSecurity           ← Encrypt/decrypt facade
        │   └── FloodgateSecurityProvider   ← Provider interface
        └── utils/
            ├── PropertyMap        ← Safe nested-map accessor
            ├── DBUtils            ← JDBC helper utilities
            └── HttpUtils          ← HTTP client utilities
```

---

## 4. Core Concepts

### 4.1 Metadata-Driven Design

All pipeline behavior is stored in four metadata tables. There is no application code that encodes routing, transformation, or connection logic — everything is data.

| Table | Purpose | Example Fields |
|-------|---------|----------------|
| **FG_API** | API endpoint definitions | `TARGET` (flow routing), `BACKUP_PAYLOAD`, `CONCURRENCY` |
| **FG_FLOW** | Flow/module chain definitions | `ENTRY`, `MODULE` (map of modules), `RULE` (mapping rules) |
| **FG_DATASOURCE** | Connection configurations | `CONNECTOR`, `URL`, `USER`, `PASSWORD`, `DBTYPE`, `PASSIVE` |
| **FG_TEMPLATE** | Document format templates | Header/Body/Footer template DSL |

All tables share the same schema: `(ID VARCHAR(255) PK, DATA CLOB)` where `DATA` is a JSON document.

**Example — Complete Pipeline Definition:**

```
FG_API "demo-api":
{
    "TARGET": { "": ["demo-flow"] },
    "BACKUP_PAYLOAD": false
}

FG_FLOW "demo-flow":
{
    "ENTRY": "writer",
    "MODULE": {
        "writer": {
            "CONNECT": "file-output",
            "ACTION": "CREATE",
            "RULE": "writer-rule",
            "TARGET": "output.json"
        }
    },
    "RULE": {
        "writer-rule": {
            "name": "name",
            "value": "value"
        }
    }
}

FG_DATASOURCE "file-output":
{
    "CONNECTOR": "FILE",
    "URL": "./data/output"
}
```

### 4.2 Context Hierarchy

Contexts carry state through the pipeline. Each level inherits from `Context` and adds stage-specific data.

```
Context (base)
  │  ● add(key, value) / get(key)
  │  ● evaluate("{KEY}") — template substitution
  │  ● Dot-notation traversal across nested contexts
  │
  ├── AgentContext (channel-level, legacy singleton)
  │
  ├── FlowContext (per-flow)
  │   ● modules: Map<String, Module>
  │   ● rules: Map<String, MappingRule>
  │   ● current: FGInputStream (data flowing through)
  │   ● Module navigation: hasNext(), setNext(), next()
  │
  └── ModuleContext (per-module)
      ● CONNECT_INFO: datasource connection metadata
      ● SEQUENCE: module execution sequences
```

### 4.3 Singleton Managers

All cross-cutting services use Kotlin `object` declarations (compile-time singletons):

| Manager | Responsibility |
|---------|---------------|
| `ConfigurationManager` | Hierarchical config with dot-notation (`datasource.meta.url`) |
| `MetaManager` | Metadata CRUD + in-memory cache backed by `FDataSource` |
| `LoggingManager` | Log datasource management |
| `FloodgateHandlerManager` | Lifecycle event dispatch to registered handlers |
| `FloodgateFunctionManager` | Custom function registry and evaluation |
| `SpoolingManager` | Background job queue with 4-worker thread pool |
| `FloodgateSecurity` | Pluggable encrypt/decrypt facade |
| `ConnectorFactory` | Connector instantiation by type |
| `TransferProgressManager` | Active file-transfer session registry |

---

## 5. Pipeline Processing

### 5.1 Request Flow

```
HTTP POST /api/demo-api
  │
  ▼
PipelineController
  │  Wraps body as JSONContainer → FGSharableInputStream
  │  Creates ChannelAgent with HTTP context
  │
  ▼
ChannelAgent.process()
  │  Loads API metadata from MetaManager
  │  Resolves target flows (supports target grouping from params)
  │  Fires CHANNEL_IN handler
  │
  │  ┌────── For each target (concurrent if CONCURRENCY.ENABLE=true) ──────┐
  │  │                                                                      │
  │  ▼                                                                      │
  │  ChannelJob.call()                                                      │
  │  │  Creates Flow for this target                                        │
  │  │  Loads flow metadata                                                 │
  │  │  Fires FLOW_IN handler                                               │
  │  │  [If SPOOLING] → serialize to disk, queue in SpoolingManager, return │
  │  │  [Otherwise] → synchronous execution:                                │
  │  │                                                                      │
  │  ▼                                                                      │
  │  Flow.prepare()                                                         │
  │  │  Creates FlowContext with all modules + rules                        │
  │  │  Sets entry module from metadata                                     │
  │  │                                                                      │
  │  ▼                                                                      │
  │  Flow.process() — Module Chain Loop                                     │
  │  │  While hasNext():                                                    │
  │  │    ┌──────────────────────────────────────────────────────────┐       │
  │  │    │  Module.processBefore()                                  │       │
  │  │    │    Load connector via ConnectorFactory                   │       │
  │  │    │    connector.connect() → establish I/O connection        │       │
  │  │    │    connector.beforeRead() or beforeCreate()              │       │
  │  │    │                                                          │       │
  │  │    │  Module.process()                                        │       │
  │  │    │    READ:   connector.read(rule) → result list            │       │
  │  │    │    CREATE: subscribe → batch → connector.create(batch)   │       │
  │  │    │                                                          │       │
  │  │    │  Module.processAfter()                                   │       │
  │  │    │    connector.commit() or rollback()                      │       │
  │  │    │    connector.close()                                     │       │
  │  │    │    Advance to CALL target (next module) if specified     │       │
  │  │    └──────────────────────────────────────────────────────────┘       │
  │  │                                                                      │
  │  │  Fires FLOW_OUT handler                                              │
  │  │  Returns result snapshot from carrier                                │
  │  └──────────────────────────────────────────────────────────────────────┘
  │
  │  Aggregates results: {"result": {target1: {...}, target2: {...}}}
  │  Fires CHANNEL_OUT handler
  │
  ▼
HTTP 200 Response
```

### 5.2 Module Execution Phases

Each module follows a strict three-phase lifecycle:

| Phase | Method | Actions |
|-------|--------|---------|
| **Before** | `processBefore()` | Resolve connector from `FG_DATASOURCE`; load document template from `FG_TEMPLATE`; establish connection; prepare statements |
| **Process** | `process()` | **READ**: Execute query, fetch rows, wrap as `FGInputStream`. **CREATE**: Subscribe to input stream, batch items by `BATCHSIZE`, write via connector |
| **After** | `processAfter()` | Commit/rollback transaction; close connection; resolve `CALL` tag for next module |

### 5.3 Pipe-Based Streaming

When a flow has two modules connected via the `PIPE` tag, data streams in bounded buffers between producer and consumer:

```
Module A (READ)          Module B (CREATE)
  │                         ▲
  │  connector.readBuffer() │  connector.createPartially()
  │     ↓ fills buffer      │     ↑ drains buffer
  └──── buffer[BUFFERSIZE] ─┘
         (bounded, back-pressure)
```

This avoids materializing the entire dataset in memory — data is processed in chunks defined by `BUFFERSIZE`.

### 5.4 Concurrent Target Execution

When `CONCURRENCY.ENABLE=true` in API metadata:

```json
{
  "TARGET": { "": ["flow-a", "flow-b", "flow-c"] },
  "CONCURRENCY": { "ENABLE": true, "THREAD_MAX": 4 }
}
```

`ChannelAgent` creates a `ThreadPoolExecutor` and submits each target as a `ChannelJob`. Results are collected via `Future.get()`.

---

## 6. Streaming Architecture

### 6.1 Carrier/Pipe Model

```
FGInputStream (abstract)
  │  subscribe() → Payload
  │  next(payload) → fills payload.data buffer
  │
  └── backed by Carrier (interface)
        │
        ├── JSONContainer — In-memory Map (header + items list)
        │     forward(): returns all items in one call
        │     getSnapshot(): returns original Map
        │
        ├── JSONPipe — Jackson streaming parser
        │     forward(): reads N items from JSON stream
        │     State machine: start(0) → seek(1) → data(2)
        │     Handles arbitrarily large JSON without full parse
        │
        ├── ListPipe — In-memory List iteration
        │     forward(): returns next bufferSize items
        │     Offset-based cursor
        │
        └── BytePipe — Raw binary stream
              forward(): reads bufferSize bytes
              BufferedInputStream-backed
```

### 6.2 File Transfer Streaming (WebSocket)

For large binary file transfers that bypass the metadata pipeline entirely:

```
Browser                       FloodGate                         Target
  │                              │                                │
  │ WS TEXT: {start,ds,file}     │                                │
  │─────────────────────────────▶│                                │
  │                              │ StreamingConnectorAdapter.open()│
  │                              │───────────────────────────────▶│
  │ WS TEXT: {ready,id,cap}      │                                │
  │◀─────────────────────────────│  Writer Thread started         │
  │                              │                                │
  │ WS BINARY: chunk (64KB)      │                                │
  │─────────────────────────────▶│ queue.put(chunk)               │
  │ WS BINARY: chunk (64KB)      │ ──▶ queue.take()               │
  │─────────────────────────────▶│      outputStream.write()─────▶│
  │        ...                   │        ...                     │
  │                              │                                │
  │ SSE: progress events ◀───────│ (500ms interval)               │
  │                              │                                │
  │ WS TEXT: {complete}          │                                │
  │─────────────────────────────▶│ END_SENTINEL → flush, close    │
  │                              │───────────────────────────────▶│
```

Backpressure: `ArrayBlockingQueue(32)` blocks `put()` when full, which blocks the Tomcat WebSocket thread, which applies TCP flow control, which raises the client's `bufferedAmount` — the client pauses sending when `bufferedAmount > chunkSize * 4`. Memory budget: 32 chunks x 64KB = **2 MB per transfer**.

---

## 7. Data Transformation

### 7.1 Mapping Rules

A mapping rule is a set of `target → source` column mappings defined in flow metadata:

```json
"RULE": {
    "writer-rule": {
        "target_col_a": "source_col_x",
        "target_col_b": "{source_col_y}",
        "target_col_c": "?",
        "target_col_d": "${FUNC_NAME}$"
    }
}
```

| Source Pattern | Action | Description |
|----------------|--------|-------------|
| `column_name` | Reference | Direct column mapping |
| `{column_name}` | Reference | Explicit reference (for special chars) |
| `?` | System | JDBC parameter placeholder |
| `${FUNC_NAME}$` | Function | Evaluated by `FunctionProcessor` |

### 7.2 Function Processors

Each connector type provides DB-specific function implementations:

| Function | Oracle | MySQL | PostgreSQL |
|----------|--------|-------|------------|
| `TARGET_DATE` | `sysdate` | `NOW()` | `NOW()` |
| `DATE` | `sysdate` | `NOW()` | `NOW()` |
| `SEQ` | Sequence value | Auto-increment | Sequence value |

Custom functions are registered via `FloodgateFunctionManager.setFunction()`.

### 7.3 Document Templates

Non-JDBC connectors (File, FTP, SFTP) use document templates for output formatting. Templates are parsed from a custom DSL into an AST:

```
TemplateNode.Root
  ├── TemplateNode.Header
  │     └── Text / Row / Column nodes
  ├── TemplateNode.Body
  │     └── Text / Row / Column / If nodes (repeated per item)
  └── TemplateNode.Footer
        └── Text / Row / Column nodes
```

Templates support conditional blocks (`If`/`Else`), variable substitution from context, and line continuation with `\`.

---

## 8. Handler System (Lifecycle Callbacks)

Handlers observe pipeline execution at six defined points:

```
CHANNEL_IN  ──▶  [ChannelAgent starts]
  FLOW_IN   ──▶    [Flow starts]
    MODULE_IN  ──▶      [Module starts]
    MODULE_PROGRESS ──▶ [Module batch progress]
    MODULE_OUT ──▶      [Module completes]
  FLOW_OUT  ──▶    [Flow completes]
CHANNEL_OUT ──▶  [ChannelAgent completes]
```

**Built-in Handler:** `FileLogHandler` — writes structured logs to `FG_LOG_API` and `FG_LOG_FLOW` tables with timestamps, status, and error details.

**Extension Point:** Implement `FloodgateAbstractHandler` and register via `FloodgateHandlerManager.addHandler()` to add monitoring, alerting, auditing, or custom side-effects.

---

## 9. Connector System

### 9.1 Connector Interface

```
Connector (interface)
  │
  ├── connect(context, module)      ← Establish connection
  ├── beforeRead / beforeCreate     ← Prepare (e.g., PreparedStatement)
  ├── read / create / update / delete ← Data operations
  ├── afterRead / afterCreate       ← Finalize (e.g., close ResultSet)
  ├── commit / rollback             ← Transaction control
  └── close                         ← Release resources
```

### 9.2 Connector Implementations

| Connector | Backend | Key Features |
|-----------|---------|-------------|
| **ConnectorDB** | JDBC (HikariCP) | Batch insert, parameterized queries, per-DBTYPE function processors, `FETCHSIZE`/`BATCHSIZE` control |
| **ConnectorFile** | `FileOutputStream` | Template-based formatting, UTF-8/configurable encoding |
| **ConnectorFTP** | Apache Commons-Net | `appendFile()` pattern, binary mode, passive mode support |
| **ConnectorSFTP** | JSch | `ChannelSftp.put()`, `StrictHostKeyChecking=no` |
| **StreamingConnectorAdapter** | All of above | Raw `OutputStream` for binary streaming (WebSocket transfers) |

### 9.3 Supported Databases

ConnectorFactory dynamically loads JDBC drivers:

| DBTYPE | Driver Class | Notes |
|--------|-------------|-------|
| `oracle` | `oracle.jdbc.driver.OracleDriver` | Oracle JDBC 19.3+ |
| `mysql` | `com.mysql.cj.jdbc.Driver` | MySQL 8+ connector |
| `mysql_old` | `com.mysql.jdbc.Driver` | Legacy MySQL connector |
| `mariadb` | `org.mariadb.jdbc.Driver` | MariaDB connector |
| `postgresql` | `org.postgresql.Driver` | PostgreSQL 42+ |
| `greenplum` | `org.postgresql.Driver` | PostgreSQL-compatible |
| `mssql` | `com.microsoft.sqlserver.jdbc.SQLServerDriver` | SQL Server JDBC |
| `db2` | `com.ibm.db2.jcc.DB2Driver` | IBM DB2 |
| `tibero` | `com.tmax.tibero.jdbc.TbDriver` | Tibero RDBMS |

---

## 10. Metadata Storage

### 10.1 Backend Abstraction

```
FDataSource (interface)
  │
  ├── FDataSourceDB      ← JDBC-backed (HikariCP + JdbcTemplate)
  │     Used in production — connects to H2, Oracle, MySQL, etc.
  │
  ├── FDataSourceFile    ← JSON files on disk
  │     One file per entry per table
  │
  └── FDataSourceDefault ← In-memory HashMap
        No persistence — used as initialization fallback
```

### 10.2 Schema

All metadata tables share a uniform two-column schema:

```sql
CREATE TABLE IF NOT EXISTS FG_API (
    ID   VARCHAR(255) PRIMARY KEY,
    DATA CLOB
);
```

The `DATA` column stores JSON. When read through `FDataSourceDB`, CLOB values are automatically parsed into `Map<String, Any>` via Jackson. This means the application works with native maps, not raw JSON strings.

### 10.3 Caching Strategy

`MetaManager` maintains an in-memory `cache: Map<String, MetaTable>` that is loaded at startup via `Floodgate.init()`. Currently, all reads go to the datasource (cache-through behavior with `fromSource = true` hardcoded), ensuring consistency at the cost of a DB round-trip per metadata read.

---

## 11. Async Execution (Spooling)

When a flow has `SPOOLING=true`, `ChannelJob` serializes the context and payload to disk, enqueues the flow ID in `SpoolingManager`, and returns immediately.

```
ChannelJob
  │  [SPOOLING=true]
  │  Serialize context + payload → ./data/spool/{flowId}.json
  │  SpoolingManager.queue.put(flowId)
  │  Return immediately with "spooled" status
  │
  ▼
SpoolingManager (background thread)
  │  queue.take() → flowId
  │  Load from disk: context, payload
  │  executor[hash(target) % 4].submit(SpoolJob)
  │
  ▼
SpoolJob.call()
  │  flow.prepare() → flow.process()
  │  Same execution as synchronous path
```

The 4-worker thread pool uses target hashing to maintain per-target ordering while allowing cross-target parallelism.

---

## 12. Security

### 12.1 Password Encryption

`FloodgateSecurity` provides a pluggable encryption facade:

```
FloodgateSecurity (object singleton)
  │  setSecurityProvider(provider)
  │  encrypt(msg) → String
  │  decrypt(msg) → String
  │
  └── FloodgateSecurityProvider (interface)
        encrypt(msg) → String
        decrypt(msg) → String
```

Connector passwords stored in `FG_DATASOURCE` metadata are decrypted at connection time via `FloodgateSecurity.decrypt()`. When no provider is configured, the `StreamingConnectorAdapter` falls back to using the raw password value.

### 12.2 Current Limitations

- No authentication on REST endpoints (all endpoints are open)
- No authorization model (any client can execute any API or modify any metadata)
- H2 console is enabled with web-allow-others and no password
- WebSocket endpoint allows all origins (`setAllowedOrigins("*")`)
- SFTP uses `StrictHostKeyChecking=no`

---

## 13. REST API Surface

### Pipeline Execution

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/{apiName}` | Execute a named pipeline |

### Metadata CRUD

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/meta/{table}` | List all entries (optional `?key=` filter) |
| `GET` | `/meta/{table}/{id}` | Read single entry |
| `POST` | `/meta/{table}` | Create entry `{ID, DATA}` |
| `PUT` | `/meta/{table}` | Update entry `{ID, DATA}` |
| `DELETE` | `/meta/{table}/{id}` | Delete entry |
| `GET` | `/health` | Health check with metadata table status |

### File Transfer

| Method | Path | Description |
|--------|------|-------------|
| `WebSocket` | `/ws/transfer` | Binary streaming file transfer |
| `GET (SSE)` | `/transfer/progress/{id}` | Real-time progress events |
| `GET` | `/transfer/active` | List all active transfers |
| `GET` | `/transfer/status/{id}` | Single transfer status |

---

## 14. Bootstrap Sequence

```
1. JVM starts → Spring Boot auto-configuration
2. Floodgate2Application (@SpringBootApplication)
3. FloodgateInitializer.run() (ApplicationRunner)
   a. Create ./data/output/ directory
   b. Bridge FloodgateProperties → ConfigurationManager
   c. Floodgate.init()
      i.   MetaManager.changeSource("meta") → FDataSourceDB with HikariCP
      ii.  LoggingManager.changeSource("meta")
      iii. MetaManager.load("FG_API")
      iv.  MetaManager.load("FG_FLOW")
      v.   MetaManager.load("FG_DATASOURCE")
      vi.  MetaManager.load("FG_TEMPLATE")
      vii. Register FileLogHandler
      viii.Register DefaultEmbedFunction
4. schema.sql → H2 creates tables (IF NOT EXISTS)
5. data.sql → H2 seeds demo pipeline
6. Tomcat starts on port 8080
7. WebSocket endpoint registered at /ws/transfer
```

---

## 15. Design Patterns

| Pattern | Where Used |
|---------|------------|
| **Singleton (object)** | All managers: `ConfigurationManager`, `MetaManager`, `FloodgateHandlerManager`, `SpoolingManager`, etc. |
| **Strategy** | `Connector` interface with DB/File/FTP/SFTP implementations |
| **Factory** | `ConnectorFactory.getConnector()` — creates connector by type |
| **Template Method** | `Module` three-phase execution: `processBefore()` → `process()` → `processAfter()` |
| **Observer** | `FloodgateHandlerManager` dispatches lifecycle events to registered handlers |
| **Producer-Consumer** | `FGInputStream`/`Payload` subscription model; `ArrayBlockingQueue` in file transfers |
| **State Machine** | `JSONPipe` parsing states: start → seek → data |
| **Chain of Responsibility** | `Flow` iterates through modules via `hasNext()`/`next()` with `CALL` chaining |
| **Abstract Factory** | `FDataSource` interface with DB/File/Default implementations |
| **Composite** | `TemplateNode` tree: Root → Header/Body/Footer → Text/Row/Column/If |
| **Context Object** | `Context` hierarchy carries state through pipeline stages |

---

## 16. Data Flow Summary

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Pipeline Path                                │
│                                                                     │
│  HTTP Body → JSONContainer → FGSharableInputStream                  │
│       → Payload.next() → Module batches → Connector.create()        │
│       → JDBC batch INSERT / File write / FTP append / SFTP put      │
│                                                                     │
│  OR (READ direction):                                               │
│  Connector.read() → List<Map> → JSONContainer/ListPipe              │
│       → FGSharableInputStream → next module or HTTP response        │
│                                                                     │
│  OR (PIPE streaming):                                               │
│  Module A: readBuffer(N) → buffer → Module B: createPartially()     │
│       Bounded buffer, no full materialization                       │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│                     File Transfer Path                              │
│                                                                     │
│  WebSocket binary chunks → ArrayBlockingQueue(32)                   │
│       → Writer thread → StreamingConnectorAdapter.OutputStream      │
│       → FILE / FTP storeFileStream() / SFTP put()                   │
│                                                                     │
│  Memory: O(queue_capacity * chunk_size) = 2 MB per transfer         │
│  No MappingRule, no DocumentTemplate, no ChannelAgent               │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 17. Configuration Reference

All configuration lives in `application.yml` under `floodgate.config`:

```yaml
floodgate:
  config:
    channel:
      meta:
        datasource: meta              # Metadata datasource name
      log:
        datasource: meta              # Log datasource name
        tableForAPI: FG_LOG_API       # API log table
        tableForFlow: FG_LOG_FLOW     # Flow log table
      spooling:
        folder: "./data/spool"        # Spooling serialization directory
      payload:
        folder: "./data/payload"      # Payload backup directory
    meta:
      source:
        tableForAPI: FG_API           # API metadata table
        tableForFlow: FG_FLOW         # Flow metadata table
        tableForDatasource: FG_DATASOURCE  # Datasource metadata table
        tableForTemplate: FG_TEMPLATE      # Template metadata table
    transfer:
      bufferQueueCapacity: 32         # Bounded queue size per transfer
      chunkSize: 65536                # 64KB chunk size
      maxConcurrentTransfers: 10      # Max parallel transfers
      timeoutSeconds: 3600            # Transfer timeout (1 hour)
    datasource:
      meta:
        type: DB                      # DB, FILE, or default
        url: "jdbc:h2:file:./data/floodgate;AUTO_SERVER=TRUE"
        user: sa
        password: ""
        maxPoolSize: 5
```

---

## 18. Key Architectural Decisions

| Decision | Rationale | Trade-off |
|----------|-----------|-----------|
| **Kotlin `object` singletons** | Zero ceremony, thread-safe initialization, no DI framework needed for core | Testing requires careful state management; tight coupling to global state |
| **Metadata in RDBMS** | Transactional, queryable, familiar; H2 for zero-install development | Schema evolution requires manual migration; JSON in CLOB is not indexed |
| **Uniform `(ID, DATA CLOB)` schema** | Any structure fits; no schema changes when metadata evolves | No column-level queries, validation, or foreign keys on metadata fields |
| **Separate Hub/Core packages** | Core engine is framework-independent; Hub wires Spring Boot | Cannot use Spring DI inside Core; manual bridging via `FloodgateInitializer` |
| **ConnectorFactory static dispatch** | Simple, predictable, no reflection or classpath scanning | Adding a new connector type requires modifying factory code |
| **Backpressure via `ArrayBlockingQueue`** | Simple, correct, JDK-native; no external library needed | Per-connection thread blocking; scales to ~10s of concurrent transfers, not 1000s |
| **File transfer as parallel path** | No risk to existing pipeline; raw binary, no transformation overhead | Two separate code paths to maintain; transfer targets don't benefit from pipeline features |

---

*Document generated from source analysis of [FloodGate](https://github.com/flatide/FloodGate) codebase.*
