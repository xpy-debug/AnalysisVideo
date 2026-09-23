package com.example.server.service;

import com.example.server.dto.MediaType;
import com.example.server.utils.MinioUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 分片上传的类型透传:类型只在 initialize 时确定并写入上传会话元数据,
 * 分片/合并/断点续传与类型无关。
 */
@ExtendWith(MockitoExtension.class)
class ChunkUploadServiceTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private StringRedisTemplate redisTemplate;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private MinioUtils minioUtils;

    @Mock
    private MediaService mediaService;

    @Test
    void initializePinsMediaTypeIntoTheUploadSession() throws Exception {
        when(mediaService.normalizeFilename("song.mp3", MediaType.AUDIO)).thenReturn("song.mp3");
        ChunkUploadService service =
                new ChunkUploadService(redisTemplate, redissonClient, minioUtils, mediaService);

        String uploadId = service.initialize("song.mp3", 3, 7L, MediaType.AUDIO);

        assertNotNull(uploadId);
        verify(mediaService).normalizeFilename("song.mp3", MediaType.AUDIO);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(redisTemplate.opsForHash()).putAll(anyString(), metadata.capture());
        assertEquals("AUDIO", metadata.getValue().get("mediaType"));
        assertEquals("song.mp3", metadata.getValue().get("filename"));
    }

    @Test
    void legacyInitializeDefaultsToVideo() throws Exception {
        when(mediaService.normalizeFilename("movie.mp4", MediaType.VIDEO)).thenReturn("movie.mp4");
        ChunkUploadService service =
                new ChunkUploadService(redisTemplate, redissonClient, minioUtils, mediaService);

        service.initialize("movie.mp4", 1, 7L);

        verify(mediaService).normalizeFilename("movie.mp4", MediaType.VIDEO);
    }
}
