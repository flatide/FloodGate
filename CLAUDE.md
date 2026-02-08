# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
mvn clean package
# Output: target/floodgate2-1.0.0.jar (executable Spring Boot JAR)
```

```bash
java -jar target/floodgate2-1.0.0.jar
# Starts on http://localhost:8080
# Dashboard: http://localhost:8080/index.html
# H2 Console: http://localhost:8080/h2-console (user: sa, no password)
```

No tests, lint, or checkstyle are configured.

## Quick Reference

- **Language**: Kotlin 2.3.0 (jvmTarget 17)
- **Framework**: Spring Boot 2.7.3
- **Source root**: `src/main/java/com/flatide/floodgate/`
- **Package structure**:
  - `core/` — Engine library (68 .kt files)
  - `hub/` — Web application layer (4 .kt files: Application, Initializer, PipelineController, MetaController)
- **Code comments**: Include Korean language
- **Singletons**: All managers use Kotlin `object` declarations
- **Design**: Metadata-driven pipeline — all behavior is configured via four metadata tables (API, Flow, Datasource, Template), not hardcoded

## Architecture

### Processing Pipeline

```
HTTP POST /api/{name} → PipelineController
  → ChannelAgent.process()
    → Load API metadata from MetaManager
    → Resolve targets (supports concurrent execution via ThreadPoolExecutor)
    → For each target: ChannelJob.call()
      → Flow.prepare() → Flow.process()
        → Module chain: processBefore() → process() → processAfter()
          → Connector performs READ/CREATE/UPDATE/DELETE
          → MappingRules transform data between source and target schemas
```

### Key Entry Points

- **Bootstrap**: `Floodgate.init()` — loads metadata tables, registers handlers and functions
- **Pipeline execution**: `ChannelAgent.process()` — main request processing
- **Hub→Core bridge**: `FloodgateInitializer` (Spring `ApplicationRunner`) wires Spring config into `ConfigurationManager`

### Singleton Managers (Kotlin `object` declarations)

| Manager | Purpose |
|---------|---------|
| `ConfigurationManager` | Hierarchical config with dot-notation keys |
| `MetaManager` | Loads/caches metadata tables (API, Flow, Datasource, Template) |
| `LoggingManager` | Manages log data sources |
| `FloodgateHandlerManager` | Lifecycle callbacks (CHANNEL_IN/OUT, FLOW_IN/OUT, MODULE_IN/OUT) |
| `ConnectorFactory` | Creates connectors by type (JDBC, FILE, FTP, SFTP) |
| `SpoolingManager` | Async job spooling |
| `FloodgateFunctionManager` | Pluggable function registry |

### Connector System

`ConnectorFactory` creates connectors based on the `CONNECTOR` field in metadata:
- **JDBC** → `ConnectorDB` (HikariCP pooling). Supported `DBTYPE` values: oracle, mysql, mysql_old, mariadb, postgresql, greenplum, mssql, db2, tibero
- **FILE** → `ConnectorFile`
- **FTP** → `ConnectorFTP` (Commons-Net)
- **SFTP** → `ConnectorSFTP` (JSch)

All extend `ConnectorBase` implementing the `Connector` interface with lifecycle methods: connect, beforeRead/afterRead, beforeCreate/afterCreate, read, create, update, delete, commit, rollback, close.

### Data Streaming

```
FGInputStream → Carrier (interface) → Pipe implementations
  ├── JSONPipe  (Jackson streaming for large JSON)
  ├── ListPipe  (in-memory List-based)
  └── BytePipe  (binary data)
