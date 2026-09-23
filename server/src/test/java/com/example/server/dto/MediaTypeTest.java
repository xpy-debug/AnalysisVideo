package com.example.server.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MediaTypeTest {

    @Test
    void fallsBackToVideoForNullBlankOrUnknownValues() {
        assertEquals(MediaType.VIDEO, MediaType.fromNullable(null));
        assertEquals(MediaType.VIDEO, MediaType.fromNullable(""));
        assertEquals(MediaType.VIDEO, MediaType.fromNullable("  "));
        assertEquals(MediaType.VIDEO, MediaType.fromNullable("movie"));
    }

    @Test
    void parsesKnownTypesCaseInsensitively() {
        assertEquals(MediaType.AUDIO, MediaType.fromNullable("audio"));
        assertEquals(MediaType.AUDIO, MediaType.fromNullable("AUDIO"));
        assertEquals(MediaType.VIDEO, MediaType.fromNullable(" Video "));
    }

    @Test
    void isAudioOnlyTrueForAudio() {
        assertTrue(MediaType.AUDIO.isAudio());
        assertFalse(MediaType.VIDEO.isAudio());
        assertFalse(MediaType.fromNullable("unknown").isAudio());
    }
}
