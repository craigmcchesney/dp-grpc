# dp-grpc

This repo is part of the Data Platform project.  The Data Platform consists of services for capturing and providing access to data captured from a particle accelerator facility.  The [data-platform repo](https://github.com/osprey-dcs/data-platform) provides a project overview and links to the various project components, as well as an installer for running the latest version.

The Data Platform Service APIs are built using [gRPC](https://grpc.io/docs/what-is-grpc/introduction/) for both interface definition and message interchange.  Using gRPC, client applications can be built to interact with the Data Platform services using practically any programming language.

This repo contains the gRPC API definition "proto" files for use in building Data Platform client applications.

## Data Platform API

The [Data Platform Technical Overview](https://github.com/osprey-dcs/data-platform/blob/main/doc/documents/dp/dp-tech.md) contains details about the API design including the following sections:

* [gRPC background](https://github.com/osprey-dcs/data-platform/blob/main/doc/documents/dp/dp-tech.md#21-grpc-background)
* [Data Platform gRPC API proto files](https://github.com/osprey-dcs/data-platform/blob/main/doc/documents/dp/dp-tech.md#22-data-platform-grpc-api-proto-files)
* [Data Platform proto file conventions](https://github.com/osprey-dcs/data-platform/blob/main/doc/documents/dp/dp-tech.md#23-data-platform-proto-file-conventions)
* [Data Platform API data model](https://github.com/osprey-dcs/data-platform/blob/main/doc/documents/dp/dp-tech.md#24-data-platform-api-data-model)
* [Data Platform API - ingestion service](https://github.com/osprey-dcs/data-platform/blob/main/doc/documents/dp/dp-tech.md#25-data-platform-api---ingestion-service)
* [Data Platform API - query service](https://github.com/osprey-dcs/data-platform/blob/main/doc/documents/dp/dp-tech.md#26-data-platform-api---query-service)
* [Data Platform API - annotation service](https://github.com/osprey-dcs/data-platform/blob/main/doc/documents/dp/dp-tech.md#27-data-platform-api---annotation-service)