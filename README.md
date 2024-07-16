# dp-grpc

This repo is part of the Data Platform project.  The Data Platform consists of services for capturing and providing access to data captured from a particle accelerator facility.  The [data-platform repo](https://github.com/osprey-dcs/data-platform) provides a project overview and links to the various project components, as well as an installer for running the latest version.

The Data Platform Service APIs are built using [gRPC](https://grpc.io/docs/what-is-grpc/introduction/) for both interface definition and message interchange.  Using gRPC, client applications can be built to interact with the Data Platform services using practically any programming language.

This repo contains the gRPC API definition "proto" files for use in building Data Platform client applications.

## Data Platform API

The [Data Platform Technical Overview](https://github.com/osprey-dcs/data-platform/blob/main/doc/documents/dp/dp-tech.md) contains details about the API design including the following sections:

* [gRPC background](https://github.com/osprey-dcs/data-platform/blob/main/doc/documents/dp/dp-tech.md#21-grpc-background)
* [Data Platform gRPC API proto files](https://github.com/osprey-dcs/data-platform/blob/main/doc/documents/dp/dp-tech.md#22-data-platform-grpc-api-proto-files)
* [Data Platform proto file conventions](https://github.com/osprey-dcs/data-platform/blob/main/doc/documents/dp/dp-tech.md#23-data-platform-proto-file-conventions)
  * [ordering of elements](https://github.com/osprey-dcs/data-platform/blob/main/doc/documents/dp/dp-tech.md#23-data-platform-proto-file-conventions)
  * [packaging of parameters for a method into a single "request" message](https://github.com/osprey-dcs/data-platform/blob/main/doc/documents/dp/dp-tech.md#packaging-of-parameters-for-a-method-into-a-single-request-message)
  * [naming of request and response messages](https://github.com/osprey-dcs/data-platform/blob/main/doc/documents/dp/dp-tech.md#naming-of-request-and-response-messages)
  * [nesting of messages](https://github.com/osprey-dcs/data-platform/blob/main/doc/documents/dp/dp-tech.md#nesting-of-messages)
  * [determining successful method execution](https://github.com/osprey-dcs/data-platform/blob/main/doc/documents/dp/dp-tech.md#determining-successful-method-execution)
* [Data Platform API data model](https://github.com/osprey-dcs/data-platform/blob/main/doc/documents/dp/dp-tech.md#24-data-platform-api-data-model)
  * [process variables](https://github.com/osprey-dcs/data-platform/blob/main/doc/documents/dp/dp-tech.md#241-process-variables)
  * [data vectors](https://github.com/osprey-dcs/data-platform/blob/main/doc/documents/dp/dp-tech.md#242-data-vectors)
  * [handling heterogeneous data](https://github.com/osprey-dcs/data-platform/blob/main/doc/documents/dp/dp-tech.md#243-handling-heterogeneous-data)
  * [timestamps](https://github.com/osprey-dcs/data-platform/blob/main/doc/documents/dp/dp-tech.md#244-timestamps)
  * [ingestion data frame](https://github.com/osprey-dcs/data-platform/blob/main/doc/documents/dp/dp-tech.md#245-ingestion-data-frame)
  * [bucketed time-series data](https://github.com/osprey-dcs/data-platform/blob/main/doc/documents/dp/dp-tech.md#246-bucketed-time-series-data)
  * [datasets](https://github.com/osprey-dcs/data-platform/blob/main/doc/documents/dp/dp-tech.md#247-datasets)
  * [annotations](https://github.com/osprey-dcs/data-platform/blob/main/doc/documents/dp/dp-tech.md#248-annotations)
  * [ingestion metadata](https://github.com/osprey-dcs/data-platform/blob/main/doc/documents/dp/dp-tech.md#249-ingestion-metadata)
* [Data Platform API - ingestion service](https://github.com/osprey-dcs/data-platform/blob/main/doc/documents/dp/dp-tech.md#25-data-platform-api---ingestion-service)
  * [provider registration](https://github.com/osprey-dcs/data-platform/blob/main/doc/documents/dp/dp-tech.md#251-provider-registration)
  * [data ingestion](https://github.com/osprey-dcs/data-platform/blob/main/doc/documents/dp/dp-tech.md#252-data-ingestion)
* [Data Platform API - query service](https://github.com/osprey-dcs/data-platform/blob/main/doc/documents/dp/dp-tech.md#26-data-platform-api---query-service)
  * [time-series data query](https://github.com/osprey-dcs/data-platform/blob/main/doc/documents/dp/dp-tech.md#261-time-series-data-query)
  * [metadata query](https://github.com/osprey-dcs/data-platform/blob/main/doc/documents/dp/dp-tech.md#262-metadata-query)
* [Data Platform API - annotation service](https://github.com/osprey-dcs/data-platform/blob/main/doc/documents/dp/dp-tech.md#27-data-platform-api---annotation-service)
  * [creating and querying datasets](https://github.com/osprey-dcs/data-platform/blob/main/doc/documents/dp/dp-tech.md#271-creating-and-querying-datasets)
  * [creating and querying annotations](https://github.com/osprey-dcs/data-platform/blob/main/doc/documents/dp/dp-tech.md#272-creating-and-querying-annotations)