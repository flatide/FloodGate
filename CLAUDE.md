# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository Layout

FloodGate is a self-contained standalone application (Kotlin / Spring Boot 2.7.3).

- **`floodgate2/`** — The main project directory containing both the core engine and the web application.
- **`floodgate_core/`** — Original core library (archived; sources are now inlined in floodgate2).

## Build

```bash
cd floodgate2 && mvn clean package
# Output: target/floodgate2-1.0.0.jar (executable Spring Boot JAR)
```

```bash
java -jar floodgate2/target/floodgate2-1.0.0.jar
# Starts on http://localhost:8080
```

No tests, lint, or checkstyle are configured.

## Quick Reference

- **Language**: Kotlin 2.3.0 (jvmTarget 17)
- **Source root**: `floodgate2/src/main/java/com/flatide/floodgate/`
- **Package structure**:
  - `core/` — Engine library (68 files)
  - `hub/` — Web application layer (4 files)
- **Entry point**: `Floodgate.init()` to bootstrap, `ChannelAgent.process()` to execute pipelines
- **Code comments**: Include Korean language
- **Design**: Metadata-driven pipeline — all behavior is configured via four metadata tables (API, Flow, Datasource, Template), not hardcoded
- **Singletons**: All managers use Kotlin `object` declarations
- **Connectors**: Factory-created by type — JDBC (with HikariCP), FILE, FTP, SFTP

## Package Map

All packages are under `com.flatide.floodgate`.

### `hub` — Web Application Layer

| Package | Key Classes |
|---------|-------------|
| `hub` | `Floodgate2Application`, `FloodgateInitializer`, `PipelineController`, `MetaController` |

### `core` — Engine Library

| Package | Key Classes |
|---------|-------------|
| `core` | `Floodgate`, `ConfigurationManager`, `FloodgateConstants` |
| `core.agent` | `ChannelAgent`, `ChannelJob`, `Context`, `AgentContext` |
| `core.agent.connector` | `ConnectorFactory`, `ConnectorDB`, `ConnectorFile`, `ConnectorFTP`, `ConnectorSFTP` |
| `core.agent.flow` | `Flow`, `FlowContext`, `FlowTag` |
| `core.agent.flow.module` | `Module`, `ModuleContext` |
| `core.agent.flow.rule` | `MappingRule`, `MappingRuleItem`, `FunctionProcessor` |
| `core.agent.flow.stream` | `FGInputStream`, `Payload`, `FGBlockingInputStream` |
| `core.agent.flow.stream.carrier.pipe` | `JSONPipe`, `ListPipe`, `BytePipe` |
| `core.agent.handler` | `FloodgateHandlerManager`, `FloodgateAbstractHandler` |
| `core.agent.meta` | `MetaManager`, `MetaTable` |
| `core.agent.template` | `DocumentTemplate` |
| `core.system.datasource` | `FDataSource`, `FDataSourceDB`, `FDataSourceFile` |
| `core.system.utils` | `PropertyMap`, `DBUtils`, `HttpUtils` |

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

### Singleton Managers (Kotlin `object` declarations)

| Manager | Purpose |
|---------|---------|
| `ConfigurationManager` | Hierarchical config with dot-notation keys |
| `MetaManager` | Loads/caches metadata tables (API, Flow, Datasource, Template) |
| `LoggingManager` | Manages log data sources |
| `FloodgateHandlerManager` | Lifecycle callbacks (CHANNEL_IN/OUT, FLOW_IN/OUT, MODULE_IN/OUT) |
| `ConnectorFactory` | Creates connectors by type (JDBC, FILE, FTP, SFTP) |
| `SpoolingManager` | Async job spooling |

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

### Metadata Tables

All pipeline behavior is driven by four metadata tables configured via `FloodgateConstants`:

| Config Key | Purpose |
|------------|---------|
| `meta.source.tableForAPI` | API definitions (targets, concurrency settings) |
| `meta.source.tableForFlow` | Flow definitions (modules, rules, entry points) |
| `meta.source.tableForDatasource` | Connector configurations |
| `meta.source.tableForTemplate` | Document templates for SQL/protocol generation |

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
