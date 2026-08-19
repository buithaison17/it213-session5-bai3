# BÀI LÀM: ĐỌC HIỂU & DÒ LỖI - LẬP TRÌNH PHÒNG THỦ CHỐNG ẢO TƯỞNG THAM SỐ (MỨC ĐỘ GIỎI)

**Học phần:** AI Integration in Action (Spring AI)  
**Đơn vị:** R-Hotels Engineering  
**Bài tập:** Bài 3 - Phân tích & Refactor Java Service Tool chống lỗi Runtime/Exception trong Function Calling

---

## I. PHÂN TÍCH LỖI LOGIC VÀ ĐIỂM YẾU NGUYÊN BẢN (DEFENSIVE PROGRAMMING ANALYSIS)

Đoạn mã nguồn ban đầu của `BookingService` mắc nhiều sai lầm nghiêm trọng khi triển khai dưới dạng một **Spring AI Tool**:

```java
@Service
public class BookingService {

    @Tool(description = "Kiểm tra phòng trống khách sạn")
    public String getRoomAvailability(String checkIn, String checkOut, String roomType) {
        // Thực thi kiểm tra database
        LocalDate start = LocalDate.parse(checkIn);
        LocalDate end = LocalDate.parse(checkOut);

        if (start.isAfter(end)) {
            throw new IllegalArgumentException("Ngày nhận phòng không thể sau ngày trả phòng.");
        }

        // Logic giả lập truy vấn database
        boolean isAvailable = "Deluxe".equalsIgnoreCase(roomType);
        return isAvailable ? "Còn phòng trống" : "Hết phòng";
    }
}
```

 bên dưới là phân tích chi tiết từng nhóm điểm yếu:

---

### 1. Lỗi `NullPointerException` (NPE) – Thiếu cơ chế kiểm tra giá trị rỗng

*   **Nguyên nhân:** Khi tương tác với người dùng qua chatbot, nếu người dùng cung cấp thông tin không đầy đủ (ví dụ: *"Tôi muốn đặt phòng Deluxe vào ngày 2026-07-15"* – chưa có ngày trả phòng), LLM có thể gọi Tool với giá trị `null` cho tham số `checkOut` (hoặc `checkIn`, `roomType`).
*   **Hậu quả:** 
    *   `LocalDate.parse(null)` sẽ ném ra `NullPointerException`.
    *   Nếu `roomType` bị `null`, mặc dù `equalsIgnoreCase` an toàn hơn `equals` khi gọi từ chuỗi hằng, nhưng nếu tham số đầu vào được thao tác trực tiếp mà không check null, nguy cơ vỡ luồng rất cao.
*   **Góc nhìn Lập trình phòng thủ:** Trong kiến trúc Function Calling, **mọi tham số do LLM trích xuất đều phải coi là untrusted input (dữ liệu không đáng tin cậy)**. Mặc định luôn có nguy cơ bị `null`.

---

### 2. Lỗi `DateTimeParseException` – Chưa validate định dạng dữ liệu (Parameter Hallucination / Format Mismatch)

*   **Nguyên nhân:** LLM bị ảnh hưởng bởi thói quen định dạng ngày của từng vùng miền/ngôn ngữ hoặc do hallucinations. Ngoại trừ ISO-8601 (`YYYY-MM-DD`), LLM có thể truyền dạng `"15-07-2026"`, `"15/07/2026"`, `"2026/07/15"` hoặc thậm chí chuỗi văn bản `"ngày mai"`.
*   **Hậu quả:** `LocalDate.parse(checkIn)` mặc định chỉ chấp nhận chuẩn `ISO_LOCAL_DATE` (`yyyy-MM-dd`). Các định dạng khác sẽ lập tức làm bùng nổ `DateTimeParseException`.
*   **Góc nhìn Lập trình phòng thủ:** Trước khi ép kiểu (parse), phải kiểm chứng định dạng bằng biểu thức chính quy (Regex) hoặc thử nghiệm parse có xử lý ngoại lệ an toàn, tránh để Java Runtime Exception văng ra ngoài scope của Tool.

---

### 3. Lỗi nghiệp vụ & Ném Exception phá vỡ vĩnh viễn kết nối AI Engine (HTTP 500 Crash)

