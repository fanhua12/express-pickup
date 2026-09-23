package com.pickup.assistant.ocr;

import android.content.Context;
import android.net.Uri;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import com.google.mlkit.vision.text.TextRecognizer;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 截图 OCR: ML Kit 离线中文模型(随 APK 打包), 识别全程不联网
 * 把截图里所有文字按行拼成文本, 交给 Ingestor 走与短信/通知同一套解析
 */
public final class OcrManager {

    public interface Callback {
        void onResult(String text, String error);
    }

    private static volatile TextRecognizer recognizer;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    private OcrManager() {}

    private static TextRecognizer client() {
        if (recognizer == null) {
            synchronized (OcrManager.class) {
                if (recognizer == null) {
                    recognizer = TextRecognition.getClient(
                            new ChineseTextRecognizerOptions.Builder().build());
                }
            }
        }
        return recognizer;
    }

    /** 批量识别: 顺序处理多张截图, 单张失败跳过, 最终把文字合并成一段 */
    public static void recognizeAll(final Context ctx, final java.util.List<Uri> uris, final Callback cb) {
        IO.execute(() -> {
            StringBuilder all = new StringBuilder();
            String lastError = null;
            for (Uri uri : uris) {
                try {
                    InputImage image = InputImage.fromFilePath(ctx, uri);
                    Text vision = com.google.android.gms.tasks.Tasks.await(
                            client().process(image), 60, java.util.concurrent.TimeUnit.SECONDS);
                    all.append(join(vision)).append('\n');
                } catch (Exception e) {
                    lastError = e.getMessage() == null ? "识别失败" : e.getMessage();
                }
            }
            if (all.length() == 0 && lastError != null) {
                cb.onResult(null, lastError);
                return;
            }
            cb.onResult(all.toString(), null);
        });
    }

    private static String join(Text vision) {
        java.util.List<Text.Line> lines = new java.util.ArrayList<>();
        for (Text.TextBlock block : vision.getTextBlocks()) {
            for (Text.Line line : block.getLines()) lines.add(line);
        }
        lines.sort((a, b) -> {
            android.graphics.Rect ra = a.getBoundingBox();
            android.graphics.Rect rb = b.getBoundingBox();
            int ya = ra == null ? 0 : ra.top;
            int yb = rb == null ? 0 : rb.top;
            if (Math.abs(ya - yb) > 12) return ya - yb;
            return (ra == null ? 0 : ra.left) - (rb == null ? 0 : rb.left);
        });
        StringBuilder sb = new StringBuilder();
        for (Text.Line line : lines) sb.append(line.getText()).append('\n');
        return sb.toString();
    }
}
