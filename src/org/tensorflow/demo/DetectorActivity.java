/*
 * Copyright 2016 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tensorflow.demo;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.Image;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Environment;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.util.Size;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Display;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;

import org.tensorflow.demo.OverlayView.DrawCallback;
import org.tensorflow.demo.env.BorderedText;
import org.tensorflow.demo.env.ImageUtils;
import org.tensorflow.demo.env.Logger;
import org.tensorflow.demo.model.BienSo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;

public class DetectorActivity extends CameraActivity implements OnImageAvailableListener {

    private static final Logger LOGGER = new Logger();

    private static final int TF_OD_API_INPUT_SIZE = 300;
    private static final String TF_OD_API_MODEL_FILE =
            "file:///android_asset/biensoxemay1.pb";
    private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/biensoxemay.txt";
    private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.85f;
    private static final boolean MAINTAIN_ASPECT = false;
    private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);
    private static final boolean SAVE_PREVIEW_BITMAP = false;
    private static final float TEXT_SIZE_DIP = 10;
    private static final int[] COLORS = {
            Color.BLUE, Color.RED, Color.GREEN, Color.YELLOW, Color.CYAN, Color.MAGENTA, Color.WHITE,
            Color.parseColor("#55FF55"), Color.parseColor("#FFA500"), Color.parseColor("#FF8888"),
            Color.parseColor("#AAAAFF"), Color.parseColor("#FFFFAA"), Color.parseColor("#55AAAA"),
            Color.parseColor("#AA33AA"), Color.parseColor("#0D0068")
    };
    private final Paint boxPaint = new Paint();
    private Integer sensorOrientation;
    private Classifier detector;
    private int previewWidth = 0;
    private int previewHeight = 0;
    private Bitmap rgbFrameBitmap = null;
    private Bitmap croppedBitmap = null;
    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;
    private Bitmap cropCopyBitmap;
    private BorderedText borderedText;
    private long lastProcessingTimeMs;
    private OverlayView detectionOverlay;
    private List<Classifier.Recognition> mappedRecognitions =
            new LinkedList<>();
    private String textw;

    @Override
    public void onPreviewSizeChosen(final Size size, final int rotation) {
        final float textSizePx =
                TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
        borderedText = new BorderedText(textSizePx);
        borderedText.setTypeface(Typeface.MONOSPACE);

        try {
            detector = TensorFlowObjectDetectionAPIModel.create(
                    getAssets(), TF_OD_API_MODEL_FILE, TF_OD_API_LABELS_FILE, TF_OD_API_INPUT_SIZE);
        } catch (final IOException e) {
            LOGGER.e("Exception initializing classifier!", e);
            Toast toast =
                    Toast.makeText(
                            getApplicationContext(), "Classifier could not be initialized", Toast.LENGTH_SHORT);
            toast.show();
            finish();
        }

        previewWidth = size.getWidth();
        previewHeight = size.getHeight();

        final Display display = getWindowManager().getDefaultDisplay();
        final int screenOrientation = display.getRotation();

        LOGGER.i("Sensor orientation: %d, Screen orientation: %d", rotation, screenOrientation);

        sensorOrientation = rotation + screenOrientation;

        LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
        croppedBitmap = Bitmap.createBitmap(TF_OD_API_INPUT_SIZE, TF_OD_API_INPUT_SIZE, Config.ARGB_8888);

        frameToCropTransform =
                ImageUtils.getTransformationMatrix(
                        previewWidth, previewHeight,
                        TF_OD_API_INPUT_SIZE, TF_OD_API_INPUT_SIZE,
                        sensorOrientation, MAINTAIN_ASPECT);

        cropToFrameTransform = new Matrix();
        frameToCropTransform.invert(cropToFrameTransform);

        addCallback(
                new DrawCallback() {
                    @Override
                    public void drawCallback(final Canvas canvas) {
                        renderDebug(canvas);
                    }
                });

        boxPaint.setColor(Color.RED);
        boxPaint.setStyle(Style.STROKE);
        boxPaint.setStrokeWidth(8.0f);
        detectionOverlay = findViewById(R.id.detection_overlay);
        detectionOverlay.addCallback(
                new DrawCallback() {
                    @Override
                    public void drawCallback(final Canvas canvas) {
                        renderOverlay(canvas);
                    }
                });
    }

    private ImageView iv;

    protected void processImageRGBbytes(int[] rgbBytes) {
        rgbFrameBitmap.setPixels(rgbBytes, 0, previewWidth, 0, 0, previewWidth, previewHeight);
        final Canvas canvas = new Canvas(croppedBitmap);
        canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);

        // For examining the actual TF input.
        if (SAVE_PREVIEW_BITMAP) {
            ImageUtils.saveBitmap(croppedBitmap);
        }

        runInBackground(
                new Runnable() {
                    @Override
                    public void run() {
                        final long startTime = SystemClock.uptimeMillis();
                        final List<Classifier.Recognition> results = detector.recognizeImage(croppedBitmap);
                        lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;
                        LOGGER.i("Detect: %s", results);
                        cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
                        final Canvas canvas = new Canvas(cropCopyBitmap);
                        final Paint paint = new Paint();
                        paint.setColor(Color.RED);
                        paint.setStyle(Style.STROKE);
                        paint.setStrokeWidth(2.0f);

                        mappedRecognitions.clear();
                        bienSos.clear();
                        for (final Classifier.Recognition result : results) {
                            final RectF location = result.getLocation();
                            if (location != null && result.getConfidence() >= MINIMUM_CONFIDENCE_TF_OD_API) {

                                canvas.drawRect(location, paint);
                                cropToFrameTransform.mapRect(location);
                                result.setLocation(location);
                                mappedRecognitions.add(result);
                                final int x = (int) location.left;
                                final int y = (int) location.top;
                                final int w = (int) location.right - (int) location.left;
                                final int h = (int) location.bottom - (int) location.top;


                                try {
//                                    bitmap2=ImageUtils.saveBitmap();saveBitmap(cutBitmap(rgbFrameBitmap, x, y, w, h));
                                    bitmap2 = saveBitmap(cutBitmap(rgbFrameBitmap, x, y, w, h), System.currentTimeMillis() + "");
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                                int flag = -1;
                                for (BienSo bienSo : bienSos) {
                                    if (Integer.valueOf(result.getId()) == bienSo.getId()) {
                                        flag = Integer.valueOf(result.getId());
                                    }
                                }
                                if (flag == -1) {
                                    if(result.getConfidence()>=0.999f){
                                        textw = processImage(bitmap2);
                                    }
                                    bienSos.add(new BienSo(Integer.valueOf(result.getId()), bitmap2));
                                } else {
                                    bienSos.remove(flag);
                                    bienSos.add(flag, new BienSo(flag, bitmap2));
                                }


                            }
                        }
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (!bienSos.isEmpty()) {
                                    bienSoAdapter.notifyDataSetChanged();
                                }
                                if (textw != "") {
                                    tvBienSo.setText(textw);
                                }

                            }
                        });

                        requestRender();
                        detectionOverlay.postInvalidate();
                        computing = false;
                        if (postInferenceCallback != null) {
                            postInferenceCallback.run();
                        }
                    }
                });
    }

    private String processImage(Bitmap bitmap) {
        String t = "not operational";
        TextRecognizer textRecognizer = new TextRecognizer.Builder(getApplicationContext()).build();
        if (textRecognizer.isOperational()) {
            Frame frame = new Frame.Builder().setBitmap(bitmap).build();

            SparseArray<TextBlock> items = textRecognizer.detect(frame);
            StringBuilder stringBuilder = new StringBuilder();

            for (int i = 0; i < items.size(); i++) {
                TextBlock textBlock = items.valueAt(i);
                stringBuilder.append(textBlock.getValue());
                stringBuilder.append("\n");
            }
            t = stringBuilder.toString();
        } else {
//            Log.d(TAG, "processImage: ");
        }
        return t;
    }

    private Bitmap saveBitmap(Bitmap bitmap, String filename) throws Exception {
        Matrix matrix = new Matrix();
        matrix.postRotate(90);
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        final String root =
                Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "tensorflow";
        final File myDir = new File(root);

        if (!myDir.mkdirs()) {
            LOGGER.i("Make dir failed");
        }
        final File file = new File(myDir, filename);
        if (file.exists()) {
            file.delete();
        }
        try {
            final FileOutputStream out = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 99, out);
            out.flush();
            out.close();
        } catch (final Exception e) {
            LOGGER.e(e, "Exception!");
        }
        return bitmap;
    }

    private Bitmap cutBitmap(Bitmap originalBitmap, int x, int y, int width, int height) {
        Bitmap cutBitmap = Bitmap.createBitmap(width,
                height, Bitmap.Config.ARGB_8888);
        Matrix matrix = new Matrix();
        matrix.postRotate(90);
        Canvas canvas = new Canvas(cutBitmap);
        Rect srcRect = new Rect(x, y, x + width, y + height);
        Rect desRect = new Rect(0, 0, width, height);
        canvas.drawBitmap(originalBitmap, srcRect, desRect, null);
        return cutBitmap;
    }

//    private Bitmap getBitMap(Bitmap cropCopyBitmap, RectF location) {
//        int targetWidth  = 300;
//        int targetHeight = 300;
//        Paint paint = new Paint();
//        paint.setFilterBitmap(true);
//        Bitmap targetBitmap = Bitmap.createBitmap(, targetHeight,Bitmap.Config.ARGB_8888);
//        Canvas canvas = new Canvas(targetBitmap);
//        Path path = new Path();
//        path.addRect(location, Path.Direction.CW);
//        canvas.clipPath(path);
//        canvas.drawBitmap( cropCopyBitmap, new Rect((int)location.left,(int) location.top, cropCopyBitmap.getWidth(), cropCopyBitmap.getHeight()),
//                new Rect(0, 0, targetWidth, targetHeight), paint);
//        Matrix matrix = new Matrix();
//        matrix.postScale(1f, 1f);
//        return Bitmap.createBitmap(targetBitmap, 0, 0, 100, 100, matrix, true);
//    }


    @Override
    protected int getLayoutId() {
        return R.layout.camera_connection_fragment_detection;
    }

    @Override
    protected Size getDesiredPreviewFrameSize() {
        return DESIRED_PREVIEW_SIZE;
    }

    @Override
    public void onSetDebug(final boolean debug) {
        detector.enableStatLogging(debug);
    }

    private void renderOverlay(final Canvas canvas) {
        final float multiplier =
                Math.min(canvas.getWidth() / (float) previewHeight, canvas.getHeight() / (float) previewWidth);
        Matrix frameToCanvasMatrix =
                ImageUtils.getTransformationMatrix(
                        previewWidth,
                        previewHeight,
                        (int) (multiplier * previewHeight),
                        (int) (multiplier * previewWidth),
                        sensorOrientation,
                        false);
        int count = 0;
        for (final Classifier.Recognition recognition : mappedRecognitions) {
            final RectF location = new RectF(recognition.getLocation());

            frameToCanvasMatrix.mapRect(location);
            boxPaint.setColor(COLORS[count]);
            count = (count + 1) % COLORS.length;

            final float cornerSize = Math.min(location.width(), location.height()) / 8.0f;
            canvas.drawRoundRect(location, cornerSize, cornerSize, boxPaint);

            final String labelString =
                    !TextUtils.isEmpty(recognition.getTitle())
                            ? String.format("%s %.2f", recognition.getTitle(), recognition.getConfidence())
                            : String.format("%.2f", recognition.getConfidence());
            borderedText.drawText(canvas, location.left + cornerSize, location.bottom, labelString);
        }
    }

    private void renderDebug(final Canvas canvas) {
        if (!isDebug()) {
            return;
        }
        final Bitmap copy = cropCopyBitmap;
        if (copy == null) {
            return;
        }

        final int backgroundColor = Color.argb(100, 0, 0, 0);
        canvas.drawColor(backgroundColor);

        final Matrix matrix = new Matrix();
        final float scaleFactor = 2;
        matrix.postScale(scaleFactor, scaleFactor);
        matrix.postTranslate(
                canvas.getWidth() - copy.getWidth() * scaleFactor,
                canvas.getHeight() - copy.getHeight() * scaleFactor);
        canvas.drawBitmap(copy, matrix, new Paint());

        final Vector<String> lines = new Vector<>();
        if (detector != null) {
            final String statString = detector.getStatString();
            final String[] statLines = statString.split("\n");
            Collections.addAll(lines, statLines);
        }
        lines.add("");

        lines.add("Frame: " + previewWidth + "x" + previewHeight);
        lines.add("Crop: " + copy.getWidth() + "x" + copy.getHeight());
        lines.add("View: " + canvas.getWidth() + "x" + canvas.getHeight());
        lines.add("Rotation: " + sensorOrientation);
        lines.add("Inference time: " + lastProcessingTimeMs + "ms");

        borderedText.drawLines(canvas, 10, canvas.getHeight() - 10, lines);
    }
}