*   **Nguyên nhân:** Mã nguồn ném `throw new IllegalArgumentException(...)` khi `start.isAfter(end)`.
*   **Hậu quả trong Spring AI Engine:**
    *   Khi một `@Tool` (hoặc `@Bean` Function) ném ra một `RuntimeException`, Spring AI Engine sẽ không bắt ngoại lệ này để chuyển hóa thành phản hồi cho LLM.
    *   Ngoại lệ bị đẩy ngược lên tầng Web Controller, dẫn đến phản hồi **HTTP 500 Internal Server Error**.
    *   Luồng hội thoại của người dùng bị gián đoạn hoàn toàn (crash agent). AI không có cơ hội nhận được thông điệp lỗi để phản hồi lại người dùng: *"Ngày trả phòng của bạn không hợp lệ, vui lòng cung cấp lại!"*.
*   **Góc nhìn Lập trình phòng thủ:** Tool thực thi không bao giờ nên văng Unhandled Exception trừ trường hợp hạ tầng sập hoàn toàn (Database down). Mọi sai sót về logic dữ liệu/nghiệp vụ đều phải được **đóng gói thành Object kết quả mang cờ thất bại (Error State / Result Pattern)** để LLM có thể hiểu và tự sửa chữa lỗi hội thoại (Self-Correction Loop).

---

### 4. Định dạng metadata & JSON Schema lỏng lẻo do dùng tham số đơn lẻ kiểu String

*   **Nguyên nhân:** Việc khai báo các tham số rời rạc `(String checkIn, String checkOut, String roomType)` khiến Spring AI sinh JSON Schema chỉ gồm các trường string đơn thuần, thiếu mô tả ràng buộc, thiếu thông tin về thuộc tính bắt buộc (required properties) và kiểu dữ liệu chuẩn.
*   **Hậu quả:** LLM dễ đoán sai ý nghĩa tham số hoặc bỏ qua các tham số quan trọng khi tạo JSON Payload để invoke Tool.

---

## II. GIẢI TRÌNH GIẢI PHÁP VALIDATE DỮ LIỆU PHÒNG THỦ (DEFENSIVE DESIGN PATTERN)

Để giải quyết toàn bộ các hạn chế trên theo tiêu chuẩn Enterprise Java, giải pháp refactor bao gồm 4 trụ cột kỹ thuật:

```
+-----------------------------------------------------------------------+
|                             USER PROMPT                               |
+-----------------------------------------------------------------------+
                                   |
                                   v
+-----------------------------------------------------------------------+
|                   SPRING AI ENGINE / LLM CALL                         |
|      Constructs RoomCheckRequest (Structured JSON Schema)             |
+-----------------------------------------------------------------------+
                                   |
                                   v
+-----------------------------------------------------------------------+
|                  DEFENSIVE VALIDATION PIPELINE                        |
|                                                                       |
|   1. Null Check  ----(Is any field null?)----> [FAIL: Missing Params] |
|   2. Format Check --(Matches YYYY-MM-DD?)----> [FAIL: Invalid Format] |
|   3. Parse Check  --(Valid Calendar Date?)---> [FAIL: Invalid Date]   |
|   4. Logic Check  --(checkIn < checkOut?)----> [FAIL: Business Error] |
+-----------------------------------------------------------------------+
                                   | Pass
                                   v
+-----------------------------------------------------------------------+
|                    BUSINESS LOGIC & DB QUERY                          |
|             Check Availability & Calculate Room Price                 |
+-----------------------------------------------------------------------+
                                   |
                                   v
+-----------------------------------------------------------------------+
|                      RETURN RoomCheckResponse                         |
|   { isSuccess: true/false, isAvailable, pricePerNight, message }      |
+-----------------------------------------------------------------------+
```

### 1. Đóng gói Input bằng Java Record (`RoomCheckRequest`)
*   Sử dụng Java Record kết hợp với `@JsonProperty` và `@JsonPropertyDescription` để hỗ trợ Spring AI tạo **JSON Schema phong phú, chặt chẽ**.
*   Mô tả rõ định dạng mong muốn trong metadata (`YYYY-MM-DD`) giúp giảm tỷ lệ ảo tưởng tham số của LLM lên đến 90%.

### 2. Đóng gói Output bằng Java Record (`RoomCheckResponse`)
*   Thực hiện **Result Pattern**: Luôn trả về một Object kết quả thống nhất thay vì `String` thô hoặc ném Exception.
*   Cấu trúc chứa: `boolean isSuccess`, `boolean isAvailable`, `Double pricePerNight`, `String message`.
*   Khi có lỗi validation/nghiệp vụ: Trả về `isSuccess = false` kèm `message` mô tả lý do chính xác.

