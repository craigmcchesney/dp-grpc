package com.ospreydcs.dp.grpc.benchmark;

import com.ospreydcs.dp.grpc.v1.benchmark.BenchmarkGrpc;
import com.ospreydcs.dp.grpc.v1.benchmark.Int64Msg;
import com.ospreydcs.dp.grpc.v1.benchmark.SnapshotDataResponse;
import com.ospreydcs.dp.grpc.v1.common.Data;
import com.ospreydcs.dp.grpc.v1.common.Datum;
import com.ospreydcs.dp.grpc.v1.ingestion.Ingestion;
import io.grpc.*;
import io.grpc.stub.StreamObserver;

import java.text.DecimalFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class BenchmarkClient {
    private static final Logger logger = LogManager.getLogger();

    private final BenchmarkGrpc.BenchmarkBlockingStub blockingStub;
    private final BenchmarkGrpc.BenchmarkStub asyncStub;

    /** Construct client for accessing Benchmark server using the existing channel. */
    public BenchmarkClient(Channel channel) {
        // 'channel' here is a Channel, not a ManagedChannel, so it is not this code's responsibility to
        // shut it down.

        // Passing Channels to code makes code easier to test and makes it easier to reuse Channels.
        blockingStub = BenchmarkGrpc.newBlockingStub(channel);
        asyncStub = BenchmarkGrpc.newStub(channel);
    }

    /** Send message to server. */
    public void sendUnarySpamRequest() {
        long value = 42;
        logger.info("Sending request value: " + value + " ...");
        Int64Msg request = Int64Msg.newBuilder().setValue(value).build();
        Int64Msg response;
        try {
            response = blockingStub.unarySpam(request);
        } catch (StatusRuntimeException e) {
            logger.warn("RPC failed: {}", e.getStatus());
            return;
        }
        logger.info("Response value: " + response.getValue());
    }

    public void sendStreamingSpamRequest() {
        long n = (long) Math.pow(10, 6);
        Int64Msg request = Int64Msg.newBuilder().setValue(n).build();
        Iterator<Int64Msg> responseStream;
        Instant t0 = Instant.now();
        int responseCount = 0;
        try {
            responseStream = blockingStub.streamingSpam(request);
            while (responseStream.hasNext()) {
                Int64Msg responseMsg = responseStream.next();
                responseCount = responseCount + 1;
            }
        } catch (StatusRuntimeException e) {
            logger.warn("RPC failed: {}", e.getStatus());
            return;
        }
        Instant t1 = Instant.now();
        long dt = t0.until(t1, ChronoUnit.SECONDS);

        // commented out because dt can be zero
//        double rate = n/dt;
//        logger.log(Level.INFO, "{} counts in {} sec for {} cnts/sec", n, dt, rate);

        logger.info("{} counts in {} sec", n, dt);

    }

    private Ingestion.SnapshotData buildSnapshotDataRequest(int numRows, int numColumns) {
        Ingestion.SnapshotData.Builder snapshotDataBuilder = Ingestion.SnapshotData.newBuilder();
        // build list of column values for each column
        for (int colIndex = 1 ; colIndex <= numColumns ; colIndex++) {
            Data.Builder dataBuilder = Data.newBuilder();
            dataBuilder.setName("col_" + colIndex);
            for (int rowIndex = 1 ; rowIndex <= numRows ; rowIndex++) {
                Datum rowDatum = Datum.newBuilder().setFloatValue(rowIndex).build();
                dataBuilder.addData(rowDatum);
//                // use this commented code to look at serialized size of double data
//                int datumSize = rowDatum.getSerializedSize();
//                logger.info("serialized double size: {}", datumSize);
            }
            dataBuilder.build();
            snapshotDataBuilder.addPvData(dataBuilder);
        }
        return snapshotDataBuilder.build();
    }

    public void sendUnaryIngestionRequest() {

        // chris: scorpius requirement is equivalent to a [1k row x 10k] data frame every second.
        // Our stated goal is one [1k row x 4k] data
        // NOTE: tried 1000x1000 but get "gRPC message exceeds maximum size 4194304: 11011893"
        int numRows = 1000;
        int numColumns = 100;
        int numRequests = 1000;

        Ingestion.SnapshotData.Builder snapshotDataBuilder = Ingestion.SnapshotData.newBuilder();
        // build list of column values for each column
        for (int colIndex = 1 ; colIndex <= numColumns ; colIndex++) {
            Data.Builder dataBuilder = Data.newBuilder();
            dataBuilder.setName("col_" + colIndex);
            for (int rowIndex = 1 ; rowIndex <= numRows ; rowIndex++) {
                Datum rowDatum = Datum.newBuilder().setFloatValue(rowIndex).build();
                dataBuilder.addData(rowDatum);
            }
            dataBuilder.build();
            snapshotDataBuilder.addPvData(dataBuilder);
        }
        Ingestion.SnapshotData request = snapshotDataBuilder.build();
        long requestNumBytes = Double.BYTES * numColumns * numRows;
        logger.info("data bytes per request: {}", requestNumBytes);

        long totalBytesSent = 0;
        Instant t0 = Instant.now();
        for (int requestIndex = 1 ; requestIndex <= numRequests ; requestIndex++) {
            Ingestion.SnapshotID response;
            try {
                response = blockingStub.unaryIngestion(request);
            } catch (StatusRuntimeException e) {
                logger.warn("RPC failed: {}", e.getStatus());
                return;
            }
            totalBytesSent = totalBytesSent + requestNumBytes;
//            logger.info("request {}: received snapshotId: {}", requestIndex, response.getSnapshotID());
        }
        Instant t1 = Instant.now();
        long dtMillis = t0.until(t1, ChronoUnit.MILLIS);
        double dtSeconds = dtMillis / 1_000.0;
        double totalMbytesSent = totalBytesSent / 1_000_000.0;
        double dataRate = totalMbytesSent / dtSeconds;

        logger.info("{} Mbytes in {} sec {} Mbytes/sec", totalMbytesSent, dtSeconds, dataRate);
    }

    public static IngestionStreamResult sendStreamingIngestionRequest(BenchmarkGrpc.BenchmarkStub asyncStub, int streamNumber, int numRequests, int numRows, int numColumns, Ingestion.SnapshotData request) {
        final CountDownLatch finishLatch = new CountDownLatch(1);
        final boolean[] responseError = {false}; // must be final for access by inner class, but we need to modify the value, so final array
        final boolean[] runtimeError = {false}; // must be final for access by inner class, but we need to modify the value, so final array
        StreamObserver<SnapshotDataResponse> responseObserver = new StreamObserver<SnapshotDataResponse>() {
            @Override
            public void onNext(SnapshotDataResponse response) {
                boolean isError = false;
                int rowCount = response.getRowCount();
                int colCount = response.getColCount();
                boolean dataError = response.getStatus().getDataError();
                String errorMsg = response.getStatus().getStatusMsg();
                logger.debug("stream: {} received response rowCount: {} colCount: {} errorMsg: {}"
                        , streamNumber, rowCount, colCount, errorMsg);
                if (dataError) {
                    logger.error("stream: {} response error flag is set msg: {}", errorMsg);
                    isError = true;
                }
                if (rowCount != numRows) {
                    logger.error("stream: {} response rowCount: {} doesn't match expected rowCount: {}", streamNumber, rowCount, numRows);
                    isError = true;
                }
                if (colCount != numColumns) {
                    logger.error("stream: {} response colCount: {} doesn't match expected colCount: {}", streamNumber, colCount, numColumns);
                    isError = true;
                }
                if (isError) {
                    responseError[0] = true;
                }
            }

            @Override
            public void onError(Throwable t) {
                Status status = Status.fromThrowable(t);
                logger.error("stream: {} StreamingIngestion Failed status: {} message: {}", streamNumber, status, t.getMessage());
                runtimeError[0] = true;
                finishLatch.countDown();
            }

            @Override
            public void onCompleted() {
                logger.info("stream: {} Finished StreamingIngestion", streamNumber);
                finishLatch.countDown();
            }
        };

        StreamObserver<Ingestion.SnapshotData> requestObserver = asyncStub.streamingIngestion(responseObserver);

//        // chris: scorpius requirement is equivalent to a [1k row x 10k] data frame every second.
//        // Our stated goal is one [1k row x 4k] data
//        // NOTE: tried 1000x1000 but get "gRPC message exceeds maximum size 4194304: 11011893"
//        Ingestion.SnapshotData request = buildSnapshotDataRequest(numRows, numColumns);

        IngestionStreamResult result = new IngestionStreamResult();

        long dataValuesSubmitted = 0;
        long dataBytesSubmitted = 0;
        long grpcBytesSubmitted = 0;
        try {
            for (int requestIndex = 1; requestIndex <= numRequests; requestIndex++) {
                logger.debug("stream: {} sending request: {}", streamNumber, requestIndex);
                requestObserver.onNext(request);
                dataValuesSubmitted = dataValuesSubmitted + (numRows * numColumns);
                dataBytesSubmitted = dataBytesSubmitted + (numRows * numColumns * Double.BYTES);
                grpcBytesSubmitted = grpcBytesSubmitted + request.getSerializedSize();
                // totalBytesSent = totalBytesSent + requestNumBytes;
                if (finishLatch.getCount() == 0) {
                    // RPC completed or errored before we finished sending.
                    // Sending further requests won't error, but they will just be thrown away.
                    result.setStatus(false);
                    return result;
                }
            }
        } catch (RuntimeException e) {
            logger.error("stream: {} StreamingIngestion failed: {}", streamNumber, e.getMessage());
            // cancel rpc, onError() sets runtimeError[0]
            requestObserver.onError(e);
            throw e;
        }

        // mark the end of requests
        requestObserver.onCompleted();

        // receiving happens asynchronously
        try {
            finishLatch.await(1, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            logger.error("stream: {} StreamingIngestion encountered timeout waiting for stream completion", streamNumber);
            result.setStatus(false);
            return result;
        }

        if (responseError[0]) {
            logger.error("stream: {} StreamingIngestion response error encountered", streamNumber);
            result.setStatus(false);
            return result;
        } else if (runtimeError[0]) {
            logger.error("stream: {} StreamingIngestion runtime error encountered", streamNumber);
            result.setStatus(false);
            return result;
        } else {
            result.setStatus(true);
            result.setDataValuesSubmitted(dataValuesSubmitted);
            result.setDataBytesSubmitted(dataBytesSubmitted);
            result.setGrpcBytesSubmitted(grpcBytesSubmitted);
            return result;
        }
    }

    class IngestionStreamTask implements Callable<IngestionStreamResult> {

        private int streamNumber;
        private int numRequests;
        private int numRows;
        private int numColumns;
        private Ingestion.SnapshotData request;

        public IngestionStreamTask(int streamNumber, int numRequests, int numRows, int numColumns, Ingestion.SnapshotData request) {
            this.streamNumber = streamNumber;
            this.numRequests = numRequests;
            this.numRows = numRows;
            this.numColumns = numColumns;
            this.request = request;
        }

        @Override
        public IngestionStreamResult call() throws Exception {
            IngestionStreamResult result = sendStreamingIngestionRequest(asyncStub, streamNumber, numRequests, numRows, numColumns, request);
            return result;
        }

    }

    public void multithreadedStreamingIngestionScenario() {
        // specify number of rows, columns, and requests to send in each stream
        // one minute of data at 4000 PVs x 1000 samples per second 1000 rows 250 columns (960 requests / numThreads)
        int numRows = 1000;
        int numColumns = 250;
        int numRequests = 480;

        // specify number of streams/threads
        int numThreads = 2;

        // initialize request for each stream
        Map<Integer,Ingestion.SnapshotData> streamRequestMap = new HashMap<>();
        for (int i = 1; i <= numThreads ; i++) {
            streamRequestMap.put(i, buildSnapshotDataRequest(numRows, numColumns));
        }

        Instant t0 = Instant.now();

        boolean success = true;
        long dataValuesSubmitted = 0;
        long dataBytesSubmitted = 0;
        long grpcBytesSubmitted = 0;

        if (numThreads == 1) {
            // don't use thread pool for single thread
            logger.info("single thread specified, not creating thread pool");
            // send stream of SnapshotData requests
            IngestionStreamResult ingestionResult =
                    sendStreamingIngestionRequest(asyncStub,1, numRequests, numRows, numColumns, streamRequestMap.get(1));
            success = ingestionResult.getStatus();
            dataValuesSubmitted = dataValuesSubmitted + ingestionResult.getDataValuesSubmitted();
            dataBytesSubmitted = dataBytesSubmitted + ingestionResult.getDataBytesSubmitted();
            grpcBytesSubmitted = grpcBytesSubmitted + ingestionResult.getGrpcBytesSubmitted();

        } else {
            logger.info("creating thread pool of size: {}", numThreads);
            var executorService = Executors.newFixedThreadPool(numThreads);
            List<IngestionStreamTask> taskList = new ArrayList<>();
            for (int i = 1 ; i <= numThreads ; i++) {
                IngestionStreamTask task = new IngestionStreamTask(i, numRequests, numRows, numColumns, streamRequestMap.get(i));
                taskList.add(task);
            }
            List<Future<IngestionStreamResult>> resultList = null;
            try {
                resultList = executorService.invokeAll(taskList);
                executorService.shutdown();
                if (executorService.awaitTermination(1, TimeUnit.MINUTES)) {
                    for (int i = 0 ; i < resultList.size() ; i++) {
                        Future<IngestionStreamResult> future = resultList.get(i);
                        IngestionStreamResult ingestionResult = future.get();
                        if (!ingestionResult.getStatus()) {
                            success = false;
                        }
                        dataValuesSubmitted = dataValuesSubmitted + ingestionResult.getDataValuesSubmitted();
                        dataBytesSubmitted = dataBytesSubmitted + ingestionResult.getDataBytesSubmitted();
                        grpcBytesSubmitted = grpcBytesSubmitted + ingestionResult.getGrpcBytesSubmitted();
                    }
                    if (!success) {
                        logger.error("thread pool future for sendStreamingIngestionRequest() returned false");
                    }
                } else {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException | ExecutionException ex) {
                executorService.shutdownNow();
                logger.warn("Data transmission Interrupted by exception: {}", ex.getMessage());
                Thread.currentThread().interrupt();
            }
        }

        if (success) {

            Instant t1 = Instant.now();
            long dtMillis = t0.until(t1, ChronoUnit.MILLIS);
            double dtSeconds = dtMillis / 1_000.0;

            String dataValuesSubmittedString = String.format("%,8d", dataValuesSubmitted);
            String dataBytesSubmittedString = String.format("%,8d", dataBytesSubmitted);
            String grpcBytesSubmittedString = String.format("%,8d", grpcBytesSubmitted);
            String grpcOverheadBytesString = String.format("%,8d", grpcBytesSubmitted - dataBytesSubmitted);
            logger.info("data values submitted: {}", dataValuesSubmittedString);
            logger.info("data bytes submitted: {}", dataBytesSubmittedString);
            logger.info("grpc bytes submitted: {}", grpcBytesSubmittedString);
            logger.info("grpc overhead bytes: {}", grpcOverheadBytesString);

            double dataValueRate = dataValuesSubmitted / dtSeconds;
            double dataMByteRate = (dataBytesSubmitted / 1_000_000.0) / dtSeconds;
            double grpcMByteRate = (grpcBytesSubmitted / 1_000_000.0) / dtSeconds;
            DecimalFormat formatter = new DecimalFormat("#,###.00");
            String dtSecondsString = formatter.format(dtSeconds);
            String dataValueRateString = formatter.format(dataValueRate);
            String dataMbyteRateString = formatter.format(dataMByteRate);
            String grpcMbyteRateString = formatter.format(grpcMByteRate);
            logger.info("execution time: {} seconds", dtSecondsString);
            logger.info("data value rate: {} values/sec", dataValueRateString);
            logger.info("data byte rate: {} MB/sec", dataMbyteRateString);
            logger.info("grpc byte rate: {} MB/sec", grpcMbyteRateString);

        } else {
            logger.error("streaming ingestion scenario failed, performance data invalid");
        }

    }

    public static void main(String[] args) throws Exception {

        // Access a service running on the local machine on port 50051
        String target = "localhost:50051";

        // Create a communication channel to the server, known as a Channel. Channels are thread-safe
        // and reusable. It is common to create channels at the beginning of your application and reuse
        // them until the application shuts down.
        //
        // For the example we use plaintext insecure credentials to avoid needing TLS certificates. To
        // use TLS, use TlsChannelCredentials instead.
        ManagedChannel channel = Grpc.newChannelBuilder(target, InsecureChannelCredentials.create())
                .build();

        // use plaintext channel
//        ManagedChannelBuilder<?> channelBuilder = ManagedChannelBuilder.forTarget(target);
//        channelBuilder.usePlaintext();
//        ManagedChannel channel = channelBuilder.build();
        try {
            BenchmarkClient client = new BenchmarkClient(channel);
//            client.sendUnarySpamRequest();
//            client.sendStreamingSpamRequest();
//            client.sendUnaryIngestionRequest();
            client.multithreadedStreamingIngestionScenario();
        } finally {
            // ManagedChannels use resources like threads and TCP connections. To prevent leaking these
            // resources the channel should be shut down when it will no longer be used. If it may be used
            // again leave it running.
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
