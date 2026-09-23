package com.example.server.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.server.entity.MediaFile;
import com.example.server.dto.MediaType;
import com.example.server.dto.VideoContext;
import com.example.server.mapper.MediaFileMapper;
import com.example.server.utils.MinioUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class MediaService {

    private static final Logger log = LoggerFactory.getLogger(MediaService.class);

    private final MediaFileMapper mediaFileMapper;
    private final StringRedisTemplate redisTemplate;
    private final MinioUtils minioUtils;
    private final ObjectMapper objectMapper;
    private final AgentCheckpointService checkpointService;
    private final AgentTelemetry telemetry;
    private final QdrantVectorStore vectorStore;
    private final VideoContextService videoContextService;

    private static final String MEDIA_MD5_KEY_PREFIX = "media:md5:";
    private static final Set<String> VIDEO_SUFFIXES = Set.of(
            ".mp4", ".mov", ".mkv", ".avi", ".webm", ".m4v");
    private static final Set<String> AUDIO_SUFFIXES = Set.of(
            ".mp3", ".wav", ".m4a", ".aac", ".flac", ".ogg");

    public MediaService(MediaFileMapper mediaFileMapper,
                        StringRedisTemplate redisTemplate,
                        MinioUtils minioUtils,
                        ObjectMapper objectMapper,
                        AgentCheckpointService checkpointService,
                        AgentTelemetry telemetry,
                        QdrantVectorStore vectorStore,
                        VideoContextService videoContextService) {
        this.mediaFileMapper = mediaFileMapper;
        this.redisTemplate = redisTemplate;
        this.minioUtils = minioUtils;
        this.objectMapper = objectMapper;
        this.checkpointService = checkpointService;
        this.telemetry = telemetry;
        this.vectorStore = vectorStore;
        this.videoContextService = videoContextService;
    }

    public String calculateMd5(MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream()) {
            return calculateMd5(inputStream);
        }
    }

    public String calculateMd5(File file) throws IOException {
        try (InputStream inputStream = Files.newInputStream(file.toPath())) {
            return calculateMd5(inputStream);
        }
    }

    public void rememberContentHash(Long mediaId, String md5) {
        if (mediaId == null || md5 == null || md5.isBlank()) return;
        try {
            redisTemplate.opsForValue().set(MEDIA_MD5_KEY_PREFIX + mediaId, md5);
        } catch (RuntimeException e) {
            log.warn("media_hash_cache_write_failed mediaId={}", mediaId, e);
        }
    }

    /** 兼容旧调用方:未指定媒体类型时按视频落库。 */
    public MediaFile saveUploadedMedia(String filename, String fileUrl, Long userId, String md5) {
        return saveUploadedMedia(filename, fileUrl, userId, md5, MediaType.VIDEO);
    }

    public MediaFile saveUploadedMedia(String filename,
                                       String fileUrl,
                                       Long userId,
                                       String md5,
                                       MediaType type) {
        MediaType mediaType = type == null ? MediaType.VIDEO : type;
        MediaFile mediaFile = new MediaFile();
        mediaFile.setFilename(normalizeFilename(filename, mediaType));
        mediaFile.setFilePath(fileUrl);
        mediaFile.setStatus("COMPLETED");
        mediaFile.setUploadTime(LocalDateTime.now());
        mediaFile.setUserId(userId);
        mediaFile.setContentHash(md5);
        mediaFile.setMediaType(mediaType.name());
        try {
            mediaFileMapper.insert(mediaFile);
            rememberContentHash(mediaFile.getId(), md5);
            invalidateUserList(userId);
            return mediaFile;
        } catch (RuntimeException e) {
            removeUploadedObject(fileUrl, e);
            throw e;
        }
    }

    public List<MediaFile> listByUser(Long userId) {
        String cacheKey = userListKey(userId);
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return objectMapper.readValue(cached, new TypeReference<List<MediaFile>>() { });
            }
        } catch (Exception e) {
            log.warn("media_list_cache_read_failed userId={}", userId, e);
        }

        QueryWrapper<MediaFile> query = new QueryWrapper<>();
        List<MediaFile> mediaFiles = mediaFileMapper.selectList(
                query.select("id", "filename", "status", "cover_url", "upload_time", "media_type")
                        .eq("user_id", userId)
                        .orderByDesc("id"));
        try {
            redisTemplate.opsForValue().set(
                    cacheKey, objectMapper.writeValueAsString(mediaFiles), 30, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("media_list_cache_write_failed userId={}", userId, e);
        }
        return mediaFiles;
    }

    public String contentHash(Long mediaId) {
        try {
            String cached = redisTemplate.opsForValue().get(MEDIA_MD5_KEY_PREFIX + mediaId);
            if (cached != null && !cached.isBlank()) return cached;
        } catch (RuntimeException e) {
            log.warn("media_hash_cache_read_failed mediaId={}", mediaId, e);
        }

        MediaFile mediaFile = mediaFileMapper.selectById(mediaId);
        String persisted = mediaFile == null ? null : mediaFile.getContentHash();
        rememberContentHash(mediaId, persisted);
        return persisted;
    }

    public void deleteOwnedMedia(Long mediaId, Long userId) {
        MediaFile mediaFile = requireOwnedMedia(mediaId, userId);
        mediaFileMapper.deleteById(mediaId);
        if (mediaFile.getFilePath() != null && mediaFile.getFilePath().startsWith("http")) {
            try {
                minioUtils.removeFile(mediaFile.getFilePath());
            } catch (RuntimeException e) {
                log.warn("media_object_cleanup_failed mediaId={} path={}",
                        mediaId, mediaFile.getFilePath(), e);
            }
        }
        purgeRuntimeArtifacts(mediaId);
        invalidateUserList(userId);
    }

    public boolean exists(Long mediaId) {
        return mediaId != null && mediaFileMapper.selectById(mediaId) != null;
    }

    public void purgeRuntimeArtifacts(Long mediaId) {
        VideoContext context = null;
        try {
            context = checkpointService.loadContext(mediaId);
        } catch (RuntimeException e) {
            log.warn("media_evidence_manifest_read_failed mediaId={}", mediaId, e);
        }
        videoContextService.deleteEvidenceFrames(context);
        try {
            redisTemplate.delete(List.of(
                    MEDIA_MD5_KEY_PREFIX + mediaId,
                    "transcription:active:" + mediaId,
                    "transcription:state:" + mediaId));
            checkpointService.deleteMedia(mediaId);
            telemetry.deleteTask(mediaId);
            vectorStore.deleteMedia(mediaId);
        } catch (RuntimeException e) {
            log.warn("media_runtime_cleanup_failed mediaId={}", mediaId, e);
        }
    }

    public void invalidateUserList(Long userId) {
        if (userId == null) return;
        try {
            redisTemplate.delete(userListKey(userId));
        } catch (RuntimeException e) {
            log.warn("media_list_cache_invalidation_failed userId={}", userId, e);
        }
    }

    public String readableSource(String source) {
        return minioUtils.readableSource(source);
    }

    /** 兼容旧调用方:按视频后缀白名单归一化文件名。 */
    public String normalizeVideoFilename(String filename) {
        return normalizeFilename(filename, MediaType.VIDEO);
    }

    /** 按音频后缀白名单归一化文件名。 */
    public String normalizeAudioFilename(String filename) {
        return normalizeFilename(filename, MediaType.AUDIO);
    }

    /**
     * 归一化上传文件名:去路径、trim、长度校验,并按媒体类型套用对应的后缀白名单。
     * 视频链路与音频链路共用同一套规则,仅白名单不同。
     */
    public String normalizeFilename(String filename, MediaType type) {
        MediaType mediaType = type == null ? MediaType.VIDEO : type;
        String typeLabel = mediaType.isAudio() ? "音频" : "视频";
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException(typeLabel + "文件名不能为空");
        }
        String normalized = filename.replace('\\', '/');
        normalized = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (normalized.isBlank() || normalized.length() > 255) {
            throw new IllegalArgumentException(typeLabel + "文件名无效或过长");
        }
        String suffix = fileSuffix(normalized).toLowerCase(java.util.Locale.ROOT);
        if (!suffixesFor(mediaType).contains(suffix)) {
            throw new IllegalArgumentException(mediaType.isAudio()
                    ? "仅支持 MP3、WAV、M4A、AAC、FLAC 和 OGG 音频"
                    : "仅支持 MP4、MOV、MKV、AVI、WEBM 和 M4V 视频");
        }
        return normalized;
    }

    private Set<String> suffixesFor(MediaType type) {
        return type.isAudio() ? AUDIO_SUFFIXES : VIDEO_SUFFIXES;
    }

    public MediaFile requireOwnedMedia(Long mediaId, Long userId) {
        MediaFile mediaFile = mediaFileMapper.selectById(mediaId);
        if (mediaFile == null) throw new NoSuchElementException("文件不存在");
        if (!Objects.equals(mediaFile.getUserId(), userId)) {
            throw new SecurityException("无权访问该文件");
        }
        return mediaFile;
    }

    private String calculateMd5(InputStream inputStream) throws IOException {
        MessageDigest digest = md5Digest();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private MessageDigest md5Digest() {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 is not available", e);
        }
    }

    private String userListKey(Long userId) {
        // v3：列表缓存新增了 media_type 字段，升版让旧缓存（无类型）立即失效，
        // 避免前端把音频误判成视频而走错分析端点。
        return "media:list:v3:user:" + userId;
    }

    private String fileSuffix(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : "";
    }

    private void removeUploadedObject(String fileUrl, RuntimeException originalError) {
        try {
            minioUtils.removeFile(fileUrl);
        } catch (RuntimeException cleanupError) {
            originalError.addSuppressed(cleanupError);
            log.warn("uploaded_object_rollback_failed path={}", fileUrl, cleanupError);
        }
    }
}