```

### Context Hierarchy

`Context` → `AgentContext` → `FlowContext` → `ModuleContext`

### Embedded Database (H2)

The app uses an embedded H2 file database at `./data/floodgate.mv.db` for metadata storage. Schema and seed data are auto-initialized on startup:
- `src/main/resources/schema.sql` — creates 6 tables (FG_API, FG_FLOW, FG_DATASOURCE, FG_TEMPLATE, FG_LOG_API, FG_LOG_FLOW)
- `src/main/resources/data.sql` — seeds a demo pipeline (demo-api → demo-flow → file-output)
- All metadata tables use the same schema: `(ID VARCHAR(255) PK, DATA CLOB)` where DATA is JSON

### Metadata Tables

All pipeline behavior is driven by four metadata tables configured via `FloodgateConstants`:

| Config Key | Table | Purpose |
|------------|-------|---------|
| `meta.source.tableForAPI` | FG_API | API definitions (targets, concurrency settings) |
| `meta.source.tableForFlow` | FG_FLOW | Flow definitions (modules, rules, entry points) |
| `meta.source.tableForDatasource` | FG_DATASOURCE | Connector configurations |
| `meta.source.tableForTemplate` | FG_TEMPLATE | Document templates for SQL/protocol generation |

### REST Endpoints

| Endpoint | Description |
|----------|-------------|
| `GET /health` | Health check with metadata table status |
| `POST /api/{apiName}` | Execute a pipeline |
| `GET /meta/{table}` | List metadata entries |
| `GET /meta/{table}/{id}` | Read a metadata entry |
| `POST /meta/{table}` | Create a metadata entry |
| `PUT /meta/{table}` | Update a metadata entry |
| `DELETE /meta/{table}/{id}` | Delete a metadata entry |

### Dashboard UI

`src/main/resources/static/index.html` — Single-page JavaScript app with tabs for Pipeline execution, API/Flow/Datasource metadata CRUD, and log viewing.

## Configuration

`src/main/resources/application.yml` contains all Spring and FloodGate config. Key `floodgate.config` paths:
- `channel.meta.datasource` — metadata datasource name (default: "meta")
- `channel.log.*` — log table names
- `channel.spooling.folder` / `channel.payload.folder` — file paths for spooling/payload
- `meta.source.tableFor*` — metadata table names
- `datasource.meta.*` — meta datasource connection (type, url, user, password, maxPoolSize)

## Package Map

All packages are under `com.flatide.floodgate`.

| Package | Key Classes |
|---------|-------------|
| `hub` | `Floodgate2Application`, `FloodgateInitializer`, `PipelineController`, `MetaController` |
| `core` | `Floodgate`, `ConfigurationManager`, `FloodgateConstants`, `ConfigBase` |
| `core.agent` | `ChannelAgent`, `ChannelJob`, `Context`, `AgentContext` |
| `core.agent.connector` | `ConnectorFactory`, `ConnectorDB`, `ConnectorFile`, `ConnectorFTP`, `ConnectorSFTP` |
| `core.agent.connector.function` | `FloodgateFunctionManager`, `FloodgateAbstractFunction`, `DefaultEmbedFunction` |
| `core.agent.flow` | `Flow`, `FlowContext`, `FlowTag` |
| `core.agent.flow.module` | `Module`, `ModuleContext` |
| `core.agent.flow.rule` | `MappingRule`, `MappingRuleItem`, `FunctionProcessor` |
| `core.agent.flow.stream` | `FGInputStream`, `FGSharableInputStream`, `FGBlockingInputStream`, `Payload` |
| `core.agent.flow.stream.carrier.pipe` | `JSONPipe`, `ListPipe`, `BytePipe` |
| `core.agent.handler` | `FloodgateHandlerManager`, `FloodgateAbstractHandler`, `FileLogHandler` |
| `core.agent.meta` | `MetaManager`, `MetaTable`, `APIInfo` |
| `core.agent.template` | `DocumentTemplate`, `TemplateNode`, `TemplateParser` |
| `core.agent.logging` | `LoggingManager` |
| `core.agent.spool` | `SpoolingManager`, `SpoolJob` |
| `core.system.datasource` | `FDataSource`, `FDataSourceDB`, `FDataSourceFile`, `FDataSourceDefault` |
| `core.system.utils` | `PropertyMap`, `DBUtils`, `HttpUtils` |
