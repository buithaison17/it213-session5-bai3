package com.example.bai3;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RoomCheckResponse(
        boolean isSuccess,
        boolean isAvailable,
        Double pricePerNight,
        String message
) {
    /**
     * Factory method tạo response thành công
     */
    public static RoomCheckResponse success(boolean isAvailable, Double pricePerNight, String message) {
        return new RoomCheckResponse(true, isAvailable, pricePerNight, message);
    }

    /**
     * Factory method tạo response thất bại do lỗi validation hoặc logic
     */
    public static RoomCheckResponse failure(String errorMessage) {
        return new RoomCheckResponse(false, false, null, errorMessage);
    }
}