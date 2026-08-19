package com.example.bai3;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

@Service
public class BookingService {
    // Pattern kiểm tra định dạng YYYY-MM-DD
    private static final Pattern DATE_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    @Tool(description = "Kiểm tra tình trạng phòng trống và đơn giá của khách sạn R-Hotels theo khoảng thời gian và loại phòng.")
    public RoomCheckResponse getRoomAvailability(RoomCheckRequest request) {
        // Step 1: Defensive check - Null Object Payload
        if (request == null) {
            return RoomCheckResponse.failure("Dữ liệu yêu cầu không được để trống (Null payload).");
        }

        // Step 2: Validate các trường thông tin bắt buộc (Null hoặc Blank)
        if (isNullOrBlank(request.checkIn())) {
            return RoomCheckResponse.failure("Thiếu thông tin ngày nhận phòng (checkIn). Vui lòng hỏi lại khách hàng.");
        }
        if (isNullOrBlank(request.checkOut())) {
            return RoomCheckResponse.failure("Thiếu thông tin ngày trả phòng (checkOut). Vui lòng hỏi lại khách hàng.");
        }
        if (isNullOrBlank(request.roomType())) {
            return RoomCheckResponse.failure("Thiếu thông tin loại phòng (roomType). Vui lòng hỏi lại khách hàng.");
        }

        String checkInStr = request.checkIn().trim();
        String checkOutStr = request.checkOut().trim();
        String roomType = request.roomType().trim();

        // Step 3: Validate định dạng chuỗi ngày bằng Regex
        if (!DATE_PATTERN.matcher(checkInStr).matches()) {
            return RoomCheckResponse.failure("Ngày nhận phòng '" + checkInStr + "' không đúng định dạng chuẩn YYYY-MM-DD (Ví dụ đúng: 2026-07-15).");
        }
        if (!DATE_PATTERN.matcher(checkOutStr).matches()) {
            return RoomCheckResponse.failure("Ngày trả phòng '" + checkOutStr + "' không đúng định dạng chuẩn YYYY-MM-DD (Ví dụ đúng: 2026-07-18).");
        }

        // Step 4: Parse ngày an toàn và validate giá trị lịch thực tế
        LocalDate checkInDate;
        LocalDate checkOutDate;
        try {
            checkInDate = LocalDate.parse(checkInStr);
            checkOutDate = LocalDate.parse(checkOutStr);
        } catch (DateTimeParseException e) {
            return RoomCheckResponse.failure("Ngày được cung cấp không tồn tại trên thực tế. Chi tiết: " + e.getMessage());
        }

        // Step 5: Validate logic nghiệp vụ (Business Rules)
        if (checkInDate.isAfter(checkOutDate) || checkInDate.isEqual(checkOutDate)) {
            return RoomCheckResponse.failure("Ngày nhận phòng (" + checkInStr + ") phải diễn ra trước ngày trả phòng (" + checkOutStr + ").");
        }

        // Step 6: Thực thi truy vấn nghiệp vụ/database (Giả lập)
        return executeRoomSearchLogic(checkInDate, checkOutDate, roomType);
    }

    /**
     * Logic nghiệp vụ tra cứu database giả lập
     */
    private RoomCheckResponse executeRoomSearchLogic(LocalDate checkIn, LocalDate checkOut, String roomType) {
        if ("Deluxe".equalsIgnoreCase(roomType)) {
            return RoomCheckResponse.success(
                    true,
                    1500000.0,
                    "Phòng Deluxe còn trống từ ngày " + checkIn + " đến ngày " + checkOut + " với giá 1,500,000 VNĐ/đêm."
            );
        } else if ("Suite".equalsIgnoreCase(roomType)) {
            return RoomCheckResponse.success(
                    true,
                    2800000.0,
                    "Phòng Suite còn trống từ ngày " + checkIn + " đến ngày " + checkOut + " với giá 2,800,000 VNĐ/đêm."
            );
        } else if ("Standard".equalsIgnoreCase(roomType)) {
            return RoomCheckResponse.success(
                    false,
                    0.0,
                    "Loại phòng Standard đã hết phòng trong khoảng thời gian từ " + checkIn + " đến " + checkOut + "."
            );
        } else {
            return RoomCheckResponse.failure("Loại phòng '" + roomType + "' không tồn tại trong hệ thống R-Hotels. Các loại phòng hiện có: Standard, Deluxe, Suite.");
        }
    }

    /**
     * Utility check rỗng
     */
    private boolean isNullOrBlank(String str) {
        return str == null || str.isBlank();
    }
}