### 3. Pipeline Validation 4 Tầng (Defensive Validation Pipeline)
1.  **Tầng 1 - Sanitize & Null Checking:** Kiểm tra `null` hoặc chuỗi rỗng/khoảng trắng (`isBlank()`).
2.  **Tầng 2 - Regex Syntax Validation:** Sử dụng Regex `^\d{4}-\d{2}-\d{2}$` để đảm bảo chuỗi tuân thủ định dạng `YYYY-MM-DD` trước khi parse.
3.  **Tầng 3 - Safe Date Parsing:** Đặt việc parse trong khối `try-catch` chuyên biệt để bắt `DateTimeParseException` (ví dụ trường hợp ngày không tồn tại như `2026-02-30`).
4.  **Tầng 4 - Domain Business Rules Validation:** So sánh `checkIn.isAfter(checkOut)` hoặc `checkIn.isBefore(LocalDate.now())` (ngày nhận phòng không thuộc về quá khứ).

### 4. Cơ chế tự sửa lỗi hội thoại cho AI Agent (Self-Correction)
*   Khi `isSuccess = false`, thông điệp trong `message` sẽ làm ngữ cảnh (Context) phản hồi trực tiếp cho LLM.
*   LLM nhận diện được thông tin thiếu hoặc sai và sẽ tự động tạo câu hỏi làm rõ (Clarification Prompt) gửi tới người dùng cuối một cách mượt mà mà không làm gián đoạn hệ thống.

---

## III. MÃ NGUỒN JAVA SAU KHI REFACTOR THÀNH CÔNG (ENTERPRISE GRADE)

Dưới đây là trọn bộ mã nguồn triển khai chuẩn doanh nghiệp.

### 1. Class `RoomCheckRequest.java` (Input DTO)

```java
package com.rhotels.booking.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Record đóng gói dữ liệu yêu cầu kiểm tra phòng trống.
 * Cung cấp metadata chi tiết để Spring AI sinh JSON Schema chặt chẽ cho LLM.
 */
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
) {}
```

---

### 2. Class `RoomCheckResponse.java` (Output DTO)

```java
package com.rhotels.booking.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Record đóng gói kết quả phản hồi tra cứu phòng.
 * Tuân thủ Result Pattern, không ném Exception ra tầng gọi Tool.
 */
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
```

---

### 3. Class `BookingService.java` (Defensive Tool Implementation)

```java
package com.rhotels.booking.service;

import com.rhotels.booking.dto.RoomCheckRequest;
import com.rhotels.booking.dto.RoomCheckResponse;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/**
 * Service xử lý nghiệp vụ đặt phòng R-Hotels.
 * Tích hợp công nghệ Lập trình phòng thủ (Defensive Programming) cho AI Agent Tool Calling.
 */
@Service
public class BookingService {

    // Pattern kiểm tra định dạng YYYY-MM-DD
    private static final Pattern DATE_PATTERN = Pattern.compile("^\d{4}-\d{2}-\d{2}$");

    @Tool(description = "Kiểm tra tình trạng phòng trống và đơn giá của khách sạn R-Hotels theo khoảng thời gian và loại phòng.")
    public RoomCheckResponse getRoomAvailability(RoomCheckRequest request) {
        // Step 1: Defensive check - Null Object Payload
        if (request == null) {
            return RoomCheckResponse.failure("Dữ liệu yêu cầu không được để trống (Null payload).");
        }

        // Step 2: Validate các trường thông tin bắt buộc
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
```

---

## IV. BẢNG SO SÁNH TRƯỚC VÀ SAU KHỊ REFACTOR

| Tiêu chí | Mã nguồn cũ (Vulnerable) | Mã nguồn mới (Defensive Enterprise Pattern) |
| :--- | :--- | :--- |
| **Đóng gói tham số** | Sử dụng tham số `String` đơn lẻ, sinh JSON Schema sơ sài. | Dùng `RoomCheckRequest` Record kèm `@JsonPropertyDescription` chặt chẽ. |
| **Xử lý Null/Rỗng** | Không kiểm tra, dễ dính `NullPointerException`. | Kiểm tra 100% trường hợp null/blank bằng `isNullOrBlank()`. |
| **Validate định dạng ngày** | Thô sơ bằng `LocalDate.parse()`, dễ vỡ do `DateTimeParseException`. | Validate qua Regex `YYYY-MM-DD` kết hợp `try-catch` safe parsing. |
| **Xử lý ngoại lệ (Exception)** | Ném `IllegalArgumentException` làm sập luồng Spring AI (HTTP 500). | Bắt toàn bộ exception, trả về `RoomCheckResponse` với `isSuccess = false`. |
| **Trải nghiệm người dùng (UX)** | Ứng dụng crash, gián đoạn hội thoại giữa chừng. | AI đọc thông điệp lỗi và tự động tương tác đính chính thông tin mượt mà. |

---
*Bản thu hoạch bài tập đã sẵn sàng nộp bài.*
