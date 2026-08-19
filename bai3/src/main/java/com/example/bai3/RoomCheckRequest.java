package com.example.bai3;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record RoomCheckRequest(
        @JsonProperty(required = true)
        @JsonPropertyDescription("Ngày nhận phòng, định dạng bắt buộc ISO yyyy-MM-dd (Ví dụ: '2026-07-15')")
        String checkIn,

        @JsonProperty(required = true)
        @JsonPropertyDescription("Ngày trả phòng, định dạng bắt buộc ISO yyyy-MM-dd (Ví dụ: '2026-07-18')")
        String checkOut,

        @JsonProperty(required = true)
        @JsonPropertyDescription("Loại phòng khách hàng muốn đặt (Ví dụ: 'Standard', 'Deluxe', 'Suite')")
        String roomType
) {
}