package com.example.server.service;

import com.example.server.dto.MediaType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link MediaService#normalizeFilename} 是上传链路唯一的后缀白名单闸门。
 * 这里只测这个纯函数,因此用 null 依赖构造实例——方法本身不触碰任何字段。
 */
class MediaServiceFilenameTest {

    private final MediaService mediaService =
            new MediaService(null, null, null, null, null, null, null, null);

    @Test
    void acceptsAudioSuffixesOnlyForAudioType() {
        for (String suffix : new String[]{".mp3", ".wav", ".m4a", ".aac", ".flac", ".ogg"}) {
            assertEquals("clip" + suffix,
                    mediaService.normalizeFilename("clip" + suffix, MediaType.AUDIO));
        }
    }

    @Test
    void acceptsVideoSuffixesOnlyForVideoType() {
        for (String suffix : new String[]{".mp4", ".mov", ".mkv", ".avi", ".webm", ".m4v"}) {
            assertEquals("clip" + suffix,
                    mediaService.normalizeFilename("clip" + suffix, MediaType.VIDEO));
        }
    }

    @Test
    void rejectsAudioFileOnVideoPath() {
        assertThrows(IllegalArgumentException.class,
                () -> mediaService.normalizeFilename("song.mp3", MediaType.VIDEO));
    }

    @Test
    void rejectsVideoFileOnAudioPath() {
        assertThrows(IllegalArgumentException.class,
                () -> mediaService.normalizeFilename("movie.mp4", MediaType.AUDIO));
    }

    @Test
    void stripsPathAndNormalizesCase() {
        assertEquals("song.MP3",
                mediaService.normalizeFilename("/tmp/dir/song.MP3", MediaType.AUDIO));
    }

    @Test
    void rejectsBlankOrOverlongNames() {
        assertThrows(IllegalArgumentException.class,
                () -> mediaService.normalizeFilename("   ", MediaType.AUDIO));
        assertThrows(IllegalArgumentException.class,
                () -> mediaService.normalizeFilename("a".repeat(260) + ".mp3", MediaType.AUDIO));
    }

    @Test
    void nullTypeDefaultsToVideoWhitelist() {
        assertEquals("movie.mp4", mediaService.normalizeFilename("movie.mp4", null));
        assertThrows(IllegalArgumentException.class,
                () -> mediaService.normalizeFilename("song.mp3", null));
    }
}
