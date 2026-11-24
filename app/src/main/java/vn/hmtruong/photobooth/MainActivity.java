package vn.hmtruong.photobooth;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int PRINT_WIDTH_UNITS = 25; // 10 cm : 14.8 cm = 25 : 37.
    private static final int PRINT_HEIGHT_UNITS = 37;
    private static final int PICK_PHOTOS = 1;
    private static final int PICK_FRAME = 2;
    private static final int SAVE_PNG = 3;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<Uri> photos = new ArrayList<>();
    private Uri frame;
    private TextView status;
    private TextView dimensions;
    private TextView frameLabel;
    private Button saveButton;
    private EditorPreview preview;
    private TextView editLabel;
    private TextView frameRatioLabel;
    private SeekBar zoomBar, rotationBar, panXBar, panYBar, frameRatioBar;
    private final Bitmap[] thumbs = new Bitmap[4];
    private Bitmap frameThumb;
    private final Size[] photoSizes = new Size[4];
    private Layout previewLayout;
    private final PhotoEdit[] edits = {new PhotoEdit(), new PhotoEdit(), new PhotoEdit(), new PhotoEdit()};
    private int selectedPhoto;
    private float frameWidthScale = 1f;
    private boolean settingControls;
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
        body.addView(label("Ghép 4 ảnh tại máy • PNG khổ in 10 × 14,8 cm", 15, Color.DKGRAY));

        Button choosePhotos = button("1. Chọn 4 ảnh");
        choosePhotos.setOnClickListener(v -> pickPhotos());
        body.addView(choosePhotos);

        preview = new EditorPreview();
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(-1, dp(470));
        previewParams.topMargin = dp(12);
        body.addView(preview, previewParams);
        body.addView(label("Chạm một ô ảnh để chỉnh • kéo ảnh để đổi vị trí", 13, Color.DKGRAY));

        editLabel = label("Ảnh 1 · Thu phóng 100% · Xoay 0°", 15, Color.rgb(16, 55, 119));
        body.addView(editLabel);
        zoomBar = addSlider(body, "Thu phóng ảnh (25–400%)", 25, 400, 100,
                value -> { edits[selectedPhoto].zoom = value / 100f; edited(); });
        rotationBar = addSlider(body, "Xoay ảnh (-180° đến 180°)", -180, 180, 0,
                value -> { edits[selectedPhoto].rotation = value; edited(); });
        panXBar = addSlider(body, "Dịch ảnh ngang", -100, 100, 0,
                value -> { edits[selectedPhoto].panX = value / 100f; edited(); });
        panYBar = addSlider(body, "Dịch ảnh dọc", -100, 100, 0,
                value -> { edits[selectedPhoto].panY = value / 100f; edited(); });
        Button resetPhoto = button("Đặt lại ảnh đang chọn");
        resetPhoto.setOnClickListener(v -> { edits[selectedPhoto] = new PhotoEdit(); showSelectedPhoto(); });
        body.addView(resetPhoto);

        dimensions = label("Chọn 4 ảnh để xem kích thước ảnh ghép.", 14, Color.DKGRAY);
        body.addView(dimensions);
        Button chooseFrame = button("2. Chọn khung PNG trong suốt");
        chooseFrame.setOnClickListener(v -> pickFrame());
        body.addView(chooseFrame);
        frameLabel = label("Chưa chọn khung (có thể xuất không khung)", 14, Color.DKGRAY);
        body.addView(frameLabel);
        frameRatioLabel = label("Độ rộng khung: 100%", 14, Color.DKGRAY);
        body.addView(frameRatioLabel);
        frameRatioBar = addSlider(body, "Nới/thu chiều ngang của khung (70–130%)", 70, 130, 100,
                value -> {
                    frameWidthScale = value / 100f;
                    frameRatioLabel.setText("Độ rộng khung: " + value + "%");
                    preview.invalidate();
                });
        frameRatioBar.setEnabled(false);

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

    private interface SliderChange { void changed(int value); }

    private SeekBar addSlider(LinearLayout body, String title, int min, int max, int initial, SliderChange change) {
        body.addView(label(title, 13, Color.DKGRAY));
        SeekBar bar = new SeekBar(this);
        bar.setMax(max - min);
        bar.setProgress(initial - min);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!settingControls) change.changed(progress + min);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        body.addView(bar);
        return bar;
    }

    private void edited() {
        if (settingControls) return;
        editLabel.setText("Ảnh " + (selectedPhoto + 1) + " · Thu phóng "
                + Math.round(edits[selectedPhoto].zoom * 100) + "% · Xoay "
                + edits[selectedPhoto].rotation + "°");
        preview.invalidate();
    }

    private void showSelectedPhoto() {
        settingControls = true;
        PhotoEdit edit = edits[selectedPhoto];
        zoomBar.setProgress(Math.round(edit.zoom * 100) - 25);
        rotationBar.setProgress(edit.rotation + 180);
        panXBar.setProgress(Math.round(edit.panX * 100) + 100);
        panYBar.setProgress(Math.round(edit.panY * 100) + 100);
        settingControls = false;
        edited();
    }

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
            for (int i = 0; i < 4; i++) edits[i] = new PhotoEdit();
            selectedPhoto = 0;
            showSelectedPhoto();
            refreshPreview();
        } else if (requestCode == PICK_FRAME && data.getData() != null) {
            frame = data.getData();
            persistRead(frame);
            frameLabel.setText("Khung: " + fileName(frame));
            frameRatioBar.setEnabled(true);
            status.setText("Đã chọn khung. Đang cập nhật bản xem trước…");
            if (photos.size() == 4) refreshPreview();
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
        Uri selectedFrame = frame;
        worker.execute(() -> {
            try {
                Size[] sizes = new Size[4];
                Bitmap[] newThumbs = new Bitmap[4];
                for (int i = 0; i < 4; i++) {
                    sizes[i] = orientedSize(selected.get(i));
                    newThumbs[i] = decode(selected.get(i), 512);
                }
                if (selectedFrame != null) rawSize(selectedFrame);
                Bitmap newFrameThumb = selectedFrame == null ? null : decode(selectedFrame, 768);
                runOnUiThread(() -> {
                    for (int i = 0; i < 4; i++) {
                        Bitmap old = thumbs[i];
                        thumbs[i] = newThumbs[i];
                        photoSizes[i] = sizes[i];
                        if (old != null) old.recycle();
                    }
                    if (frameThumb != null) frameThumb.recycle();
                    frameThumb = newFrameThumb;
                    recalculateLayout();
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

    private void recalculateLayout() {
        for (Size size : photoSizes) if (size == null) return;
        try {
            previewLayout = makeLayout(photoSizes);
            dimensions.setText("Khổ in 10 × 14,8 cm • ảnh ghép " + previewLayout.width + " × "
                    + previewLayout.height + " px. Bản xem trước hiển thị cả khung và phần ảnh bị cắt.");
            int availableWidth = getResources().getDisplayMetrics().widthPixels - dp(40);
            int desiredHeight = (int) ((long) availableWidth * previewLayout.height / previewLayout.width);
            preview.getLayoutParams().height = Math.max(dp(340), Math.min(dp(620), desiredHeight));
            preview.requestLayout();
            preview.invalidate();
        } catch (IOException e) {
            status.setText(e.getMessage());
        }
    }

    private void export(Uri destination) {
        busy = true;
        saveButton.setEnabled(false);
        status.setText("Đang ghép ảnh nguyên kích thước…");
        List<Uri> selected = new ArrayList<>(photos);
        Uri selectedFrame = frame;
        float selectedFrameWidth = frameWidthScale;
        PhotoEdit[] selectedEdits = new PhotoEdit[4];
        for (int i = 0; i < 4; i++) selectedEdits[i] = edits[i].copy();
        worker.execute(() -> {
            long started = SystemClock.elapsedRealtime();
            try {
                Layout layout = inspect(selected);
                RenderStats stats;
                try (OutputStream stream = getContentResolver().openOutputStream(destination, "w")) {
                    if (stream == null) throw new IOException("Không mở được tệp PNG để ghi.");
                    stats = renderTiled(selected, selectedFrame, layout, selectedEdits,
                            selectedFrameWidth, stream);
                }
                long total = SystemClock.elapsedRealtime() - started;
                String summary = String.format(Locale.getDefault(),
                        "Đã lưu PNG %d × %d px · Tổng %.1f giây\n"
                                + "Chuẩn bị %.1f · Ghép ảnh %.1f · Khung %.1f · Nén/ghi PNG %.1f giây",
                        layout.width, layout.height, total / 1000.0, stats.setupMs / 1000.0,
                        stats.photoMs / 1000.0, stats.frameMs / 1000.0, stats.pngMs / 1000.0);
                runOnUiThread(() -> status.setText(summary));
            } catch (Exception | OutOfMemoryError e) {
                runOnUiThread(() -> status.setText("Xuất ảnh thất bại: " + e.getMessage()));
            } finally {
                runOnUiThread(() -> { busy = false; saveButton.setEnabled(true); });
            }
        });
    }

    private RenderStats renderTiled(List<Uri> selected, Uri selectedFrame, Layout layout,
                                    PhotoEdit[] selectedEdits, float selectedFrameWidth, OutputStream stream)
            throws IOException {
        RenderStats stats = new RenderStats();
        long setupStart = SystemClock.elapsedRealtime();
        Source[] sources = new Source[4];
        Source overlay = null;
        Bitmap fittedFrame = null;
        Bitmap tile = null;
        try {
            for (int i = 0; i < 4; i++) {
                sources[i] = new Source(selected.get(i), orientation(selected.get(i)));
            }
            if (selectedFrame != null) {
                Size frameSize = rawSize(selectedFrame);
                if (selectedFrameWidth == 1f
                        && frameSize.width == layout.width && frameSize.height == layout.height)
                    overlay = new Source(selectedFrame, ExifInterface.ORIENTATION_NORMAL);
                else
                    fittedFrame = decode(selectedFrame, 0);
            }

            // The tile and one PNG row are the only buffers that grow with output width.
            int rowsPerTile = Math.max(1, Math.min(128, 2_000_000 / layout.width));
            tile = Bitmap.createBitmap(layout.width, rowsPerTile, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(tile);
            Paint paint = new Paint();
            paint.setFilterBitmap(true);
            Paint framePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
            Matrix frameTransform = new Matrix();
            int[] pixels = new int[layout.width];
            LosslessPngWriter writer = new LosslessPngWriter(stream, layout.width, layout.height,
                    10.0, 14.8);
            stats.setupMs = SystemClock.elapsedRealtime() - setupStart;
            try {
                int lastProgress = -1;
                for (int top = 0; top < layout.height; ) {
                    int rows = Math.min(rowsPerTile, layout.height - top);
                    canvas.drawColor(Color.WHITE);
                    long stageStart = SystemClock.elapsedRealtime();
                    for (int i = 0; i < 4; i++)
                        drawPhotoPart(canvas, sources[i], layout, i, selectedEdits[i], top, rows, paint);
                    stats.photoMs += SystemClock.elapsedRealtime() - stageStart;
                    stageStart = SystemClock.elapsedRealtime();
                    if (overlay != null) drawPart(canvas, overlay, top, rows, paint);
                    if (fittedFrame != null) {
                        frameTransform.setScale(layout.width * selectedFrameWidth / fittedFrame.getWidth(),
                                layout.height / (float) fittedFrame.getHeight());
                        frameTransform.postTranslate(layout.width * (1f - selectedFrameWidth) / 2f,
                                -top);
                        canvas.drawBitmap(fittedFrame, frameTransform, framePaint);
                    }
                    stats.frameMs += SystemClock.elapsedRealtime() - stageStart;
                    stageStart = SystemClock.elapsedRealtime();
                    for (int row = 0; row < rows; row++) {
                        tile.getPixels(pixels, 0, layout.width, 0, row, layout.width, 1);
                        writer.writeRow(pixels);
                    }
                    stats.pngMs += SystemClock.elapsedRealtime() - stageStart;
                    int progress = (int) (((long) (top + rows) * 10) / layout.height);
                    if (progress != lastProgress) {
                        lastProgress = progress;
                        int percent = progress * 10;
                        runOnUiThread(() -> status.setText("Đang lưu PNG nguyên kích thước: " + percent + "%"));
                    }
                    top += rows;
                }
                long finishStart = SystemClock.elapsedRealtime();
                writer.finish();
                stats.pngMs += SystemClock.elapsedRealtime() - finishStart;
            } finally {
                writer.abort();
            }
        } finally {
            if (tile != null) tile.recycle();
            for (Source source : sources) if (source != null) source.close();
            if (overlay != null) overlay.close();
            if (fittedFrame != null) fittedFrame.recycle();
        }
        return stats;
    }

    private void drawPart(Canvas canvas, Source source, int top, int rows, Paint paint) throws IOException {
        int left = Math.max(0, source.x);
        int right = Math.min(canvas.getWidth(), source.x + source.width);
        int upper = Math.max(top, source.y);
        int lower = Math.min(top + rows, source.y + source.height);
        if (left >= right || upper >= lower) return;
        Rect oriented = new Rect(left - source.x, upper - source.y,
                right - source.x, lower - source.y);
        Rect raw = rawRegion(oriented, source.rawWidth, source.rawHeight, source.orientation);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inSampleSize = 1;
        options.inScaled = false;
        Bitmap part = source.decoder.decodeRegion(raw, options);
        if (part == null) throw new IOException("Không đọc được một phần của ảnh nguồn.");
        Bitmap rotated = part;
        try {
            Matrix matrix = orientationMatrix(source.orientation);
            if (!matrix.isIdentity())
                rotated = Bitmap.createBitmap(part, 0, 0, part.getWidth(), part.getHeight(), matrix, false);
            if (rotated.getWidth() != oriented.width() || rotated.getHeight() != oriented.height())
                throw new IOException("Không thể xoay ảnh nguồn ở độ phân giải gốc.");
            canvas.drawBitmap(rotated, left, upper - top, paint);
        } finally {
            if (rotated != part) rotated.recycle();
            part.recycle();
        }
    }

    private void drawPhotoPart(Canvas canvas, Source source, Layout layout, int index,
                               PhotoEdit edit, int top, int rows, Paint paint) throws IOException {
        Rect cell = cellRect(layout, index);
        int upper = Math.max(cell.top, top);
        int lower = Math.min(cell.bottom, top + rows);
        if (upper >= lower) return;
        Matrix transform = photoMatrix(layout, index, source.width, source.height, edit);
        Matrix inverse = new Matrix();
        if (!transform.invert(inverse)) throw new IOException("Không thể tính góc xoay ảnh.");
        for (int left = cell.left; left < cell.right; ) {
            int right = (int) Math.min((long) cell.right, (long) left + 512);
            float[] corners = {left, upper, right, upper, left, lower, right, lower};
            inverse.mapPoints(corners);
            float minX = Math.min(Math.min(corners[0], corners[2]), Math.min(corners[4], corners[6]));
            float maxX = Math.max(Math.max(corners[0], corners[2]), Math.max(corners[4], corners[6]));
            float minY = Math.min(Math.min(corners[1], corners[3]), Math.min(corners[5], corners[7]));
            float maxY = Math.max(Math.max(corners[1], corners[3]), Math.max(corners[5], corners[7]));
            Rect oriented = new Rect(
                    Math.max(0, (int) Math.floor(minX) - 2),
                    Math.max(0, (int) Math.floor(minY) - 2),
                    Math.min(source.width, (int) Math.ceil(maxX) + 2),
                    Math.min(source.height, (int) Math.ceil(maxY) + 2));
            if (oriented.left < oriented.right && oriented.top < oriented.bottom) {
                Rect raw = rawRegion(oriented, source.rawWidth, source.rawHeight, source.orientation);
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                options.inSampleSize = 1;
                options.inScaled = false;
                Bitmap part = source.decoder.decodeRegion(raw, options);
                if (part == null) throw new IOException("Không đọc được vùng ảnh sau khi xoay.");
                Bitmap rotated = part;
                try {
                    Matrix orientation = orientationMatrix(source.orientation);
                    if (!orientation.isIdentity())
                        rotated = Bitmap.createBitmap(part, 0, 0, part.getWidth(), part.getHeight(), orientation, false);
                    canvas.save();
                    canvas.clipRect(left, upper - top, right, lower - top);
                    canvas.translate(0, -top);
                    canvas.concat(transform);
                    canvas.drawBitmap(rotated, oriented.left, oriented.top, paint);
                    canvas.restore();
                } finally {
                    if (rotated != part) rotated.recycle();
                    part.recycle();
                }
            }
            left = right;
        }
    }

    private Rect cellRect(Layout layout, int index) {
        int col = index % 2, row = index / 2;
        int left = col == 0 ? 0 : layout.colWidths[0];
        int top = row == 0 ? 0 : layout.rowHeights[0];
        return new Rect(left, top, left + layout.colWidths[col], top + layout.rowHeights[row]);
    }

    private Matrix photoMatrix(Layout layout, int index, int width, int height, PhotoEdit edit) {
        Rect cell = cellRect(layout, index);
        Matrix matrix = new Matrix();
        matrix.setTranslate(-width / 2f, -height / 2f);
        matrix.postScale(edit.zoom, edit.zoom);
        matrix.postRotate(edit.rotation);
        matrix.postTranslate(cell.exactCenterX() + edit.panX * cell.width() / 2f,
                cell.exactCenterY() + edit.panY * cell.height() / 2f);
        return matrix;
    }

    private Rect rawRegion(Rect r, int width, int height, int orientation) {
        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                return new Rect(width - r.right, r.top, width - r.left, r.bottom);
            case ExifInterface.ORIENTATION_ROTATE_180:
                return new Rect(width - r.right, height - r.bottom, width - r.left, height - r.top);
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                return new Rect(r.left, height - r.bottom, r.right, height - r.top);
            case ExifInterface.ORIENTATION_TRANSPOSE:
                return new Rect(r.top, r.left, r.bottom, r.right);
            case ExifInterface.ORIENTATION_ROTATE_90:
                return new Rect(r.top, height - r.right, r.bottom, height - r.left);
            case ExifInterface.ORIENTATION_TRANSVERSE:
                return new Rect(width - r.bottom, height - r.right, width - r.top, height - r.left);
            case ExifInterface.ORIENTATION_ROTATE_270:
                return new Rect(width - r.bottom, r.left, width - r.top, r.right);
            default:
                return r;
        }
    }

    private Layout inspect(List<Uri> selected) throws IOException {
        Size[] sizes = new Size[4];
        for (int i = 0; i < 4; i++) sizes[i] = orientedSize(selected.get(i));
        return makeLayout(sizes);
    }

    private Layout makeLayout(Size[] sizes) throws IOException {
        int left = Math.max(sizes[0].width, sizes[2].width);
        int right = Math.max(sizes[1].width, sizes[3].width);
        int top = Math.max(sizes[0].height, sizes[1].height);
        int bottom = Math.max(sizes[2].height, sizes[3].height);
        long width = (long) left + right, height = (long) top + bottom;
        if (width * PRINT_HEIGHT_UNITS < height * PRINT_WIDTH_UNITS)
            width = (height * PRINT_WIDTH_UNITS + PRINT_HEIGHT_UNITS - 1) / PRINT_HEIGHT_UNITS;
        else
            height = (width * PRINT_HEIGHT_UNITS + PRINT_WIDTH_UNITS - 1) / PRINT_WIDTH_UNITS;
        if (width > Integer.MAX_VALUE || height > Integer.MAX_VALUE)
            throw new IOException("Kích thước ảnh vượt giới hạn định dạng Android.");
        left = (int) (width / 2);
        right = (int) (width - left);
        top = (int) (height / 2);
        bottom = (int) (height - top);
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

    private static final class PhotoEdit {
        float zoom = 1f;
        int rotation;
        float panX, panY;

        PhotoEdit copy() {
            PhotoEdit copy = new PhotoEdit();
            copy.zoom = zoom;
            copy.rotation = rotation;
            copy.panX = panX;
            copy.panY = panY;
            return copy;
        }
    }

    private static final class RenderStats {
        long setupMs, photoMs, frameMs, pngMs;
    }

    private final class EditorPreview extends View {
        private final Paint imagePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final Paint selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final ScaleGestureDetector pinch;
        private float viewScale = 1f, offsetX, offsetY, lastX, lastY;

        EditorPreview() {
            super(MainActivity.this);
            setBackgroundColor(Color.rgb(226, 234, 248));
            selectedPaint.setColor(Color.rgb(211, 164, 64));
            selectedPaint.setStyle(Paint.Style.STROKE);
            selectedPaint.setStrokeWidth(dp(3));
            pinch = new ScaleGestureDetector(MainActivity.this,
                    new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                        @Override public boolean onScale(ScaleGestureDetector detector) {
                            PhotoEdit edit = edits[selectedPhoto];
                            edit.zoom = Math.max(0.25f, Math.min(4f,
                                    edit.zoom * detector.getScaleFactor()));
                            showSelectedPhoto();
                            return true;
                        }
                    });
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            Layout layout = previewLayout;
            if (layout == null) return;
            viewScale = Math.min(getWidth() / (float) layout.width,
                    getHeight() / (float) layout.height);
            offsetX = (getWidth() - layout.width * viewScale) / 2f;
            offsetY = (getHeight() - layout.height * viewScale) / 2f;
            canvas.save();
            canvas.translate(offsetX, offsetY);
            canvas.scale(viewScale, viewScale);
            canvas.drawColor(Color.rgb(226, 234, 248));
            canvas.drawRect(0, 0, layout.width, layout.height, whitePaint());
            for (int i = 0; i < 4; i++) {
                if (thumbs[i] == null || photoSizes[i] == null) continue;
                Rect cell = cellRect(layout, i);
                canvas.save();
                canvas.clipRect(cell);
                canvas.concat(photoMatrix(layout, i, photoSizes[i].width,
                        photoSizes[i].height, edits[i]));
                canvas.drawBitmap(thumbs[i], null,
                        new RectF(0, 0, photoSizes[i].width, photoSizes[i].height), imagePaint);
                canvas.restore();
            }
            if (frameThumb != null) {
                float frameLeft = layout.width * (1f - frameWidthScale) / 2f;
                canvas.drawBitmap(frameThumb, null,
                        new RectF(frameLeft, 0, frameLeft + layout.width * frameWidthScale,
                                layout.height), imagePaint);
            }
            Rect chosen = cellRect(layout, selectedPhoto);
            selectedPaint.setStrokeWidth(dp(3) / viewScale);
            canvas.drawRect(chosen, selectedPaint);
            canvas.restore();
        }

        private Paint whitePaint() {
            Paint paint = new Paint();
            paint.setColor(Color.WHITE);
            return paint;
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (previewLayout == null) return false;
            pinch.onTouchEvent(event);
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                float x = (event.getX() - offsetX) / viewScale;
                float y = (event.getY() - offsetY) / viewScale;
                if (x < 0 || y < 0 || x >= previewLayout.width || y >= previewLayout.height)
                    return false;
                selectedPhoto = (x >= previewLayout.colWidths[0] ? 1 : 0)
                        + (y >= previewLayout.rowHeights[0] ? 2 : 0);
                lastX = event.getX();
                lastY = event.getY();
                getParent().requestDisallowInterceptTouchEvent(true);
                showSelectedPhoto();
                return true;
            }
            if (action == MotionEvent.ACTION_MOVE && event.getPointerCount() == 1 && !pinch.isInProgress()) {
                Rect cell = cellRect(previewLayout, selectedPhoto);
                PhotoEdit edit = edits[selectedPhoto];
                edit.panX = Math.max(-1f, Math.min(1f,
                        edit.panX + (event.getX() - lastX) / viewScale / (cell.width() / 2f)));
                edit.panY = Math.max(-1f, Math.min(1f,
                        edit.panY + (event.getY() - lastY) / viewScale / (cell.height() / 2f)));
                lastX = event.getX();
                lastY = event.getY();
                showSelectedPhoto();
                return true;
            }
            if (action == MotionEvent.ACTION_POINTER_UP && event.getPointerCount() > 1) {
                int remaining = event.getActionIndex() == 0 ? 1 : 0;
                lastX = event.getX(remaining);
                lastY = event.getY(remaining);
                return true;
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            }
            return true;
        }
    }

    private final class Source {
        final InputStream stream;
        final BitmapRegionDecoder decoder;
        final int rawWidth, rawHeight, width, height, orientation;
        int x, y;

        Source(Uri uri, int orientation) throws IOException {
            InputStream opened = getContentResolver().openInputStream(uri);
            if (opened == null) throw new IOException("Không mở được ảnh nguồn.");
            BitmapRegionDecoder created;
            try { created = BitmapRegionDecoder.newInstance(opened, false); }
            catch (Exception e) { opened.close(); throw new IOException("Ảnh không hỗ trợ đọc theo từng dải.", e); }
            if (created == null) { opened.close(); throw new IOException("Không đọc được ảnh nguồn."); }
            stream = opened;
            decoder = created;
            rawWidth = decoder.getWidth();
            rawHeight = decoder.getHeight();
            this.orientation = orientation;
            boolean swapped = orientation >= 5 && orientation <= 8;
            width = swapped ? rawHeight : rawWidth;
            height = swapped ? rawWidth : rawHeight;
        }

        void close() {
            decoder.recycle();
            try { stream.close(); } catch (IOException ignored) { }
        }
    }

    @Override protected void onDestroy() {
        worker.shutdown();
        for (Bitmap thumb : thumbs) if (thumb != null) thumb.recycle();
        if (frameThumb != null) frameThumb.recycle();
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
