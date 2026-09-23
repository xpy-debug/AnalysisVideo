package com.example.server.service;

import com.example.server.dto.TranscriptSegment;
import com.example.server.dto.VideoContext;
import com.example.server.utils.MinioUtils;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudioContextServiceTest {

    private static final String AUDIO_SOURCE = "audio-source.mp3";

    /** 音频链路只跑 ASR,因此用一个只返回固定转写结果的存根替代真实 ASR 调用。 */
    private AudioContextService serviceWith(List<TranscriptSegment> segments) {
        SegmentedTranscriptionService stub = new SegmentedTranscriptionService(null, null) {
            @Override
            public List<TranscriptSegment> transcribe(String videoPath, Path audioDir, String traceId) {
                return segments;
            }
        };
        // bucketName/endpoint 与音频路径不匹配,readableSource 会原样返回,不会触碰 MinioClient。
        MinioUtils minioUtils = new MinioUtils(null, "media", "http://localhost:9000");
        return new AudioContextService(stub, minioUtils);
    }

    @Test
    void mapsTranscriptsToTranscriptOnlySegments() {
        AudioContextService service = serviceWith(List.of(
                new TranscriptSegment(0, 60_000, "第一段"),
                new TranscriptSegment(60_000, 120_000, "第二段")));

        VideoContext context = service.build(AUDIO_SOURCE, "总结", null);

        assertEquals(AUDIO_SOURCE, context.source());
        assertEquals("总结", context.userGoal());
        assertEquals(2, context.segments().size());
        assertEquals(60_000, context.segments().get(1).startMs());
        assertEquals(120_000, context.segments().get(1).endMs());
        assertEquals("第二段", context.segments().get(1).transcript());
        // 音频片段不含 OCR 文本与证据帧,下游据此天然跳过画面相关处理。
        assertTrue(context.segments().get(0).ocrTexts().isEmpty());
        assertTrue(context.segments().get(0).evidenceFrames().isEmpty());
        assertEquals("第一段\n第二段", context.transcriptText());
    }

    @Test
    void failsWhenNoSpeechIsRecognized() {
        AudioContextService service = serviceWith(List.of());

        assertThrows(IllegalStateException.class,
                () -> service.build(AUDIO_SOURCE, "总结", null));
    }
}
