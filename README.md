# Data Platform API

## API overview

The Data Platform uses the [gRPC remote procedure call (RPC) framework](https://grpc.io/) to provide the API for its Ingestion and Query Services.  Support for bulding gRPC clients and servers is provided for[many programming languages](https://grpc.io/docs/languages/).

The gRPC framework uses [Google's Protocol Buffers](https://protobuf.dev/overview) for serializing structured data.  The API is specified in text files with a ".proto" extension with definitions of both protocol buffer data structures and services.  The service definition includes the RPC methods supported by the service with method parameters and return types.

The Data Platform API includes "proto" files for both the Ingestion and Query Services that define the RPC methods and data structures specific to those services.  They both utilize a third file, "common.proto" that defines data structures common to both APIs.  

The "proto" files defining the Data Platform API are contained in the [dp-grpc repo](https://github.com/osprey-dcs/dp-grpc).  The Ingestion and Query Service APIs are discussed in more detail below, preceded by a description of the service proto files and relevant conventions.

## service proto file structure and conventions

The service "proto" files, "ingestion.proto" and "query.proto" use a similar file structure and naming conventions.

Each file imports the file "common.proto" which defines data structures in common to both services.

Each proto file includes a "service" definition block that defines the service's RPC method interface including method parameters and return types.

The remainder of each file includes data structures specific to the service API.  The "most important" data structures are listed first.

Where possible, we've tried to following the API naming conventions suggested in [this google document](https://cloud.google.com/apis/design/naming_convention).

The primary naming convention concerns the "request" and "response" types for the RPC methods.  In general, method parameters are bundled into a single gRPC request "message" (data structure) with a name that includes the RPC method name.  Likewise for the method response "message" (return type).  

In cases where it is appropriate to use the same request or response message data structure for multiple RPC methods, we do our best to indicate that in the data structure names.  Below are a simple example and then one that is a bit more complex.

A simple example is the Ingestion Service method "registerProvider()".  The method request parameters are bundled in a message data structure called "RegisterProviderRequest".  The method returns the response message type "RegisterProviderResponse".

A more complex example is the Ingestion Service RPC methods "ingestDataStream()" (bidirectional streaming data ingestion API) and "ingestData()" (unary data ingestion API).  The request method parameters to each RPC are bundled in the common message type "IngestionRequest".  The method return type is "IngestionResponse".  This pattern is also used for time series data queries defined in "query.proto".

## ingestion service API

The Ingestion Service API is defined in the dp-grpc repo's [ingestion.proto](https://github.com/osprey-dcs/dp-grpc/blob/main/src/main/proto/ingestion.proto) file.  The strcuture and naming conventions used within the file are discussed above.  The service's RPC interface is described here.  Please consult the proto files themselves for additional information about the various data structures used in the API.  They are documented there to avoid duplication and conflicting documentation with this file.

### provider registration RPC methods

#### registerProvider()

```
rpc registerProvider (RegisterProviderRequest) returns (RegisterProviderResponse);
```

The provider registration API is not yet implemented.  For now, data ingestion clients should send a unique integer identifier in ingestion requests to distinguish providers as appropriate.

### data ingestion RPC methods

#### ingestDataStream()

```
rpc ingestDataStream (stream IngestDataRequest) returns (stream IngestDataResponse);
```

The Ingestion Service performs initial validation on each IngestDataRequest in the stream, and replies immediately with a IngestDataResponse message indicating acknowledgement for a valid request, or rejection of an invalid one. The request is then added to a queue for async ingestion handling.

The ingestion handling of each request in the stream is performed asynchronously.  The Ingestion Service writes data from the request to the "buckets" collection in MongoDB, adding one document to the collection for each "column" of data in the request's DataFrame object.

A separate MongoDB "requestStatus" collection is used to note the processing status of each request, with a document for each handled request.  The collection is keyed by the providerId and clientRequestId specified in the IngestDataRequest.  This collection can be used by an administrative monitoring process to detect and notify about errors in the ingestion process.

The method returns a stream of IngestDataResponse messages, one per request.  Each response includes providerId and clientRequestId for use by the client in mapping a response to the corresponding request.  The response message only indicates if validation succeeded or failed.  Because ingestion handling is performed asynchronously, the MongoDB "requestStatus" collection must be used to determine the success or failure of individual requests.

#### ingestData()

```
rpc ingestData (IngestDataRequest) returns (IngestDataResponse);
```

This unary data ingestion API is not yet implemented.  It is anticipated that the behavior will be exactly the same as for the *ingestDataStream()* method, except that the method only supports sending a single request and receiving a single response.  The response and processing performed will be the same as for the streaming case.

## query service API

The Query Service API is defined in the dp-grpc repo's [query.proto](https://github.com/osprey-dcs/dp-grpc/blob/main/src/main/proto/query.proto) file.  The strcuture and naming conventions used within the file are discussed above.  The service's RPC interface is described here.  Please consult the proto files themselves for additional information about the various data structures used in the API.  They are documented there to avoid duplication and conflicting documentation with this file.

### time series data query RPC methods

#### queryData()

```
rpc queryData(QueryDataRequest) returns (QueryDataResponse);
```

Client sends a single QueryDataRequest with the query parameters, and receives a single QueryDataResponse with the query results. The response may indicate rejection, error in handling, no data matching query, or otherwise contains the data matching the query specification.

#### queryDataTable()

```
rpc queryDataTable(QueryDataRequest) returns (QueryTableResponse);
```

This time series data query returns its result in a tabular format, for use by the Data Platform web application.  The client sends a single QueryDataRequest with the query parameters and receives a single QueryTableResponse. The response content may indicate rejection, error in handling, no data matching query, or otherwise contains the tabular data matching the query specification.

#### queryDataStream()

```
rpc queryDataStream(QueryDataRequest) returns (stream QueryDataResponse);
```

Client sends a single QueryDataRequest with the query parameters, and receives a stream of QueryDataResponse messages with the query results. The response may indicate rejection, error in handling, no data matching query, or otherwise contains the data matching the query specification.  Results are sent in the response stream until the MongoDB cursor for the query is exhausted, or an error is encountered in processing.

The response stream is closed by the server in case of rejection, if there is an error in processing, or the result cursor is exhausted. 

We expect this to be the best performing RPC for time series data query.

#### queryDataBidiStream()

```
rpc queryDataBidiStream(stream QueryDataRequest) returns (stream QueryDataResponse);
```

Client sends a QueryDataRequest with the query parameters, and receives an initial QueryDataResponse message with the query results. 

While the MongoDB cursor for the query result contains additional details, the client sends a QueryDataRequest message with a CursorOperation payload to receive the next QueryDataResponse message in the stream.  This should continue in a loop until the query result is exhausted.

The server closes the response stream if a request is rejected, or when the result is exhausted or an error is encountered.

Each individual response may indicate rejection, error in handling, no data matching query, or otherwise contains the data matching the query specification.

### metadata query RPC methods

#### queryMetadata()

```
rpc queryMetadata(QueryMetadataRequest) returns (QueryMetadataResponse);
```

This RPC is used by clients to learn about data sources (PVs/columns) available in the archive.  Client sends a single QueryMetadataRequest with the query parameters, and receives a single QueryMetadataResponse with the query results. The response may indicate rejection, error in handling, no data matching query, or otherwise contains the data matching the query specification.

### annotations query RPC methods

#### queryAnnotations()

```
rpc queryAnnotations(QueryAnnotationsRequest) returns (QueryAnnotationsResponse);
```

This RPC is used by clients to query over annotations added to ingested data, and is not yet implemented.  Client sends a single QueryAnnotationsRequest with the query parameters, and receives a single QueryAnnotationsResponse with the query results. The response may indicate rejection, error in handling, no data matching query, or otherwise contains the data matching the query specification.
