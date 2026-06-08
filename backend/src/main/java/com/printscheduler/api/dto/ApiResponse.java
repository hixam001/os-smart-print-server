package com.printscheduler.api.dto;

public class ApiResponse<T> {

    private final boolean success;
    private final long    timestamp;
    private final T       data;
    private final ErrorDetail error;

    private ApiResponse(boolean success, T data, ErrorDetail error) {
        this.success   = success;
        this.timestamp = System.currentTimeMillis();
        this.data      = data;
        this.error     = error;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> error(ErrorDetail error) {
        return new ApiResponse<>(false, null, error);
    }

    public boolean     isSuccess()   { return success; }
    public long        getTimestamp(){ return timestamp; }
    public T           getData()     { return data; }
    public ErrorDetail getError()    { return error; }
}
