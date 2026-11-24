# Photobooth Offline (Android)

App Android thuần Java, không có quyền Internet, không dùng máy chủ hay thư viện xử lý ảnh bên ngoài.

## Chức năng

- Chọn đúng 4 ảnh từ bộ nhớ điện thoại bằng trình chọn tệp Android.
- Chọn một khung PNG trong suốt (không bắt buộc).
- Ghép 4 ảnh thành lưới 2 × 2. Chạm một ô trong bản xem trước để chỉnh riêng ảnh đó: kéo để dịch, chụm hai ngón hoặc dùng thanh trượt để zoom 25–400%, dùng thanh trượt để xoay -180° đến 180°.
- Bản xem trước hiển thị khung chồng lên ảnh và phần ảnh bị cắt. App nới canvas để ảnh xuất có tỉ lệ cố định **10 × 14,8 cm (25:37)**, bất kể tỉ lệ của ảnh hoặc khung đầu vào. Thanh trượt chỉnh độ rộng khung 70–130% trên canvas này.
- Áp khung PNG lên trên ảnh ghép. Khung nhỏ hơn sẽ được phóng lên và có thể mềm nét hơn. Bốn ảnh đầu vào không bị giảm kích thước khi đọc; thao tác zoom/rotate do người dùng chọn có thể nội suy hoặc cắt ảnh khi xuất.
- Có hai nút xuất PNG. Bản **nguyên kích thước** giữ độ phân giải canvas theo ảnh nguồn để lưu trữ. Bản **in nhanh** tạo PNG 1200 × 1776 px (khoảng 305 DPI ở 10 × 14,8 cm), giảm thời gian ghép và nén đáng kể. Cả hai có metadata `pHYs` cho khổ in 10 × 14,8 cm; PNG không nén mất dữ liệu ở độ phân giải đã chọn. Bản in nhanh lấy mẫu lại ảnh nguồn để đúng độ phân giải in, nên dùng bản nguyên kích thước nếu cần phóng lớn hoặc lưu trữ tối đa chi tiết. Khi in, chọn đúng khổ giấy và tắt tùy chọn tự cắt/phóng của ứng dụng in nếu có; một số ứng dụng in có thể bỏ qua metadata PNG.
- Xoay ảnh theo EXIF trước khi ghép.

PNG lossless không làm mất chất lượng thêm khi lưu ảnh ghép, nhưng kích thước tệp không nhất thiết bằng tổng kích thước các JPG gốc. Bốn ảnh 5 MB không thể được cam kết tạo ra đúng một ảnh 20 MB; ảnh JPEG đã được nén mất dữ liệu, còn ảnh ghép PNG giữ dữ liệu pixel và có thể lớn hơn nhiều lần. Bộ ghi PNG thử năm bộ lọc dự đoán chuẩn trên từng dòng rồi chọn kết quả dễ nén nhất, không thay đổi pixel khi giải mã. App giữ nguyên độ phân giải ảnh đầu vào, còn metadata và byte của từng tệp gốc không nằm trong PNG ghép. Nếu cần giữ nguyên byte của ảnh gốc, phải lưu thêm các tệp gốc riêng (ví dụ ZIP).

## Mở và chạy

Mở thư mục này bằng Android Studio có Android SDK 35 và JDK 17. Gradle wrapper 8.14.3 đã được kèm theo; sync Gradle rồi chạy module `app` trên Android 8.0+ (API 26+). Máy chỉ cần Internet **lúc tải công cụ build lần đầu**; app đã cài chạy hoàn toàn offline.

GitHub Actions đã có thể tạo APK mà không cần cài Android Studio trên máy cá nhân. Ảnh ghép được xử lý theo từng dải và ghi PNG từng dòng, không tạo bitmap toàn bộ ảnh ghép trong RAM và không đặt ngưỡng 60 triệu pixel. Thiết bị vẫn có các giới hạn thực tế về chiều ảnh mà bộ giải mã Android hỗ trợ, bộ nhớ cho một dải ảnh, bộ nhớ trống và dung lượng lưu trữ; app báo lỗi thay vì âm thầm giảm độ phân giải.

## Khung mẫu

[Graduation · UIT · HaMinhTruong — đen vintage](frames/graduation-uit-haminhtruong-vintage-black.png) là khung PNG 1024 × 1536 px, gồm bốn ô trong suốt để đặt ảnh. Có thể tải tệp PNG này vào app ở bước chọn khung. Khung dùng đẹp nhất khi bốn ảnh đầu vào có cùng tỉ lệ dọc 2:3.

## Tạo APK bằng GitHub Actions (không cài Android Studio)

1. Tạo repository GitHub và đưa **nội dung thư mục `photobooth-android`** lên ngay gốc repository, gồm cả thư mục `.github` và tệp `gradlew`.
2. Mở tab **Actions** → **Build Android APK** → **Run workflow**. Workflow cũng tự chạy khi push lên nhánh `main`.
3. Khi job hoàn tất, tải artifact `photobooth-offline-debug-apk`. Giải nén để lấy `app-debug.apk` và cài trên điện thoại Android 8.0 trở lên.

APK này được ký bằng khóa debug, phù hợp để dùng thử hoặc cài riêng. Nếu phát hành công khai, cần cấu hình khóa ký release riêng.
