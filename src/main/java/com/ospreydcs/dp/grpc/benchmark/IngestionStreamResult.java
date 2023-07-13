package com.ospreydcs.dp.grpc.benchmark;

public class IngestionStreamResult {

    private boolean status;
    private long dataValuesSubmitted = 0;
    private long dataBytesSubmitted = 0;
    private long grpcBytesSubmitted = 0;


    public boolean getStatus() {
        return status;
    }

    public void setStatus(boolean status) {
        this.status = status;
    }

    public long getDataValuesSubmitted() {
        return dataValuesSubmitted;
    }

    public void setDataValuesSubmitted(long dataValuesSubmitted) {
        this.dataValuesSubmitted = dataValuesSubmitted;
    }

    public long getDataBytesSubmitted() {
        return dataBytesSubmitted;
    }

    public void setDataBytesSubmitted(long dataBytesSubmitted) {
        this.dataBytesSubmitted = dataBytesSubmitted;
    }

    public long getGrpcBytesSubmitted() {
        return grpcBytesSubmitted;
    }

    public void setGrpcBytesSubmitted(long grpcBytesSubmitted) {
        this.grpcBytesSubmitted = grpcBytesSubmitted;
    }
}
