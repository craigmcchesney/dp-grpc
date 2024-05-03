# dp-grpc

This repo is part of the Data Platform project.  The Data Platform consists of services for capturing and providing access to data captured from a particle accelerator facility.  The [data-platform repo](https://github.com/osprey-dcs/data-platform) provides a project overview and links to the various project components, as well as an installer for running the latest version.

The Data Platform Service APIs are built using [gRPC](https://grpc.io/docs/what-is-grpc/introduction/) for both interface definition and message interchange.  Using gRPC, client applications can be built to interact with the Data Platform services using practically any programming language.

This repo contains the gRPC API definition "proto" files for use in building Data Platform client applications.

// TODO: add link for tech document.

Pasted below is Section 2 from the [Data Platform tech document]() managed in the data-platform repo.

## Section 2: Data Platform API

This section provides additional background for the gRPC framework used to implement the Data Platform API, the data and service models reflected in the API, and mapping of the API elements to the gRPC "proto" files that define the API.


### 2.1 gRPC background

[gRPC is a framework](https://grpc.io/docs/what-is-grpc/introduction/) that allows a client application to call a method on a server application.  Defining an API with gRPC consists of identifying the services to be provided by the application, specifying the methods that can be called remotely for each service along with the method parameters and return types.

Underlying the gRPC framework is another Google-developed technology, [Protocol Buffers, which is an open source mechanism for serializing structured data](https://protobuf.dev/overview).  gRPC uses Protocol Buffers as both the Interface Definition Language (IDL), and as the underlying message interchange format.

The gRPC API is defined using "proto" files (a text file with a ".proto" extension).  Proto files contain definitions for services, service methods, and the data types used by those methods.  Data types are called "messages", and each message specifies a series of name-value pairs called "fields".  The definition of one message can be nested within another, limiting the scope of the nested data type to the message it is nested within.

See the links above for some simple examples of services, methods, and messages.

### 2.2 Data Platform gRPC API proto files

Currently, the Data Platform API defines three application services: ingestion, query, and annotation.  The methods and data types (messages) for each service are contained in individual "proto" files (e.g., "ingestion.proto", "query.proto", and "annotation.proto"), with some shared data types in "common.proto" that are included in the relevant service files via "import" statements.

### 2.3 Data Platform proto file conventions

#### ordering of elements

Within the Data Platform service proto files, elements are listed in the following order:

1. service method definitions
2. definition of request and response data types
3. definition of other shared data types

#### packaging of parameters for a method into a single "request" message

For all Data Platform service methods, parameters are bundled into a single "request" message data type, instead of listing multiple parameters to the method.

#### naming of request and response messages

The service-specific proto files each begin with a "service" definition block that defines the method interface for that service, including parameters and return types.  Where possible, the data types for the request and response use message names based on the corresponding method name.

A simple example is the Ingestion Service method "registerProvider()". The method request parameters are bundled in a message data structure called "RegisterProviderRequest". The method returns the response message type "RegisterProviderResponse".  So the method definition looks like this:

```
rpc registerProvider (RegisterProviderRequest) returns (RegisterProviderResponse);
```

A more complex example is the Ingestion Service RPC methods "ingestDataStream()" (bidirectional streaming data ingestion API) and "ingestData()" (unary data ingestion API). We want both methods to use the same request and response data types, so we use the common message types "IngestionRequest" and "IngestionResponse". This pattern is also used for time-series data queries defined in "query.proto".  The method definitions look like this:

```
rpc ingestData (IngestDataRequest) returns (IngestDataResponse);
rpc ingestDataStream (stream IngestDataRequest) returns (stream IngestDataResponse);
```

#### nesting of messages

Where possible, nesting is used to enclose simpler messages within the more complex messages that use them.  In cases where we want to share messages between multiple request or response messages, the definition of those messages appears after the request and response messages in the proto file.

#### determining successful method execution

A common pattern is used across all Data Platform service method responses to assist in determining whether an operation succeeded or failed.  All response messages use the gRPC "one of" mechanism so that the message payload is either an "ExceptionalResult" message indicating that the operation failed, or a method-specific message containing the result of a successful operation.

The "ExceptionalResult" message is defined in "common.proto" with an enum indicating the status of the operation and a descriptive message.  The enum indicates operations that were rejected, encountered an error in processing, failed to return data, resources that were unavailable when requested, etc.

Here is an example of the use of this pattern in the "QueryDataResponse" message used to send the result of time-series data queries:

```
message QueryDataResponse {

  oneof result {
    ExceptionalResult exceptionalResult = 10;
    QueryData queryData = 11;
  }

  message QueryData {

    repeated DataBucket dataBuckets = 1;

    message DataBucket {
      // DataBucket field definitions...
    }
  }
}
```

### 2.4 Data Platform API data model

The purpose of this section is to introduce some of the elements of the Data Platform's data model.  These concepts will be used in subsequent descriptions of the various service APIs.

#### 2.4.1 process variables

The core element of the Data Platform is the "process variable" (PV).  In control theory, a process variable is the current measured value of a particular part of a process that is being monitored or controlled.  The primary purpose of the Data Platform Ingestion and Query Services is to store and retrieve PV measurements.  It is assumed that each PV for a particular facility is uniquely named.  E.g., "S01:GCC01" might identify the first vacuum cold cathode gauge in sector one in the storage ring for some accelerator facility.

#### 2.4.2 data vectors

The Data Platform Ingestion and Query Service APIs for handling data work with vectors of PV measurements.  In "common.proto", this is reflected in the message data type "DataColumn", which includes a PV name and list of measurements.

#### 2.4.3 handling heterogeneous data

One requirement for the Data Platform API is to provide a general mechanism for handling heterogeneous data types for PV measurements including simple scalar values, as well as multidimensional arrays, structures, and images.   This is accomplished by the "DataValue" message data type in "common.proto",  which uses the "one of" mechanism to support a number of different data types for the values in a data vector (DataColumn).

#### 2.4.4 timestamps

Time is represented in the Data Platform API using the "Timestamp" message defined in "common.proto".  It contains two components for the number of seconds since the epoch, and nanoseconds.  As a convenience, the message "TimestampList" is used to send a list of timestamps.

#### 2.4.5 ingestion data frame

The message "IngestionDataFrame", defined in "ingestion.proto", is the primary unit of ingestion in the Data Platform API.  It contains the set of data to be ingested, using a list of "DataColumn" PV data vectors (described above).  It uses the message "DataTimestamps", defined in "common.proto", to specify the timestamps for the data values in those vectors.

"DataTimestamps" provides two mechanisms for specifying the timestamps for the data values.

A "TimestampList" (described above) may be used to send an explicit list of "Timestamp" objects.  It is assumed that each PV data vector "DataColumn" is the same size as the list of timestamps, so that there is a data value specified for each corresponding time value.

A second alternative is to use the "SamplingClock" message, defined in "common.proto".  It uses three fields to specify the data timestamps, with a start time "Timestamp", the sample period in nanoseconds, and an integer count of the number of samples.  The size of each data vector "DataColumn" in the "IngestionDataFrame" is expected to match the sample count.


#### 2.4.6 bucketed time-series data

We use the ["bucket pattern"](https://www.mongodb.com/blog/post/building-with-patterns-the-bucket-pattern) as an optimization for handling time-series data in the Data Platform API for query results, as well as for storing a vector of PV measurement values in MongoDB.  A "bucket" is a record that contains all the measurement values for a single PV for a specified time range.

This allows a list of values to be stored as a single unit in the database and returned in query results, as opposed to storing and returning individual data values and requiring that each record contains both a timestamp and data value (which effectively triples the record size for scalar data).  This leads to a more compact database, smaller gRPC messages to send query results, and improved overall performance.

A simple example of the bucket pattern follows (a slightly modified version of an example taken from the link above), demonstrating bucketing of temperature sensor data.  The first snippet shows three measurements, with one record per measurement:

```
{
   sensor_id: 12345,
   timestamp: ISODate("2019-01-31T10:00:00.000Z"),
   temperature: 40
}

{
   sensor_id: 12345,
   timestamp: ISODate("2019-01-31T10:01:00.000Z"),
   temperature: 40
}

{
   sensor_id: 12345,
   timestamp: ISODate("2019-01-31T10:02:00.000Z"),
   temperature: 41
}
```

With bucketing, we save the overhead of the sensor_id and timestamp in each record:
```
{
    sensor_id: 12345,
    start_date: ISODate("2019-01-31T10:00:00.000Z"),
    sample_period_nanos: 1_000_000_000,
    count: 3
    measurements: [ 40, 40, 41 ]
}
```

Bucketing is used to send the results of time-series data queries.  The message "QueryDataResponse" in "query.proto" contains the query result in "QueryData", which contains a list of "DataBucket" messages.  Each "DataBucket" contains a vector of data in a "DataColumn" message for a single PV, along with time expressed using "DataTimestamps" (described above), with either an explicit list of timestamps for the bucket data values, or a SamplingClock with start time and sample period.


#### 2.4.7 datasets

When designing the Data Platform's Annotation Service, we found we needed a mechanism for specifying a collection of data in the archive as the subject of an annotation.  We decided to add the notion of a "dataset", consisting of a list of "data blocks", where each "data block" specifies a list of PV names and a time range.

If you think of the entire data archive as a giant spreadsheet, with a column for each PV name and a row for each measurement timestamp, a "data block" specifies some region within that spreadsheet, and a "dataset" contains a collection of those regions.  This is illustrated in the figure below.

![dataset figure](doc/images/dataset-datablock.png "dataset figure")

"annotation.proto" defines the messages "DataSet" and "DataBlock" for use as the data model for creating annotations, where a "DataSet" includes a description and a list of "DataBlock" messages, and each "DataBlock" includes begin and end Timestamp messages (described above), and a list of PV names.

#### 2.4.8 annotations

An annotation allows clients to annotate the data archive with notes, data associations, and post-acquisition calculations.  The Data Platform Annotation Service currently supports only a "comment" annotation, but additional types of annotations will be added in future releases.

Given the definition of a "DataSet" described above, the message "CreateAnnotationRequest" in "annotation.proto" is used to create an annotation, by providing the id of the "DataSet" to be annotated (which allows us to add multiple annotations to the same DataSet), and the details for the particular type of annotation to be created.  In the case of a "CommentAnnotation", we simply specify the text of the comment.

For a link between related datasets, we might create a "LinkAnnotation" that specifies the id of the linked dataset and some text describing the relationship.

#### 2.4.9 ingestion metadata

The Ingestion Service API allows descriptive metadata to be attached to data sent to the archive.  Two types of metadata are supported, key/value attributes and event metadata.

The message "Attribute", defined in "common.proto", is a simple data structure that includes two strings, a key and a value.  The message "IngestDataRequest", in "ingestion.proto" includes an optional list of "Attribute" messages that can be used to tag the request's data as it is added to the archive.

The message "EventMetadata", also defined in "common.proto", allows incoming data to be associated with some event.  The "EventMetadata" message includes fields for the event description, with start and stop timestamps specifying the event start and stop time.

### 2.5 Data Platform API - ingestion service

"DpIngestionService" is a gRPC service defined in "ingestion.proto".  It includes methods for provider registration and data ingestion.

#### 2.5.1 provider registration

The Ingestion Service provider registration mechanism is not yet implemented.  It will assign unique identifiers to the infrastructure elements that will use the ingestion API to send data to the archive.

#### 2.5.2 data ingestion

The Ingestion Service provides a very streamlined API for ingesting data to the archive.  There are two methods for data ingestion:

```
rpc ingestData (IngestDataRequest) returns (IngestDataResponse);
rpc ingestDataStream (stream IngestDataRequest) returns (stream IngestDataResponse);
```

Both methods use the same request and response messages.  The method "ingestData()" sends a single "IngestDataRequest" and receives a single "IngestDataResponse" corresponding to the request.  "ingestDataStream()" is a bidirectional gRPC streaming method that allows the client to send a stream of "IngestDataRequest" messages and receive a stream of "IngestDataResponse" messages.  In both cases, the client uses the combination of provider id and request id to match incoming responses to outgoing requests.

##### ingestion data frame

As described above, the "IngestionDataFrame" is the primary unit of ingestion, containing a set of PV data vectors with the corresponding timestamp specification.

##### ingestion request

An "IngestDataRequest", defined in "ingestion.proto", includes an "IngestionDataFrame", optional metadata (list of key/value attributes or event metadata), a "Timestamp" indicating the time the request is sent, an id specifying the provider sending the data, and a mandatory client-generated request identifier, uniquely identifying the request for that provider.

##### ingestion response

The message "IngestDataResponse" in "ingestion.proto" contains one of two payloads, either an "ExceptionalResult" (described above) indicating an error or rejection, or an "AckResult", indicating the request was accepted and echoing back the dimensions of the request in confirmation.  The response also includes provider id and client request id for matching the response to the corresponding request, and a "Timestamp" indicating the time the message was sent.

The Ingestion Service is fully asynchronous, so the response does not indicate if a request is successfully handled, only whether the request is accepted or rejected.  A separate API for checking if a request was handled successfully will be added in a future release.  It will support queries by provider id and/or request id to identify errors in handling ingestion requests.  For now, the "requestStatus" collection in MongoDB contains a document for each request indicating whether it succeeded or failed.  It is envisioned that a monitoring tool will use the request status API  to detect ingestion errors and send notification.

### 2.6 Data Platform API - query service

"DpQueryService" is a gRPC service defined in "query.proto".  It includes methods for querying both time-series data and metadata.

#### 2.6.1 time-series data query

The Query Service provides several methods for querying time-series data, offering different options for performance and packaging of results.
```
rpc queryData(QueryDataRequest) returns (QueryDataResponse);
rpc queryDataTable(QueryDataRequest) returns (QueryTableResponse);
rpc queryDataStream(QueryDataRequest) returns (stream QueryDataResponse);
rpc queryDataBidiStream(stream QueryDataRequest) returns (stream QueryDataResponse);
```

Each method accepts a "QueryDataRequest" message, described in more detail below, to specify the query parameters.  All the time-series data query methods return "QueryDataResponse" messages except for queryDataTable(), which returns data in a tabular format via a "QueryTableResponse" message. Each of the time-series query methods and the corresponding request/response objects is discussed in more detail below.


##### queryData()

The "queryData()" method is a simple unary method with a single request and response.  The result of the query must fit in a single gRPC response message, or an error is generated.  The "QueryDataResponse" contains bucketed time-series data (described above).


##### queryDataTable()

A single request/response unary method, queryDataTable() differs in that the "QueryTableResponse" message contains a tabular result for use in clients such as the Data Platform Web Application that display data in tables.


##### queryDataStream()

Expected to be the best performing time-series data query method for retrieving a large amount of data, "queryDataStream()" accepts a single "QueryDataRequest" message and returns its result as a stream of "QueryDataResponse" messages, each of which contains bucketed time-series data.


##### queryDataBidiStream()

"queryDataBidiStream()" is a bidirectional streaming method.  This method is similar to queryDataStream(), but is used in clients that need explicit control of the response stream due to memory or performance considerations.

The client sends a single "QueryDataRequest" message, receiving a single "QueryDataResponse" with bucketed time-series data.  The client then requests the next response in the stream by sending a "QueryDataRequest" containing a "CursorOperation" method with type set to "CURSOR_OP_NEXT" until the result is exhausted and the stream is closed by the service.


##### QueryDataRequest

All time-series data query methods accept a "QueryDataRequest" message (defined in "query.proto").  The message contains one of two payloads, either a "QuerySpec" or a "CursorOperation".

A "QuerySpec" message payload specifies the parameters for a time-series data query and includes begin and end timestamps specifying the time range for the query, and a list of PV names whose data to retrieve for the specified time range.

A "CursorOperation" payload is a special case and applies only to the "queryDataBidiStream()" method.  It contains an enum value from "CursorOperationType" specifying the type of cursor operation to be executed.  Currently, the enum contains a single option "CURSOR_OP_NEXT" which requests the next message in the response stream.  We may add additional operations, e.g, "fetch the next N buckets".


##### QueryDataResponse

Except for "queryDataTable()", all time-series data query methods return "QueryDataResponse" messages.  A "QueryDataResponse" contains one of two message payloads, "ExceptionalResult" if an error is encountered or no data is found (described above) or "QueryData".

A "QueryData" message includes a list of "DataBucket" messages.  Each "DataBucket" contains a vector of data in a "DataColumn" message for a single PV, along with time expressed using "DataTimestamps" (described above), with either an explicit list of timestamps for the bucket data values, or a SamplingClock with start time and sample period.  The "DataBucket" also includes a list of key/value "Attribute" messages and/or "EventMetadata" message if specified on the ingestion request that created the bucket.


##### QueryTableResponse

The "queryDataTable()" time-series data query method returns its result via a "QueryTableResponse" message.  This is essentially a packaging of the bucketed time-series data managed by the archive into a tabular data structure for use in a client such as a web application.  A "QueryTableResponse" object contains one of two payloads, an "ExceptionalResult" if an error is encountered or no data is found (described above) or a "TableResult".

A "TableResult" message contains a list of PV column data vectors, one for each PV specified in the "QueryDataRequest".  It also contains a "DataTimestamps" message with a "TimestampList" of timestamps, one for each data row in the table.  The column data vectors are the same size as the list of timestamps, and are padded with empty values where a column doesn't contain a value at the specified timestamp.


#### 2.6.2 metadata query

The Data Platform Query Service includes a single method for querying the archive's metadata about the PVs available in the archive.

```
rpc queryMetadata(QueryMetadataRequest) returns (QueryMetadataResponse);
```

"queryMetadata()" is a single request/response unary method that accepts a "QueryMetadataRequest" and returns a "QueryMetadataResponse".


##### QueryMetadataRequest

The "QueryMetadataRequest" message is defined in "query.proto", and contains one of two payloads, "PvNameList" or "PvNamePattern".  A "PvNameList" message specifies an explicit list of PVs to find metadata for.  A "PvNamePattern" specifies a regular expression pattern for matching against PV names available in the archive.


##### QueryMetadataResponse

The "QueryMetadataResponse" message contains the result of a metadata query and includes one of two payloads, either an "ExceptionalResult" if an error is encountered or no data is found (described above) or "MetadataResult".

A "MetadataResult" message contains a list of "PvInfo" messages, one for each PV specified by the query (either explicitly in the PV name list or by matching the supplied PV name pattern).  A "PvInfo" message contains metadata for an individual PV in the archive, including name, data type, sampling clock (indicating sample period), and timestamps for the first and last PV measurement in the archive.


### 2.7 Data Platform API - annotation service

"DpAnnotationService" is a gRPC service defined in "annotation.proto".  It includes methods for creating "DataSets" (described above in section 2.4.7), and for creating and querying annotations.  The service includes a placeholder method for querying datasets, but it is not yet implemented and may be removed if not deemed to be useful.


#### 2.7.1 creating datasets

The Data Platform Annotation Service uses datasets to identify the relevant data within the archive for a particular annotation.  The API includes a single method for creating datasets.
```
rpc createDataSet(CreateDataSetRequest) returns (CreateDataSetResponse);
```

This is a single request/response unary method for creating a dataset.  It accepts a "CreateDataSetRequest" message and returns a "CreateDataSetResponse".


##### CreateDataSetRequest

A "CreateDataSetRequest" message contains a "DataSet" message with details of the dataset to be created, e.g., its list of "DataBlock" messages.


##### CreateDataSetResponse

A "CreateDataSetResponse" message contains one of two payloads, an "ExceptionalResult" message if a rejection or error was encountered creating the dataset, or a "CreateDataSetResult".

A "CreateDataSetResult" message simply contains the unique identifier assigned to the new dataset if it was created successfully.


#### 2.7.2 creating and querying annotations

The Data Platform Annotation Service provides two methods related to annotations.

```
rpc createAnnotation(CreateAnnotationRequest) returns (CreateAnnotationResponse);
rpc queryAnnotations(QueryAnnotationsRequest) returns (QueryAnnotationsResponse);
```


##### createAnnotation()

The method "createAnnotation()" creates an annotation for the specified dataset.  It accepts a "CreateAnnotationRequest" message and returns a "CreateAnnotationResponse" message.


##### CreateAnnotationRequest

A "CreateAnnotationRequest" message specifies the id of the owner creating the annotation, and the id of the dataset to be annotated.  It uses a variable "one of" payload for specifying the details specific to the type of annotation being created.  Currently, there is a single type of annotation, "CommentAnnotation", which includes the text of the comment for the annotation.


##### CreateAnnotationResponse

A "CreateAnnotationResponse" message is used to return the result of the "createAnnotation()" method.  It includes one of two payloads, either an "ExceptionalResult" (described above) if an error is encountered creating the annotation, or a "CreateAnnotationResult" if the operation is successful.

A "CreateAnnotationResult" message simply contains the unique identifier assigned to the annotation.


##### queryAnnotations()

The "queryAnnotations()" method is a single request/response method that searches for annotations in the archive that match the search criteria specified for the query.  It accepts a "QueryAnnotationsRequest" message and returns a "QueryAnnotationsResponse" message.


##### QueryAnnotationsRequest

A "QueryAnnotationsRequest" encapsulates the criteria for the query.  It contains a list of "QueryAnnotationsCriterion" messages.

The "QueryAnnotationsCriterion" message defines a number of different criteria message types that can be added to the criterion list, including an "OwnerCriterion" (specifying the owner id to match in the annotation query) and "CommentCriterion" (specifying text to match against annotation comments).  Other types of criterion messages will be added as additional types of annotations are defined.


##### QueryAnnotationsResponse

The "queryAnnotations()" method returns a "QueryAnnotationsResponse" message with the query results.  It contains one of two payloads, either an "ExceptionalResult" message if the query encountered an error or returned no data (described above), or an "AnnotationsResult" message with the results if the query was successful.

The "AnnotationsResult" message includes a list of "Annotation" messages, one for each annotation that matches the query's search criteria.

An "Annotation" message includes the unique id of the annotation, the owner id, the id of the associated dataset identifying the data in the archive that the annotation applies to, and for convenience (so that a second query to retrieve the dataset is not required) a "DataSet" message containing the list of "DataBlocks" comprising the annotation's dataset.