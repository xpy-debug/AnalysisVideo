package com.example.server.utils;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OcrUtilsTest {

    /**
     * 指向一个必定不存在的 tessdata 目录，避免回退路径在本机装了系统 Tesseract 与语言包时意外成功。
     *
     * <p>回退失败本身是由 {@link #tempImage()} 的非法图片内容保证的（Tess4J 在解码阶段就会失败），
     * 与机器上有没有原生库和语言包无关，因此断言是确定性的。
     */
    private static final String MISSING_TESSDATA = Path
            .of(System.getProperty("java.io.tmpdir"), "dovideo-missing-tessdata-" + UUID.randomUUID())
            .toString();

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void returnsPaddleTextAndTrims() throws Exception {
        String url = startServer(200, "[{\"rec_texts\":[\"  识别结果  \"]}]");

        assertEquals("识别结果", paddleOnly(url, true).recognize(tempImage()));
    }

    @Test
    void emptyResultIsValidAndDoesNotFallBack() throws Exception {
        String url = startServer(200, "[]");

        // 回退所需的 tessdata 不存在：若空结果被当成失败触发回退，这里会抛异常
        assertEquals("", paddleOnly(url, true).recognize(tempImage()));
    }

    @Test
    void sendsBase64JsonBody() throws Exception {
        AtomicReference<String> captured = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ocr", exchange -> {
            captured.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, "[]");
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/ocr";

        paddleOnly(url, true).recognize(tempImage());

        assertTrue(captured.get().contains("\"file\":\""), captured.get());
    }

    @Test
    void serviceErrorFallsBackWhenEnabled() throws Exception {
        String url = startServer(500, "{}");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> paddleOnly(url, true).recognize(tempImage()));
        assertTrue(error.getMessage().startsWith("OCR failed for"), error.getMessage());
    }

    @Test
    void unavailableServiceThrowsWhenFallbackDisabled() throws Exception {
        String url = startServer(503, "{}");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> paddleOnly(url, false).recognize(tempImage()));
        assertTrue(error.getMessage().contains("PaddleOCR 服务不可用"), error.getMessage());
    }

    @Test
    void unreachableServiceThrowsWhenFallbackDisabled() throws Exception {
        String url = "http://127.0.0.1:" + freePort() + "/ocr";

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> paddleOnly(url, false).recognize(tempImage()));
        assertTrue(error.getMessage().contains("PaddleOCR 服务不可用"), error.getMessage());
    }

    @Test
    void blankUrlUsesLocalEngineDirectly() throws Exception {
        // 未配置 PaddleOCR 地址时保持旧行为：直接调用进程内 Tesseract
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new OcrUtils("", 5, MISSING_TESSDATA, "chi_sim+eng", true).recognize(tempImage()));
        assertTrue(error.getMessage().startsWith("OCR failed for"), error.getMessage());
    }

    /** PaddleOCR 命中或失败时都不应真的跑起 Tesseract（语言包目录故意不存在）。 */
    private static OcrUtils paddleOnly(String url, boolean fallbackEnabled) {
        return new OcrUtils(url, 5, MISSING_TESSDATA, "chi_sim+eng", fallbackEnabled);
    }

    /**
     * 真正跑一次进程内 Tesseract：放一张画有文字的图，断言能识别出来。
     *
     * <p>语言包直接取 tess4j jar 自带的 eng.traineddata（见 {@link #bundledEngTessdata()}），
     * 这样测试不依赖开发机是否装了 Tesseract。原生库同理只在 Windows 上随 jar 提供，
     * 其他平台缺 libtesseract 时用 assumption 跳过，而不是让构建失败。
     */
    @Test
    void recognizesRenderedTextWithLocalEngine() throws Exception {
        Path tessdata = bundledEngTessdata();
        Assumptions.assumeTrue(tessdata != null, "tess4j 未在 classpath 提供 eng.traineddata");

        File image = renderedTextImage("HELLO WORLD");

        String text;
        try {
            // 空 URL 强制走进程内引擎
            text = new OcrUtils("", 5, tessdata.toString(), "eng", true).recognize(image);
        } catch (IllegalStateException e) {
            Assumptions.abort("本平台没有可用的 Tesseract 原生库：" + e.getMessage());
            return;
        }

        assertTrue(text.replaceAll("\\s+", "").toUpperCase().contains("HELLOWORLD"),
                "未识别出预期文本，实际输出：" + text);
    }

    /**
     * 中文识别走的是 application.properties 里的默认语言配置，因此单独验一遍：
     * 英文能读出来不代表 chi_sim 语言包装对了。
     *
     * <p>语言包不在仓库里（见 .gitignore），没有就跳过；README 有下载命令。
     */
    @Test
    void recognizesChineseWithConfiguredTessdata() throws Exception {
        Path tessdata = Path.of(System.getProperty("user.dir"), "tessdata");
        Assumptions.assumeTrue(Files.isRegularFile(tessdata.resolve("chi_sim.traineddata")),
                "缺少语言包，跳过：" + tessdata);

        File image = renderedChineseImage("视频分析 关键帧 文字识别");

        String text = new OcrUtils("", 30, tessdata.toString(), "chi_sim+eng", true).recognize(image);

        assertTrue(text.replaceAll("\\s+", "").contains("视频分析"),
                "未识别出预期中文，实际输出：" + text);
    }

    /** 挑一个能显示中文的字体，避免依赖运行机器恰好装了什么字体。 */
    private static Font chineseFont(int size) {
        for (String name : new String[]{"Microsoft YaHei", "SimHei", "SimSun", "MS Gothic", Font.DIALOG}) {
            Font font = new Font(name, Font.BOLD, size);
            if (font.canDisplay('视')) return font;
        }
        return new Font(Font.SANS_SERIF, Font.BOLD, size);
    }

    private static File renderedChineseImage(String text) throws IOException {
        return writeImage(text, chineseFont(64), 900);
    }

    /** 把画有文字的图片渲染成临时 PNG，作为真实 OCR 输入。 */
    static File renderedTextImage(String text) throws IOException {
        return writeImage(text, new Font(Font.SANS_SERIF, Font.BOLD, 64), 640);
    }

    private static File writeImage(String text, Font font, int width) throws IOException {
        BufferedImage image = new BufferedImage(width, 170, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setColor(Color.BLACK);
            graphics.setFont(font);
            graphics.drawString(text, 30, 110);
        } finally {
            graphics.dispose();
        }

        Path path = Files.createTempFile("dovideo-ocr-render-", ".png");
        ImageIO.write(image, "png", path.toFile());
        path.toFile().deleteOnExit();
        return path.toFile();
    }

    /** 从 tess4j jar 里取出 eng.traineddata 到临时目录；jar 不在 classpath 时返回 null。 */
    static Path bundledEngTessdata() throws IOException {
        try (InputStream bundled = OcrUtilsTest.class.getResourceAsStream("/tessdata/eng.traineddata")) {
            if (bundled == null) return null;
            Path tessdata = Files.createTempDirectory("dovideo-tessdata-");
            Files.copy(bundled, tessdata.resolve("eng.traineddata"));
            return tessdata;
        }
    }

    private String startServer(int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ocr", exchange -> respond(exchange, status, body));
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/ocr";
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, payload.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(payload);
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static File tempImage() throws IOException {
        Path path = Files.createTempFile("dovideo-ocr-test-", ".jpg");
        Files.write(path, new byte[]{1, 2, 3});
        path.toFile().deleteOnExit();
        return path.toFile();
    }
}
