package com.example.server.dto;

import java.util.Locale;

/**
 * 媒体类型:决定走哪条处理链路。
 *
 * <p>{@link #VIDEO} 是默认与兜底类型,行为等价于引入音频链路之前的原有流程(抽帧 + OCR + ASR);
 * {@link #AUDIO} 只跑 ASR,不抽帧、不做 OCR。类型在分片上传初始化时钉死,并随媒体记录持久化,
 * 分析侧据此选择上下文构建器与 MQ 队列。
 */
public enum MediaType {

    /** 视频:抽帧 + OCR + ASR。 */
    VIDEO,
    /** 音频:仅 ASR。 */
    AUDIO;

    /**
     * 从可空字符串安全解析类型。空值或无法识别的值一律回退到 {@link #VIDEO},
     * 保证历史数据、旧上传会话都不会因为类型字段而中断。
     */
    public static MediaType fromNullable(String value) {
        if (value == null || value.isBlank()) return VIDEO;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return VIDEO;
        }
    }

    public boolean isAudio() {
        return this == AUDIO;
    }
}
