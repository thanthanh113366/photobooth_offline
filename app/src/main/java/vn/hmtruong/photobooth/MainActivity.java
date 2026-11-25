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
import android.graphics.Typeface;
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
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.Spinner;
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
    private static final int SAVE_PRINT_PNG = 4;
    private static final int SAVE_DETAIL_PNG = 5;
    private static final int PRINT_WIDTH_PX = 1200;
    private static final int PRINT_HEIGHT_PX = 1776; // 10 × 14.8 cm at about 305 DPI.
    private static final int DETAIL_WIDTH_PX = 2400;
    private static final int DETAIL_HEIGHT_PX = 3552;
    private static final float PRINT_CONTENT_SCALE = 0.95f;
    private static final String[] RECIPIENTS = {
            "Nguyễn Trịnh Thủy Tiên", "Lê Đức Anh", "Mai Huỳnh Tuấn Anh",
            "Nguyễn Lê Yến Nhi", "Trần Hữu Kim Quý", "Nguyễn Ngọc Khánh Vân",
            "Nguyễn Hoài Nam", "Phạm Mai Diệu Thảo", "Hoàng Thị Thúy Hòa",
            "Phan Thị Gia Như", "Nguyễn Ngọc Kim Chi",
            "Trần Lê Gia Bảo", "Nguyễn Ngọc Anh Thư", "Ngô Thị Thúy Lan",
            "Trần Thiên Ân", "Nguyễn Huy Hoàng", "Nguyễn Minh Nhật",
            "Nguyễn Đức Thái Bảo", "Trần Minh Huấn", "Vũ Tiến Thành",
            "Vũ Phước Điền", "Hoàng Ngọc", "Nguyễn Ngọc Minh Thư",
            "Nguyễn Hoàng Quân", "Nguyễn Gia Phú", "Nông Nhựt Quy",
            "Mai Xuân Tuấn", "Huỳnh Thái Bảo", "Nguyễn Trần Nhật Trung",
            "Nguyễn Minh Phú", "Nguyễn Thị Minh Phú", "Nguyễn Đức Huy",
            "Dương Phước Tài"
    };

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<Uri> photos = new ArrayList<>();
    private Uri frame;
    private TextView status;
    private TextView dimensions;
    private TextView frameLabel;
    private Button saveButton;
    private Button printButton;
    private Button detailButton;
    private Button choosePhotosButton;
    private Spinner countSelector;
    private Spinner recipientSelector;
    private EditorPreview preview;
    private TextView editLabel;
    private TextView frameRatioLabel;
    private SeekBar zoomBar, rotationBar, panXBar, panYBar, frameRatioBar;
    private final Bitmap[] thumbs = new Bitmap[8];
    private Bitmap frameThumb;
    private final Size[] photoSizes = new Size[8];
    private Layout previewLayout;
    private final PhotoEdit[] edits = new PhotoEdit[8];
    private int photoCount = 4;
    private int selectedPhoto;
    private float frameWidthScale = 1f;
    private String recipientName;
    private boolean settingControls;
    private volatile boolean busy;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        for (int i = 0; i < edits.length; i++) edits[i] = new PhotoEdit();
        int pad = dp(20);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(pad, pad, pad, pad);
        body.setBackgroundColor(Color.rgb(246, 249, 255));

        TextView title = label("PHOTOBOOTH", 30, Color.rgb(16, 55, 119));
        title.setTypeface(null, 1);
        body.addView(title);
        body.addView(label("Ghép 1, 2, 4 hoặc 8 ảnh tại máy • khổ in 10 × 14,8 cm", 15, Color.DKGRAY));
        body.addView(label("Số ảnh trong thiệp", 15, Color.DKGRAY));
        countSelector = new Spinner(this);
        countSelector.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"1 ảnh", "2 ảnh xếp dọc", "4 ảnh (2 × 2)", "8 ảnh (2 × 4)"}));
        body.addView(countSelector);

        choosePhotosButton = button("1. Chọn 4 ảnh");
        choosePhotosButton.setOnClickListener(v -> pickPhotos());
        body.addView(choosePhotosButton);

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
        Button builtInFrame = button("Dùng khung Graduation · UIT có sẵn");
        builtInFrame.setOnClickListener(v -> useBuiltInFrame());
        body.addView(builtInFrame);
        frameLabel = label("Chưa chọn khung (có thể xuất không khung)", 14, Color.DKGRAY);
        body.addView(frameLabel);
        frameRatioLabel = label("Độ rộng khung: 100%", 14, Color.DKGRAY);
        body.addView(frameRatioLabel);
        frameRatioBar = addSlider(body, "Nới/thu chiều ngang của khung (70–130%)", 70, 130, 100,
                value -> {
                    frameWidthScale = value / 100f;
                    frameRatioLabel.setText("Độ rộng khung: " + value + "%");
                    if (previewLayout != null) recalculateLayout();
                    else preview.invalidate();
                });
        frameRatioBar.setEnabled(false);

        body.addView(label("Người nhận lời cảm ơn", 15, Color.DKGRAY));
        recipientSelector = new Spinner(this);
        String[] recipientChoices = new String[RECIPIENTS.length + 1];
        recipientChoices[0] = "Chọn người nhận";
        System.arraycopy(RECIPIENTS, 0, recipientChoices, 1, RECIPIENTS.length);
        recipientSelector.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, recipientChoices));
        recipientSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                recipientName = position == 0 ? null : RECIPIENTS[position - 1];
                preview.invalidate();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        body.addView(recipientSelector);

        saveButton = button("3. Lưu ảnh PNG không mất dữ liệu");
        saveButton.setOnClickListener(v -> save());
        body.addView(saveButton);
        printButton = button("4. Lưu PNG bản in nhanh (10 × 14,8 cm · 305 DPI)");
        printButton.setOnClickListener(v -> save(true));
        body.addView(printButton);
        detailButton = button("5. Lưu PNG nét hơn khi phóng to (2400 × 3552 px)");
        detailButton.setOnClickListener(v -> save(SAVE_DETAIL_PNG));
        body.addView(detailButton);
        body.addView(label("Hai bản in chừa mép trắng khoảng 2,5 mm hai bên và 3,7 mm trên dưới.",
                13, Color.DKGRAY));
        status = label("Tất cả ảnh được xử lý trên điện thoại.", 14, Color.rgb(16, 55, 119));
        body.addView(status);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(body);
        setContentView(scroll);
        countSelector.setSelection(2);
        countSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                setPhotoCount(new int[]{1, 2, 4, 8}[position]);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        setPhotoCount(4);
    }

    private void setPhotoCount(int count) {
        if (busy) return;
        photoCount = count;
        photos.clear();
        previewLayout = null;
        selectedPhoto = 0;
        for (int i = 0; i < thumbs.length; i++) {
            if (thumbs[i] != null) thumbs[i].recycle();
            thumbs[i] = null;
            photoSizes[i] = null;
            edits[i] = new PhotoEdit();
        }
        if (frameThumb != null) frameThumb.recycle();
        frameThumb = null;
        frameWidthScale = 1f;
        frameRatioBar.setProgress(30);
        frame = builtInFrameUri(count);
        frameLabel.setText("Khung có sẵn: Graduation · UIT · HaMinhTruong (" + count + " ảnh)");
        frameRatioBar.setEnabled(true);
        frameRatioLabel.setText("Độ rộng khung: 100%");
        choosePhotosButton.setText("1. Chọn " + count + " ảnh");
        dimensions.setText("Chọn " + count + " ảnh để xem kích thước ảnh ghép.");
        status.setText("Đã chọn chế độ " + count + " ảnh và khung có sẵn.");
        showSelectedPhoto();
        preview.invalidate();
    }

    private Uri builtInFrameUri(int count) {
        int id = count == 1 ? R.drawable.graduation_frame_1
                : count == 2 ? R.drawable.graduation_frame_2
                : count == 8 ? R.drawable.graduation_frame_8
                : R.drawable.graduation_frame_4;
        return Uri.parse("android.resource://" + getPackageName() + "/" + id);
    }

    private void useBuiltInFrame() {
        if (busy) return;
        frame = builtInFrameUri(photoCount);
        frameLabel.setText("Khung có sẵn: Graduation · UIT · HaMinhTruong (" + photoCount + " ảnh)");
        frameWidthScale = 1f;
        frameRatioBar.setProgress(30);
        frameRatioLabel.setText("Độ rộng khung: 100%");
        if (photos.size() == photoCount) refreshPreview();
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

    private void save() { save(SAVE_PNG); }

    private void save(boolean printSize) { save(printSize ? SAVE_PRINT_PNG : SAVE_PNG); }

    private void save(int mode) {
        if (busy) return;
        if (photos.size() != photoCount) {
            status.setText("Vui lòng chọn đúng " + photoCount + " ảnh.");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/png");
        String label = mode == SAVE_DETAIL_PNG ? "detail-" : mode == SAVE_PRINT_PNG ? "print-" : "";
        intent.putExtra(Intent.EXTRA_TITLE, "photobooth-" + label
                + System.currentTimeMillis() + ".png");
        startActivityForResult(intent, mode);
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
            if (selected.size() != photoCount) {
                status.setText("Bạn đã chọn " + selected.size() + " ảnh. Hãy chọn đúng "
                        + photoCount + " ảnh cùng lúc.");
                return;
            }
            photos.clear();
            photos.addAll(selected);
            for (Uri uri : selected) persistRead(uri);
            for (int i = 0; i < photoCount; i++) edits[i] = new PhotoEdit();
            selectedPhoto = 0;
            showSelectedPhoto();
            refreshPreview();
        } else if (requestCode == PICK_FRAME && data.getData() != null) {
            frame = data.getData();
            persistRead(frame);
            frameLabel.setText("Khung: " + fileName(frame));
            frameRatioBar.setEnabled(true);
            status.setText("Đã chọn khung. Đang cập nhật bản xem trước…");
            if (photos.size() == photoCount) refreshPreview();
        } else if ((requestCode == SAVE_PNG || requestCode == SAVE_PRINT_PNG
                || requestCode == SAVE_DETAIL_PNG)
                && data.getData() != null) {
            export(data.getData(), requestCode);
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
        printButton.setEnabled(false);
        detailButton.setEnabled(false);
        countSelector.setEnabled(false);
        recipientSelector.setEnabled(false);
        status.setText("Đang đọc thông tin ảnh…");
        List<Uri> selected = new ArrayList<>(photos);
        Uri selectedFrame = frame;
        worker.execute(() -> {
            try {
                Size[] sizes = new Size[selected.size()];
                Bitmap[] newThumbs = new Bitmap[selected.size()];
                for (int i = 0; i < selected.size(); i++) {
                    sizes[i] = orientedSize(selected.get(i));
                    newThumbs[i] = decode(selected.get(i), 512);
                }
                if (selectedFrame != null) rawSize(selectedFrame);
                Bitmap newFrameThumb = selectedFrame == null ? null : decode(selectedFrame, 768);
                runOnUiThread(() -> {
                    for (int i = 0; i < thumbs.length; i++) {
                        Bitmap old = thumbs[i];
                        thumbs[i] = i < newThumbs.length ? newThumbs[i] : null;
                        photoSizes[i] = i < sizes.length ? sizes[i] : null;
                        if (old != null) old.recycle();
                    }
                    if (frameThumb != null) frameThumb.recycle();
                    frameThumb = newFrameThumb;
                    recalculateLayout();
                    status.setText("Sẵn sàng xuất ảnh PNG.");
                    busy = false;
                    saveButton.setEnabled(true);
                    printButton.setEnabled(true);
                    detailButton.setEnabled(true);
                    countSelector.setEnabled(true);
                    recipientSelector.setEnabled(true);
                });
            } catch (Exception | OutOfMemoryError e) {
                runOnUiThread(() -> {
                    status.setText("Không đọc được ảnh: " + e.getMessage());
                    busy = false;
                    saveButton.setEnabled(true);
                    printButton.setEnabled(true);
                    detailButton.setEnabled(true);
                    countSelector.setEnabled(true);
                    recipientSelector.setEnabled(true);
                });
            }
        });
    }

    private void recalculateLayout() {
        for (int i = 0; i < photoCount; i++) if (photoSizes[i] == null) return;
        try {
            Size[] selectedSizes = new Size[photoCount];
            System.arraycopy(photoSizes, 0, selectedSizes, 0, photoCount);
            previewLayout = layoutWithFrame(makeLayout(selectedSizes), frame, frameWidthScale);
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

    private void export(Uri destination, int mode) {
        busy = true;
        saveButton.setEnabled(false);
        printButton.setEnabled(false);
        detailButton.setEnabled(false);
        countSelector.setEnabled(false);
        recipientSelector.setEnabled(false);
        boolean reducedSize = mode != SAVE_PNG;
        status.setText(reducedSize ? "Đang ghép PNG cỡ in…" : "Đang ghép ảnh nguyên kích thước…");
        List<Uri> selected = new ArrayList<>(photos);
        Uri selectedFrame = frame;
        float selectedFrameWidth = frameWidthScale;
        String selectedRecipient = recipientName;
        PhotoEdit[] selectedEdits = new PhotoEdit[selected.size()];
        for (int i = 0; i < selected.size(); i++) selectedEdits[i] = edits[i].copy();
        worker.execute(() -> {
            long started = SystemClock.elapsedRealtime();
            try {
                Layout layout = layoutWithFrame(inspect(selected), selectedFrame, selectedFrameWidth);
                int outputWidth = mode == SAVE_DETAIL_PNG ? DETAIL_WIDTH_PX : PRINT_WIDTH_PX;
                int outputHeight = mode == SAVE_DETAIL_PNG ? DETAIL_HEIGHT_PX : PRINT_HEIGHT_PX;
                Layout outputLayout = reducedSize
                        ? evenLayout(outputWidth, outputHeight, layout.colWidths.length,
                                layout.rowHeights.length) : layout;
                if (reducedSize)
                    outputLayout = layoutWithFrame(outputLayout, selectedFrame, selectedFrameWidth);
                RenderStats stats;
                try (OutputStream stream = getContentResolver().openOutputStream(destination, "w")) {
                    if (stream == null) throw new IOException("Không mở được tệp PNG để ghi.");
                    stats = renderTiled(selected, selectedFrame, outputLayout, selectedEdits,
                            selectedFrameWidth, reducedSize, selectedRecipient, stream);
                }
                long total = SystemClock.elapsedRealtime() - started;
                String summary = String.format(Locale.getDefault(),
                        "Đã lưu PNG %d × %d px · Tổng %.1f giây\n"
                                + "Chuẩn bị %.1f · Ghép ảnh %.1f · Khung %.1f · Nén/ghi PNG %.1f giây",
                        outputLayout.width, outputLayout.height, total / 1000.0, stats.setupMs / 1000.0,
                        stats.photoMs / 1000.0, stats.frameMs / 1000.0, stats.pngMs / 1000.0);
                runOnUiThread(() -> status.setText(summary));
            } catch (Exception | OutOfMemoryError e) {
                runOnUiThread(() -> status.setText("Xuất ảnh thất bại: " + e.getMessage()));
            } finally {
                runOnUiThread(() -> {
                    busy = false;
                    saveButton.setEnabled(true);
                    printButton.setEnabled(true);
                    detailButton.setEnabled(true);
                    countSelector.setEnabled(true);
                    recipientSelector.setEnabled(true);
                });
            }
        });
    }

    private RenderStats renderTiled(List<Uri> selected, Uri selectedFrame, Layout layout,
                                    PhotoEdit[] selectedEdits, float selectedFrameWidth,
                                    boolean printSize, String recipient, OutputStream stream)
            throws IOException {
        RenderStats stats = new RenderStats();
        long setupStart = SystemClock.elapsedRealtime();
        Source[] sources = new Source[selected.size()];
        Source overlay = null;
        Bitmap fittedFrame = null;
        Bitmap tile = null;
        try {
            int contentWidth = printSize ? Math.round(layout.width * PRINT_CONTENT_SCALE) : layout.width;
            int contentHeight = printSize ? Math.round(layout.height * PRINT_CONTENT_SCALE) : layout.height;
            int insetX = (layout.width - contentWidth) / 2;
            int insetY = (layout.height - contentHeight) / 2;
            Layout contentLayout = printSize
                    ? layoutWithFrame(evenLayout(contentWidth, contentHeight,
                            layout.colWidths.length, layout.rowHeights.length),
                            selectedFrame, selectedFrameWidth)
                    : layout;
            for (int i = 0; i < selected.size(); i++) {
                sources[i] = new Source(selected.get(i), orientation(selected.get(i)));
            }
            if (selectedFrame != null) {
                Size frameSize = rawSize(selectedFrame);
                if (!printSize && selectedFrameWidth == 1f
                        && frameSize.width == layout.width && frameSize.height == layout.height)
                    overlay = new Source(selectedFrame, ExifInterface.ORIENTATION_NORMAL);
                else
                    fittedFrame = decode(selectedFrame, printSize ? 2400 : 0);
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
                    int contentTop = top - insetY;
                    canvas.drawColor(Color.WHITE);
                    canvas.save();
                    canvas.translate(insetX, 0);
                    canvas.clipRect(0, insetY - top, contentWidth,
                            insetY - top + contentHeight);
                    long stageStart = SystemClock.elapsedRealtime();
                    for (int i = 0; i < selected.size(); i++)
                        drawPhotoPart(canvas, sources[i], contentLayout, i,
                                selectedEdits[i], contentTop, rows, paint);
                    stats.photoMs += SystemClock.elapsedRealtime() - stageStart;
                    stageStart = SystemClock.elapsedRealtime();
                    if (overlay != null) drawPart(canvas, overlay, contentTop, rows, paint);
                    if (fittedFrame != null) {
                        frameTransform.setScale(contentWidth * selectedFrameWidth / fittedFrame.getWidth(),
                                contentHeight / (float) fittedFrame.getHeight());
                        frameTransform.postTranslate(contentWidth * (1f - selectedFrameWidth) / 2f,
                                -contentTop);
                        canvas.drawBitmap(fittedFrame, frameTransform, framePaint);
                    }
                    drawRecipientMessage(canvas, contentLayout, contentTop, rows, recipient);
                    canvas.restore();
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

    private void drawRecipientMessage(Canvas canvas, Layout layout, int top, int rows, String recipient) {
        if (recipient == null || recipient.isEmpty()) return;
        float footerTop = layout.height * 1437f / 1536f;
        float footerBottom = layout.height * 1468f / 1536f;
        if (top >= footerBottom || top + rows <= footerTop) return;

        canvas.save();
        canvas.translate(0, -top);
        canvas.scale(layout.width / 1024f, layout.height / 1536f);
        Paint lettering = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        lettering.setColor(Color.rgb(234, 194, 120));
        lettering.setTypeface(Typeface.create("serif", Typeface.ITALIC));
        lettering.setTextAlign(Paint.Align.CENTER);
        lettering.setShadowLayer(1.8f, 0, 1, Color.rgb(16, 12, 8));
        lettering.setTextSize(19f);
        String message = "tri ân " + recipient + " đã đến dự lễ tốt nghiệp của mình";
        float width = lettering.measureText(message);
        if (width > 650f) lettering.setTextSize(19f * 650f / width);
        canvas.drawText(message, 512, 1458, lettering);
        canvas.restore();
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
        if (layout.windows != null) return layout.windows[index];
        int cols = layout.colWidths.length;
        int col = index % cols, row = index / cols;
        int left = 0, top = 0;
        for (int i = 0; i < col; i++) left += layout.colWidths[i];
        for (int i = 0; i < row; i++) top += layout.rowHeights[i];
        return new Rect(left, top, left + layout.colWidths[col], top + layout.rowHeights[row]);
    }

    private int cellAt(Layout layout, float x, float y) {
        if (layout.windows != null) {
            int nearest = 0;
            float nearestDistance = Float.MAX_VALUE;
            for (int i = 0; i < layout.windows.length; i++) {
                Rect cell = layout.windows[i];
                if (cell.contains((int) x, (int) y)) return i;
                float dx = x - cell.exactCenterX(), dy = y - cell.exactCenterY();
                float distance = dx * dx + dy * dy;
                if (distance < nearestDistance) { nearestDistance = distance; nearest = i; }
            }
            return nearest;
        }
        int col = 0, row = 0;
        int edge = layout.colWidths[0];
        while (col + 1 < layout.colWidths.length && x >= edge)
            edge += layout.colWidths[++col];
        edge = layout.rowHeights[0];
        while (row + 1 < layout.rowHeights.length && y >= edge)
            edge += layout.rowHeights[++row];
        return row * layout.colWidths.length + col;
    }

    private Matrix photoMatrix(Layout layout, int index, int width, int height, PhotoEdit edit) {
        Rect cell = cellRect(layout, index);
        float fit = Math.max(cell.width() / (float) width, cell.height() / (float) height);
        Matrix matrix = new Matrix();
        matrix.setTranslate(-width / 2f, -height / 2f);
        matrix.postScale(fit * edit.zoom, fit * edit.zoom);
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
        Size[] sizes = new Size[selected.size()];
        for (int i = 0; i < selected.size(); i++) sizes[i] = orientedSize(selected.get(i));
        return makeLayout(sizes);
    }

    private Layout makeLayout(Size[] sizes) throws IOException {
        int cols = sizes.length <= 2 ? 1 : 2;
        int rows = sizes.length / cols;
        int[] colWidths = new int[cols];
        int[] rowHeights = new int[rows];
        for (int i = 0; i < sizes.length; i++) {
            colWidths[i % cols] = Math.max(colWidths[i % cols], sizes[i].width);
            rowHeights[i / cols] = Math.max(rowHeights[i / cols], sizes[i].height);
        }
        long width = 0, height = 0;
        for (int value : colWidths) width += value;
        for (int value : rowHeights) height += value;
        if (width * PRINT_HEIGHT_UNITS < height * PRINT_WIDTH_UNITS)
            width = (height * PRINT_WIDTH_UNITS + PRINT_HEIGHT_UNITS - 1) / PRINT_HEIGHT_UNITS;
        else
            height = (width * PRINT_HEIGHT_UNITS + PRINT_WIDTH_UNITS - 1) / PRINT_WIDTH_UNITS;
        if (width > Integer.MAX_VALUE || height > Integer.MAX_VALUE)
            throw new IOException("Kích thước ảnh vượt giới hạn định dạng Android.");
        return evenLayout((int) width, (int) height, cols, rows);
    }

    private Layout evenLayout(int width, int height, int cols, int rows) {
        int[] colWidths = new int[cols];
        int[] rowHeights = new int[rows];
        for (int col = 0; col < cols; col++)
            colWidths[col] = width / cols + (col < width % cols ? 1 : 0);
        for (int row = 0; row < rows; row++)
            rowHeights[row] = height / rows + (row < height % rows ? 1 : 0);
        return new Layout(width, height, colWidths, rowHeights);
    }

    private Layout layoutWithFrame(Layout layout, Uri selectedFrame, float frameScale) {
        if (selectedFrame == null || !selectedFrame.equals(builtInFrameUri(
                layout.colWidths.length * layout.rowHeights.length))) return layout;
        int count = layout.colWidths.length * layout.rowHeights.length;
        Rect[] sourceWindows = builtInWindows(count);
        Rect[] windows = new Rect[count];
        for (int i = 0; i < count; i++) {
            Rect source = sourceWindows[i];
            float scaledLeft = layout.width * ((1f - frameScale) / 2f
                    + frameScale * source.left / 1024f);
            float scaledRight = layout.width * ((1f - frameScale) / 2f
                    + frameScale * source.right / 1024f);
            int left = Math.max(0, Math.round(scaledLeft));
            int right = Math.min(layout.width, Math.round(scaledRight));
            int top = Math.max(0, Math.round(layout.height * source.top / 1536f));
            int bottom = Math.min(layout.height,
                    Math.round(layout.height * source.bottom / 1536f));
            windows[i] = new Rect(left, top, right, bottom);
        }
        return new Layout(layout.width, layout.height, layout.colWidths, layout.rowHeights, windows);
    }

    private Rect[] builtInWindows(int count) {
        if (count == 1) return new Rect[]{new Rect(82, 215, 941, 1334)};
        if (count == 2) return new Rect[]{
                new Rect(81, 213, 942, 755), new Rect(81, 793, 942, 1333)};
        if (count == 8) return new Rect[]{
                new Rect(81, 213, 493, 476), new Rect(530, 213, 943, 476),
                new Rect(80, 510, 493, 755), new Rect(530, 510, 943, 755),
                new Rect(81, 792, 493, 1035), new Rect(530, 792, 943, 1035),
                new Rect(81, 1070, 493, 1334), new Rect(530, 1070, 943, 1334)};
        return new Rect[]{
                new Rect(80, 213, 493, 755), new Rect(529, 213, 942, 755),
                new Rect(80, 793, 493, 1335), new Rect(529, 793, 942, 1335)};
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
            for (int i = 0; i < photoCount; i++) {
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
            drawRecipientMessage(canvas, layout, 0, layout.height, recipientName);
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
                selectedPhoto = cellAt(previewLayout, x, y);
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
        final Rect[] windows;
        Layout(int width, int height, int[] colWidths, int[] rowHeights) {
            this(width, height, colWidths, rowHeights, null);
        }
        Layout(int width, int height, int[] colWidths, int[] rowHeights, Rect[] windows) {
            this.width = width; this.height = height;
            this.colWidths = colWidths; this.rowHeights = rowHeights;
            this.windows = windows;
        }
    }
}
