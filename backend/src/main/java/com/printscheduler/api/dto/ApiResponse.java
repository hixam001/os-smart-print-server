package com.printscheduler.api.dto;

/**
 * Generic API envelope for every REST response.
 *
 * <pre>
 * {
 *   "success": true,
 *   "timestamp": 1234567890,
 *   "data": { ... },
 *   "error": null
 * }
 * </pre>
 *
 * @param <T> payload type
 */
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

    // ── Factory methods ───────────────────────────────────────────────────

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> error(ErrorDetail error) {
        return new ApiResponse<>(false, null, error);
    }

    // ── Getters ───────────────────────────────────────────────────────────
    public boolean     isSuccess()   { return success; }
    public long        getTimestamp(){ return timestamp; }
    public T           getData()     { return data; }
    public ErrorDetail getError()    { return error; }
}
