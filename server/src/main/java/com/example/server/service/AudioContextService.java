package com.example.server.service;

import com.example.server.dto.TranscriptSegment;
import com.example.server.dto.VideoContext;
import com.example.server.utils.MinioUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 音频上下文构建:只跑 ASR,不抽帧、不做 OCR。
 *
 * <p>产出与视频链路同构的 {@link VideoContext}:每个片段只带 transcript,
 * {@code ocrTexts}/{@code evidenceFrames} 恒为空列表。因此下游的 AgentLoop、DeepSeekUtils、
 * 长内容检索与 checkpoint 可以原样复用,无需为音频新增数据结构。
 */
@Service
public class AudioContextService {

    private static final Logger log = LoggerFactory.getLogger(AudioContextService.class);

    private final SegmentedTranscriptionService transcriptionService;
    private final MinioUtils minioUtils;

    public AudioContextService(SegmentedTranscriptionService transcriptionService,
                               MinioUtils minioUtils) {
        this.transcriptionService = transcriptionService;
        this.minioUtils = minioUtils;
    }

    public VideoContext build(String audioPath, String userGoal, String traceId) {
        String readableAudioPath = minioUtils.readableSource(audioPath);
        Path workDir = Path.of(System.getProperty("java.io.tmpdir"), "audio-context-" + UUID.randomUUID());
        try {
            Files.createDirectories(workDir);
            // SegmentedTranscriptionService 的 ffmpeg 走 -vn 抽音轨,对纯音频输入同样有效。
            List<TranscriptSegment> transcripts = transcriptionService.transcribe(
                    readableAudioPath, workDir.resolve("audio"), traceId);
            List<VideoContext.VideoSegment> segments = transcripts.stream()
                    .map(segment -> new VideoContext.VideoSegment(
                            segment.startMs(), segment.endMs(), segment.text(), List.of(), List.of()))
                    .toList();
            if (segments.isEmpty()) throw new IllegalStateException("音频未解析出有效语音");
            return new VideoContext(audioPath, userGoal, segments);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("音频上下文构建失败", e);
        } finally {
            deleteDirectory(workDir);
        }
    }

    private void deleteDirectory(Path directory) {
        if (!Files.exists(directory)) return;
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception e) {
                    log.warn("audio_context_cleanup_failed path={}", path, e);
                }
            });
        } catch (Exception e) {
            log.warn("audio_context_cleanup_failed dir={}", directory, e);
        }
    }
}
