# dp-grpc — Claude Code Context

## Project Overview

This repo defines the gRPC API for the **Machine Learning Data Platform (MLDP)** — a high-performance archive for PV (process variable) time-series data from large-scale research facilities such as particle accelerators. The project produces a JAR of compiled Java stubs generated from the proto files; it does not run as a standalone service.

- **GitHub**: https://github.com/osprey-dcs/dp-grpc
- **Project home**: https://github.com/osprey-dcs/data-platform
- **Java package prefix**: `com.ospreydcs.dp.grpc.v1` (generated classes use service-scoped packages such as `com.ospreydcs.dp.grpc.v1.common`, `.query`, `.ingestion`, `.annotation`, and `.ingestion_stream`)
- **Maven coordinates**: `com.ospreydcs:dp-grpc` (see `pom.xml` for current version)
- **Java target**: 21

## Repository Layout

```
src/main/proto/       # All proto files (the primary artifact of this repo)
doc/                  # Images and proposed/design proto files
.dev/plan/            # Planning documents (gitignored)
pom.xml               # Maven build; runs protoc via protobuf-maven-plugin
```

## Proto Files

| File | Purpose |
|---|---|
| `common.proto` | Shared data structures used by all services |
| `ingestion.proto` | DpIngestionService — provider registration, data ingestion, subscriptions, request status |
| `query.proto` | DpQueryService — time-series data query, PV metadata query, provider query |
| `annotation.proto` | DpAnnotationService — DataSets, Annotations, Calculations, data export |
| `ingestion_stream.proto` | DpIngestionStreamService — data event subscriptions |

Proto files are compiled by the `protobuf-maven-plugin` (0.6.1) using `protoc` and `grpc-java`. Generated Java sources land in `target/generated-sources/`.

## Key Concepts

### Column Messages (`common.proto`)
Data is stored and transmitted in **column-oriented** vectors, one column per PV per request. Column message types:

- **Scalar**: `DoubleColumn`, `FloatColumn`, `Int64Column`, `Int32Column`, `BoolColumn`, `StringColumn`, `EnumColumn`
- **Array**: `DoubleArrayColumn`, `FloatArrayColumn`, `Int64ArrayColumn`, `Int32ArrayColumn`, `BoolArrayColumn`
- **Complex**: `ImageColumn`, `StructColumn`, `SerializedDataColumn`
- **Deprecated**: `DataColumn` / `DataValue` (per-sample allocation; avoid for new ingestion)

Each column message carries an optional `ColumnMetadata metadata = 10` field (added in issue #116) containing `ColumnProvenance` (source/process), `tags`, and `attributes`.

### DataFrame (`common.proto`)
The unit of ingestion. Contains `DataTimestamps` (either a `SamplingClock` or explicit `TimestampList`) plus lists of the column message types above.

### DataBucket (`common.proto`)
The unit of query results. One PV, one time range, one column message (via `DataValues` oneof).

### DataTimestamps (`common.proto`)
Two modes:
- `SamplingClock` — start time + period (nanos) + count (uniform sampling)
- `TimestampList` — explicit list of `Timestamp` messages

### Bucket Pattern
Ingestion and storage use the [MongoDB bucket pattern](https://www.mongodb.com/blog/post/building-with-patterns-the-bucket-pattern): all sample values for a PV over a time range are stored as a single record, not one record per sample.

### Asynchronous Ingestion
Ingestion responses only confirm acceptance/rejection. Actual persistence is async. Use `queryRequestStatus()` to check outcomes.

### Response Pattern
All response messages use a `oneof result` with either `ExceptionalResult` (rejection/error) or a method-specific success payload.

## Services

### DpIngestionService (`ingestion.proto`)
- `registerProvider` — must be called before ingesting; safe to call on every client startup
- `ingestData` / `ingestDataStream` / `ingestDataBidiStream` — unary / client-streaming / bidi-streaming ingestion
- `subscribeData` — bidi-stream subscription to live PV data from the ingestion pipeline
- `queryRequestStatus` — check async ingestion request outcomes

### DpQueryService (`query.proto`)
- `queryData` / `queryDataStream` / `queryDataBidiStream` / `queryTable` — retrieve archived time-series data
- `queryPvMetadata` — PV archive metadata (first/last timestamp, data type, bucket stats)
- `queryProviders` — find providers by id, text, tags, attributes
- `queryProviderMetadata` — ingestion statistics for a provider

### DpAnnotationService (`annotation.proto`)
- `createDataSet` / `queryDataSets` — manage DataSets (blocks of PVs × time ranges)
- `createAnnotation` / `queryAnnotations` — manage Annotations (text, tags, attributes, Calculations, provenance)
- `exportData` — export DataSets and/or Calculations to HDF5, CSV, or XLSX

### DpIngestionStreamService (`ingestion_stream.proto`)
- `subscribeDataEvent` — bidi-stream subscription that fires when a `PvConditionTrigger` condition is met in the live ingestion stream, optionally returning EventData for a time window around the trigger

## Proto Conventions

- Elements within a proto file are ordered: service definition → request/response messages → supporting types.
- All method parameters are bundled into a single `*Request` message.
- Request/response message names mirror the method name (e.g., `registerProvider` → `RegisterProviderRequest` / `RegisterProviderResponse`).
- Shared messages go in `common.proto`; service-scoped messages stay in the service's proto file.
- Nested messages are used to limit scope where the type is only used within one parent message.
- Empty query results return an empty list in the result payload, not an `ExceptionalResult`.

## Build

```bash
mvn compile          # compile proto files and generate Java stubs
mvn package          # build the JAR
```

Generated Java sources appear in `target/generated-sources/protobuf/`.

## Releases

Tagged as `rel-<version>`. Release artifacts (JAR + SHA-256 checksum) are attached to GitHub releases. See `README.env` for download and verification instructions.

## Planning Artifacts

Design documents and implementation plans are stored under `.dev/plan/issue-<N>/` and are gitignored.
