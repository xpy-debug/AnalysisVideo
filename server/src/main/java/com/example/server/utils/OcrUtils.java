package com.example.server.utils;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 关键帧 OCR。首选常驻的 PaddleOCR 服务（HTTP），服务不可用时按配置回退到进程内的 Tesseract。
 *
 * 回退只针对"服务可用性"层面的失败（连接失败、超时、非 2xx、响应无法解析）；
 * 服务正常返回但文本为空，是"这张图没有文字"的有效结果，不做降级。
 *
 * Tesseract 通过 Tess4J 的 JNA 绑定在本进程内调用，不再 fork 外部命令：Windows 的原生库由
 * tess4j 依赖自带，Linux 需要 libtesseract，且两者都必须自备 tessdata 语言包（见 tool.ocr.data-path）。
 */
@Component
public class OcrUtils {

    private static final Logger log = LoggerFactory.getLogger(OcrUtils.class);
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json");
    private static final int CIRCUIT_FAILURE_THRESHOLD = 3;
    private static final long CIRCUIT_COOLDOWN_MS = 60_000L;
    private static final String DEFAULT_LANGUAGES = "chi_sim+eng";

    private final String paddleUrl;
    private final String tessDataPath;
    private final String tesseractLanguages;
    private final boolean fallbackEnabled;
    private final OkHttpClient client;

    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private volatile long circuitOpenUntil = 0L;

    public OcrUtils(@Value("${tool.ocr.url:}") String paddleUrl,
                    @Value("${tool.ocr.timeout-seconds:60}") long timeoutSeconds,
                    @Value("${tool.ocr.data-path:}") String tessDataPath,
                    @Value("${tool.ocr.languages:}") String tesseractLanguages,
                    @Value("${tool.ocr.fallback-enabled:true}") boolean fallbackEnabled) {
        if (timeoutSeconds < 1) throw new IllegalArgumentException("OCR 超时时间必须大于 0");
        this.paddleUrl = paddleUrl == null ? "" : paddleUrl.trim();
        this.tessDataPath = tessDataPath == null ? "" : tessDataPath.trim();
        this.tesseractLanguages = tesseractLanguages == null || tesseractLanguages.isBlank()
                ? DEFAULT_LANGUAGES
                : tesseractLanguages.trim();
        this.fallbackEnabled = fallbackEnabled;
        this.client = new OkHttpClient.Builder()
                // 连接超时保持很短：服务挂掉时快速失败，不拖慢整批关键帧
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    public String recognize(File image) {
        if (image == null || !image.isFile()) throw new IllegalArgumentException("OCR image does not exist");

        if (paddleConfigured() && !circuitOpen()) {
            try {
                String text = recognizeWithPaddle(image);
                consecutiveFailures.set(0);
                return text;
            } catch (OcrServiceUnavailableException e) {
                recordPaddleFailure();
                log.warn("paddleocr_unavailable frame={} fallbackEnabled={}",
                        image.getName(), fallbackEnabled, e);
                if (!fallbackEnabled) throw new IllegalStateException("PaddleOCR 服务不可用", e);
            }
        } else if (paddleConfigured() && !fallbackEnabled) {
            throw new IllegalStateException("PaddleOCR 服务熔断中且未启用回退");
        }

        return recognizeWithTesseract(image);
    }

    private String recognizeWithPaddle(File image) {
        // OCR 服务（jarvis1tube/paddleocr-server）约定：POST JSON，图片以 base64 放在 file 字段；
        // 识别语言由服务端 OCR_LANG 环境变量决定，不在请求里传。
        String payload;
        try {
            String base64 = Base64.getEncoder().encodeToString(Files.readAllBytes(image.toPath()));
            payload = "{\"file\":\"" + base64 + "\"}";
        } catch (IOException e) {
            throw new OcrServiceUnavailableException("OCR 图片读取失败", e);
        }

        Request request = new Request.Builder()
                .url(paddleUrl)
                .post(RequestBody.create(payload, JSON_MEDIA_TYPE))
                .build();

        try (Response response = client.newCall(request).execute()) {
            String body = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new OcrServiceUnavailableException("PaddleOCR HTTP " + response.code());
            }
            return extractText(body);
        } catch (IOException e) {
            throw new OcrServiceUnavailableException("PaddleOCR 调用失败", e);
        }
    }

    /** 响应是数组，逐项取 rec_texts 按行拼接；空数组表示该图无文字，是有效结果。 */
    private String extractText(String body) {
        JSONArray results;
        try {
            results = JSON.parseArray(body);
        } catch (RuntimeException e) {
            throw new OcrServiceUnavailableException("PaddleOCR 响应无法解析", e);
        }
        if (results == null || results.isEmpty()) return "";

        List<String> lines = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            JSONObject item = results.getJSONObject(i);
            JSONArray texts = item == null ? null : item.getJSONArray("rec_texts");
            if (texts == null) continue;
            for (int j = 0; j < texts.size(); j++) {
                String text = texts.getString(j);
                if (text != null && !text.isBlank()) lines.add(text.trim());
            }
        }
        return String.join("\n", lines);
    }

    /**
     * 进程内调用 Tesseract。
     *
     * <p>Tess4J 的 {@code doOCR} 每次都会 init 一个引擎句柄并在 finally 里 dispose，复用实例并不能省下
     * 语言包加载开销，因此这里按调用新建，不共享状态，也就不需要额外加锁来保证线程安全。
     *
     * <p>链接错误必须与 TesseractException 一并捕获：JNA 找不到原生库时抛的是
     * {@code UnsatisfiedLinkError}（LinkageError 子类），属于 Error 而非 Exception。漏掉它会让整条
     * OCR 分支直接崩溃，而不是按设计只在单帧上降级失败。
     */
    private String recognizeWithTesseract(File image) {
        try {
            Tesseract engine = new Tesseract();
            if (!tessDataPath.isBlank()) engine.setDatapath(tessDataPath);
            engine.setLanguage(tesseractLanguages);
            // rects 传 null 表示识别整张图
            return engine.doOCR(image, (List<Rectangle>) null).trim();
        } catch (TesseractException | LinkageError e) {
            throw new IllegalStateException("OCR failed for " + image.getName(), e);
        }
    }

    private boolean paddleConfigured() {
        return !paddleUrl.isBlank();
    }

    private boolean circuitOpen() {
        return System.currentTimeMillis() < circuitOpenUntil;
    }

    private void recordPaddleFailure() {
        if (consecutiveFailures.incrementAndGet() >= CIRCUIT_FAILURE_THRESHOLD) {
            consecutiveFailures.set(0);
            circuitOpenUntil = System.currentTimeMillis() + CIRCUIT_COOLDOWN_MS;
            log.warn("paddleocr_circuit_opened cooldownMs={}", CIRCUIT_COOLDOWN_MS);
        }
    }

    private static class OcrServiceUnavailableException extends RuntimeException {
        private OcrServiceUnavailableException(String message) {
            super(message);
        }

        private OcrServiceUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
