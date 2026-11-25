# Photobooth Offline (Android)

App Android thuần Java, không có quyền Internet, không dùng máy chủ hay thư viện xử lý ảnh bên ngoài.

## Chức năng

- Chọn chế độ 1, 2, 4 hoặc 8 ảnh, rồi chọn đúng số ảnh đó từ bộ nhớ điện thoại. Bố cục lần lượt là 1 ô, 2 ô xếp dọc, 2 × 2 và 2 × 4.
- Mỗi chế độ tự chọn khung Graduation · UIT · HaMinhTruong đen vàng vintage có sẵn trong APK. Có thể thay bằng khung PNG trong suốt của bạn rồi trở lại khung có sẵn bằng nút riêng.
- Sau khi chọn ảnh và khung, có thể chọn người nhận trong danh sách 33 bạn. Bản xem trước và PNG xuất ra sẽ có một dòng chữ vàng kiểu serif nghiêng, không có hộp bao quanh, dưới “HaMinhTruong”: “tri ân [tên người nhận] đã đến dự lễ tốt nghiệp của mình”. Để mục “Chọn người nhận” nếu muốn xuất ảnh không có lời tri ân.
- Chạm một ô trong bản xem trước để chỉnh riêng ảnh đó: kéo để dịch, chụm hai ngón hoặc dùng thanh trượt để zoom 25–400%, dùng thanh trượt để xoay -180° đến 180°. Ở mức 100%, ảnh được căn vừa để phủ kín ô ảnh của khung.
- Bản xem trước hiển thị khung chồng lên ảnh và phần ảnh bị cắt. App nới canvas để ảnh xuất có tỉ lệ cố định **10 × 14,8 cm (25:37)**, bất kể tỉ lệ của ảnh hoặc khung đầu vào. Thanh trượt chỉnh độ rộng khung 70–130% trên canvas này.
- Áp khung PNG lên trên ảnh ghép. Khung nhỏ hơn sẽ được phóng lên và có thể mềm nét hơn. Ảnh đầu vào được đọc ở độ phân giải gốc khi xuất bản nguyên kích thước; thao tác căn vừa, zoom và xoay có thể nội suy hoặc cắt ảnh.
- Có ba nút xuất PNG. Bản **nguyên kích thước** giữ độ phân giải canvas theo ảnh nguồn để lưu trữ. Bản **in nhanh** tạo PNG 1200 × 1776 px (khoảng 305 DPI ở 10 × 14,8 cm), giảm thời gian ghép và nén đáng kể. Bản **nét hơn khi phóng to** tạo PNG 2400 × 3552 px, cho phép xem trên màn hình ở mức phóng khoảng gấp đôi trước khi thấy pixel, nhưng tốn thêm thời gian và dung lượng. Hai bản cỡ in thu toàn bộ ảnh và khung còn 99% kích thước, đặt giữa giấy trắng: mép khoảng 0,5 mm mỗi bên và 0,75 mm trên dưới. Kích thước PNG vẫn giữ nguyên để ứng dụng in nhận đúng tỉ lệ giấy; giảm riêng số pixel sẽ không tạo mép in. Cả ba có metadata `pHYs` cho khổ in 10 × 14,8 cm; PNG không nén mất dữ liệu ở độ phân giải đã chọn. Hai bản cỡ in lấy mẫu lại ảnh nguồn; bản nguyên kích thước dành cho lưu trữ tối đa chi tiết. Độ nét của chữ và hoa văn trên khung vẫn bị giới hạn bởi độ phân giải PNG khung đầu vào. Khi in, chọn đúng khổ giấy và tắt tùy chọn tự cắt/phóng của ứng dụng in nếu có; một số ứng dụng in có thể bỏ qua metadata PNG.
- Xoay ảnh theo EXIF trước khi ghép.

PNG lossless không làm mất chất lượng thêm khi lưu ảnh ghép, nhưng kích thước tệp không nhất thiết bằng tổng kích thước các JPG gốc. Bốn ảnh 5 MB không thể được cam kết tạo ra đúng một ảnh 20 MB; ảnh JPEG đã được nén mất dữ liệu, còn ảnh ghép PNG giữ dữ liệu pixel và có thể lớn hơn nhiều lần. Bộ ghi PNG thử năm bộ lọc dự đoán chuẩn trên từng dòng rồi chọn kết quả dễ nén nhất, không thay đổi pixel khi giải mã. App không giữ metadata và byte của từng tệp gốc trong PNG ghép. Nếu cần giữ nguyên byte của ảnh gốc, phải lưu thêm các tệp gốc riêng (ví dụ ZIP).

## Mở và chạy

Mở thư mục này bằng Android Studio có Android SDK 35 và JDK 17. Gradle wrapper 8.14.3 đã được kèm theo; sync Gradle rồi chạy module `app` trên Android 8.0+ (API 26+). Máy chỉ cần Internet **lúc tải công cụ build lần đầu**; app đã cài chạy hoàn toàn offline.

GitHub Actions đã có thể tạo APK mà không cần cài Android Studio trên máy cá nhân. Ảnh ghép được xử lý theo từng dải và ghi PNG từng dòng, không tạo bitmap toàn bộ ảnh ghép trong RAM và không đặt ngưỡng 60 triệu pixel. Thiết bị vẫn có các giới hạn thực tế về chiều ảnh mà bộ giải mã Android hỗ trợ, bộ nhớ cho một dải ảnh, bộ nhớ trống và dung lượng lưu trữ; app báo lỗi thay vì âm thầm giảm độ phân giải.

## Khung có sẵn

Bốn khung đen vàng vintage độ phân giải 1024 × 1536 px, có cửa sổ ảnh trong suốt thật, nằm trong APK và tự đổi theo chế độ. Chúng được phát triển từ [ảnh mẫu ban đầu](frames/graduation-uit-haminhtruong-vintage-black.png); phần chân khung đã tách hai nhánh lá để chừa chỗ cho dòng tri ân, hạ ngôi sao và hai gạch xuống dưới, thêm “TP Hồ Chí Minh” bên trái và “26.11.2026” bên phải. Từng PNG cũng có trong thư mục `frames/` để dùng riêng.

## Tạo APK bằng GitHub Actions (không cài Android Studio)

1. Tạo repository GitHub và đưa **nội dung thư mục `photobooth-android`** lên ngay gốc repository, gồm cả thư mục `.github` và tệp `gradlew`.
2. Mở tab **Actions** → **Build Android APK** → **Run workflow**. Workflow cũng tự chạy khi push lên nhánh `main`.
3. Khi job hoàn tất, tải artifact `photobooth-offline-debug-apk`. Giải nén để lấy `app-debug.apk` và cài trên điện thoại Android 8.0 trở lên.

APK này được ký bằng khóa debug, phù hợp để dùng thử hoặc cài riêng. Nếu phát hành công khai, cần cấu hình khóa ký release riêng.
