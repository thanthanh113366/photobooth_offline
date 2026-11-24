# Photobooth Offline (Android)

App Android thuần Java, không có quyền Internet, không dùng máy chủ hay thư viện xử lý ảnh bên ngoài.

## Chức năng

- Chọn đúng 4 ảnh từ bộ nhớ điện thoại bằng trình chọn tệp Android.
- Chọn một khung PNG trong suốt (không bắt buộc).
- Ghép 4 ảnh thành lưới 2 × 2, giữ nguyên kích thước pixel và tỉ lệ của từng ảnh; vùng dư có nền trắng.
- Áp khung PNG lên trên ảnh ghép. Khung có cùng tỉ lệ ngang/dọc sẽ được đặt vừa ảnh ghép; **bốn ảnh gốc không bị thu nhỏ**. Khung có đúng kích thước pixel được đọc theo từng dải, còn khung nhỏ hơn sẽ được phóng lên và có thể mềm nét hơn.
- Lưu ảnh ghép PNG lossless vào vị trí người dùng chọn. Không giảm kích thước hoặc xuất JPEG.
- Xoay ảnh theo EXIF trước khi ghép.

PNG lossless không làm mất chất lượng thêm khi lưu ảnh ghép, nhưng kích thước tệp không nhất thiết bằng tổng kích thước các JPG gốc. Bốn ảnh 5 MB không thể được cam kết tạo ra đúng một ảnh 20 MB; kích thước phụ thuộc dữ liệu pixel và thuật toán nén PNG. App giữ nguyên độ phân giải ảnh đầu vào, còn metadata và byte của từng tệp gốc không nằm trong PNG ghép. Nếu cần giữ nguyên byte của ảnh gốc, phải lưu thêm các tệp gốc riêng (ví dụ ZIP).

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
