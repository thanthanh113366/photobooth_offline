package vn.hmtruong.photobooth;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int PICK_PHOTOS = 1;
    private static final int PICK_FRAME = 2;
    private static final int SAVE_PNG = 3;
    private static final long MAX_PIXELS = 60_000_000L;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<Uri> photos = new ArrayList<>();
    private Uri frame;
    private TextView status;
    private TextView dimensions;
    private TextView frameLabel;
    private Button saveButton;
    private final ImageView[] previews = new ImageView[4];
    private volatile boolean busy;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        int pad = dp(20);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(pad, pad, pad, pad);
        body.setBackgroundColor(Color.rgb(246, 249, 255));

        TextView title = label("PHOTOBOOTH", 30, Color.rgb(16, 55, 119));
        title.setTypeface(null, 1);
        body.addView(title);
        body.addView(label("Ghép 4 ảnh tại máy • Xuất PNG nguyên độ phân giải", 15, Color.DKGRAY));

        Button choosePhotos = button("1. Chọn 4 ảnh");
        choosePhotos.setOnClickListener(v -> pickPhotos());
        body.addView(choosePhotos);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        grid.setRowCount(2);
        LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(-1, dp(360));
        gridParams.topMargin = dp(12);
        body.addView(grid, gridParams);
        for (int i = 0; i < 4; i++) {
            ImageView image = new ImageView(this);
            image.setBackgroundColor(Color.rgb(226, 234, 248));
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            GridLayout.LayoutParams p = new GridLayout.LayoutParams();
            p.width = 0;
            p.height = dp(180);
            p.columnSpec = GridLayout.spec(i % 2, 1f);
            p.rowSpec = GridLayout.spec(i / 2);
            p.setMargins(dp(3), dp(3), dp(3), dp(3));
            grid.addView(image, p);
            previews[i] = image;
        }

        dimensions = label("Chọn 4 ảnh để xem kích thước khung cần dùng.", 14, Color.DKGRAY);
        body.addView(dimensions);
        Button chooseFrame = button("2. Chọn khung PNG trong suốt");
        chooseFrame.setOnClickListener(v -> pickFrame());
        body.addView(chooseFrame);
        frameLabel = label("Chưa chọn khung (có thể xuất không khung)", 14, Color.DKGRAY);
        body.addView(frameLabel);

        saveButton = button("3. Lưu ảnh PNG không mất dữ liệu");
        saveButton.setOnClickListener(v -> save());
        body.addView(saveButton);
        status = label("Tất cả ảnh được xử lý trên điện thoại.", 14, Color.rgb(16, 55, 119));
        body.addView(status);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(body);
        setContentView(scroll);
    }

    private TextView label(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setPadding(0, dp(8), 0, dp(8));
        return view;
    }

    private Button button(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(56));
        p.topMargin = dp(12);
        button.setLayoutParams(p);
        return button;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void pickPhotos() {
        if (busy) return;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, PICK_PHOTOS);
    }

    private void pickFrame() {
        if (busy) return;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("image/png");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, PICK_FRAME);
    }

    private void save() {
        if (busy) return;
        if (photos.size() != 4) {
            status.setText("Vui lòng chọn đúng 4 ảnh.");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/png");
        intent.putExtra(Intent.EXTRA_TITLE, "photobooth-" + System.currentTimeMillis() + ".png");
        startActivityForResult(intent, SAVE_PNG);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        if (requestCode == PICK_PHOTOS) {
            List<Uri> selected = new ArrayList<>();
            ClipData clip = data.getClipData();
            if (clip != null) {
                for (int i = 0; i < clip.getItemCount(); i++) selected.add(clip.getItemAt(i).getUri());
            } else if (data.getData() != null) {
                selected.add(data.getData());
            }
            if (selected.size() != 4) {
                status.setText("Bạn đã chọn " + selected.size() + " ảnh. Hãy chọn đúng 4 ảnh cùng lúc.");
                return;
            }
            photos.clear();
            photos.addAll(selected);
            for (Uri uri : selected) persistRead(uri);
            refreshPreview();
        } else if (requestCode == PICK_FRAME && data.getData() != null) {
            frame = data.getData();
            persistRead(frame);
            frameLabel.setText("Khung: " + fileName(frame));
            status.setText("Đã chọn khung. Khung cần có kích thước pixel đúng như ghi bên trên.");
        } else if (requestCode == SAVE_PNG && data.getData() != null) {
            export(data.getData());
        }
    }

    private void persistRead(Uri uri) {
        try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
        catch (SecurityException ignored) { /* Quyền tạm thời vẫn đủ cho phiên hiện tại. */ }
    }

    private String fileName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) { }
        return "frame.png";
    }

    private void refreshPreview() {
        busy = true;
        saveButton.setEnabled(false);
        status.setText("Đang đọc thông tin ảnh…");
        List<Uri> selected = new ArrayList<>(photos);
        worker.execute(() -> {
            try {
                Layout layout = inspect(selected);
                Bitmap[] thumbs = new Bitmap[4];
                for (int i = 0; i < 4; i++) thumbs[i] = decode(selected.get(i), 512);
                runOnUiThread(() -> {
                    for (int i = 0; i < 4; i++) previews[i].setImageBitmap(thumbs[i]);
                    dimensions.setText("Ảnh ghép: " + layout.width + " × " + layout.height
                            + " px. Khung PNG cần đúng kích thước này.");
                    status.setText("Sẵn sàng xuất ảnh PNG.");
                    busy = false;
                    saveButton.setEnabled(true);
                });
            } catch (Exception | OutOfMemoryError e) {
                runOnUiThread(() -> {
                    status.setText("Không đọc được ảnh: " + e.getMessage());
                    busy = false;
                    saveButton.setEnabled(true);
                });
            }
        });
    }

    private void export(Uri destination) {
        busy = true;
        saveButton.setEnabled(false);
        status.setText("Đang ghép ảnh nguyên kích thước…");
        List<Uri> selected = new ArrayList<>(photos);
        Uri selectedFrame = frame;
        worker.execute(() -> {
            Bitmap output = null;
            try {
                Layout layout = inspect(selected);
                if ((long) layout.width * layout.height > MAX_PIXELS)
                    throw new IOException("Ảnh ghép vượt 60 triệu pixel; cần thiết bị có bộ nhớ lớn hơn.");
                if (selectedFrame != null) {
                    Size frameSize = rawSize(selectedFrame);
                    if (frameSize.width != layout.width || frameSize.height != layout.height)
                        throw new IOException("Khung là " + frameSize.width + " × " + frameSize.height
                                + " px; cần " + layout.width + " × " + layout.height + " px.");
                }
                output = Bitmap.createBitmap(layout.width, layout.height, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(output);
                canvas.drawColor(Color.WHITE);
                Paint paint = new Paint();
                paint.setFilterBitmap(false); // Không nội suy hoặc thu nhỏ ảnh nguồn.
                for (int i = 0; i < 4; i++) {
                    Bitmap source = decode(selected.get(i), 0);
                    int col = i % 2, row = i / 2;
                    int x = (col == 0 ? 0 : layout.colWidths[0]) + (layout.colWidths[col] - source.getWidth()) / 2;
                    int y = (row == 0 ? 0 : layout.rowHeights[0]) + (layout.rowHeights[row] - source.getHeight()) / 2;
                    canvas.drawBitmap(source, x, y, paint);
                    source.recycle();
                }
                if (selectedFrame != null) {
                    Bitmap overlay = decode(selectedFrame, 0);
                    canvas.drawBitmap(overlay, 0, 0, paint);
                    overlay.recycle();
                }
                try (OutputStream stream = getContentResolver().openOutputStream(destination, "w")) {
                    if (stream == null || !output.compress(Bitmap.CompressFormat.PNG, 100, stream))
                        throw new IOException("Không ghi được tệp PNG.");
                }
                runOnUiThread(() -> status.setText("Đã lưu PNG " + layout.width + " × " + layout.height + " px."));
            } catch (Exception | OutOfMemoryError e) {
                runOnUiThread(() -> status.setText("Xuất ảnh thất bại: " + e.getMessage()));
            } finally {
                if (output != null) output.recycle();
                runOnUiThread(() -> { busy = false; saveButton.setEnabled(true); });
            }
        });
    }

    private Layout inspect(List<Uri> selected) throws IOException {
        Size[] sizes = new Size[4];
        for (int i = 0; i < 4; i++) sizes[i] = orientedSize(selected.get(i));
        int left = Math.max(sizes[0].width, sizes[2].width);
        int right = Math.max(sizes[1].width, sizes[3].width);
        int top = Math.max(sizes[0].height, sizes[1].height);
        int bottom = Math.max(sizes[2].height, sizes[3].height);
        long width = (long) left + right, height = (long) top + bottom;
        if (width > Integer.MAX_VALUE || height > Integer.MAX_VALUE || width * height > MAX_PIXELS)
            throw new IOException("Ảnh ghép vượt 60 triệu pixel; app không giảm độ phân giải tự động.");
        return new Layout((int) width, (int) height, new int[]{left, right}, new int[]{top, bottom});
    }

    private Size rawSize(Uri uri) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (InputStream stream = getContentResolver().openInputStream(uri)) {
            if (stream == null) throw new IOException("Không mở được ảnh.");
            BitmapFactory.decodeStream(stream, null, options);
        }
        if (options.outWidth <= 0 || options.outHeight <= 0) throw new IOException("Định dạng ảnh không hỗ trợ.");
        return new Size(options.outWidth, options.outHeight);
    }

    private Size orientedSize(Uri uri) throws IOException {
        Size raw = rawSize(uri);
        int orientation = orientation(uri);
        return orientation >= 5 && orientation <= 8
                ? new Size(raw.height, raw.width) : raw;
    }

    private int orientation(Uri uri) {
        try (InputStream stream = getContentResolver().openInputStream(uri)) {
            if (stream != null) return new ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        } catch (Exception ignored) { }
        return ExifInterface.ORIENTATION_NORMAL;
    }

    private Bitmap decode(Uri uri, int maxPreviewSide) throws IOException {
        Size raw = rawSize(uri);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inSampleSize = 1;
        if (maxPreviewSide > 0) {
            while (Math.max(raw.width, raw.height) / options.inSampleSize > maxPreviewSide * 2) {
                options.inSampleSize *= 2;
            }
        }
        Bitmap bitmap;
        try (InputStream stream = getContentResolver().openInputStream(uri)) {
            if (stream == null) throw new IOException("Không mở được ảnh.");
            bitmap = BitmapFactory.decodeStream(stream, null, options);
        }
        if (bitmap == null) throw new IOException("Không giải mã được ảnh.");
        Matrix matrix = orientationMatrix(orientation(uri));
        if (matrix.isIdentity()) return bitmap;
        Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, false);
        if (rotated != bitmap) bitmap.recycle();
        return rotated;
    }

    private Matrix orientationMatrix(int exif) {
        Matrix m = new Matrix();
        switch (exif) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: m.setScale(-1, 1); break;
            case ExifInterface.ORIENTATION_ROTATE_180: m.setRotate(180); break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL: m.setScale(1, -1); break;
            case ExifInterface.ORIENTATION_TRANSPOSE: m.setRotate(90); m.postScale(-1, 1); break;
            case ExifInterface.ORIENTATION_ROTATE_90: m.setRotate(90); break;
            case ExifInterface.ORIENTATION_TRANSVERSE: m.setRotate(270); m.postScale(-1, 1); break;
            case ExifInterface.ORIENTATION_ROTATE_270: m.setRotate(270); break;
        }
        return m;
    }

    @Override protected void onDestroy() {
        worker.shutdown();
        super.onDestroy();
    }

    private static class Size {
        final int width, height;
        Size(int width, int height) { this.width = width; this.height = height; }
    }

    private static class Layout {
        final int width, height;
        final int[] colWidths, rowHeights;
        Layout(int width, int height, int[] colWidths, int[] rowHeights) {
            this.width = width; this.height = height;
            this.colWidths = colWidths; this.rowHeights = rowHeights;
        }
    }
}
