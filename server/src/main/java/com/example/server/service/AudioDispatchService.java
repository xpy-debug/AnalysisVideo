package com.example.server.service;

import com.example.server.dto.AgentFeedback;
import com.example.server.dto.AnalysisMode;
import com.example.server.dto.AnalysisTaskMsg;
import com.example.server.dto.TaskStage;
import com.example.server.dto.TaskStatus;
import com.example.server.entity.MediaFile;
import com.example.server.utils.AnalysisTaskKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 音频分析任务的派发入口:与 {@link AnalysisDispatchService} 同构,但投递到音频交换机/队列,
 * 与视频链路互不干扰。幂等键与限流复用同一套实现——任务身份以 (内容, 目标, 模式) 为作用域,
 * 音频与视频的 contentHash 天然不同,不会串键。
 */
@Service
public class AudioDispatchService {

    private static final Logger log = LoggerFactory.getLogger(AudioDispatchService.class);
    private static final Duration ACTIVE_TTL = Duration.ofHours(6);

    private final AiService aiService;
    private final MediaService mediaService;
    private final StringRedisTemplate redisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final TaskEventService taskEventService;
    private final AnalysisDispatchService quotaService;
    private final String audioExchange;
    private final String audioRoutingKey;

    public AudioDispatchService(AiService aiService,
                                MediaService mediaService,
                                StringRedisTemplate redisTemplate,
                                RabbitTemplate rabbitTemplate,
                                TaskEventService taskEventService,
                                AnalysisDispatchService quotaService,
                                @Value("${app.mq.audio-analysis.exchange:audio-analysis.exchange}")
                                String audioExchange,
                                @Value("${app.mq.audio-analysis.routing-key:audio.analysis}")
                                String audioRoutingKey) {
        this.aiService = aiService;
        this.mediaService = mediaService;
        this.redisTemplate = redisTemplate;
        this.rabbitTemplate = rabbitTemplate;
        this.taskEventService = taskEventService;
        this.quotaService = quotaService;
        this.audioExchange = audioExchange;
        this.audioRoutingKey = audioRoutingKey;
    }

    public AnalysisDispatchService.SubmissionResult submit(MediaFile mediaFile,
                                                           String goal,
                                                           AgentFeedback revision,
                                                           AnalysisMode mode) {
        AnalysisMode resolvedMode = mode == null ? AnalysisMode.GENERAL : mode;
        Long mediaId = mediaFile.getId();
        String action = revision == null
                ? AnalysisTaskMsg.START_ANALYSIS
                : AnalysisTaskMsg.REVISE_ANALYSIS;
        String contentHash = revision == null ? contentHash(mediaId) : "media-" + mediaId;
        String goalDigest = AnalysisTaskKeys.goalDigest(goal, resolvedMode);
        String activeKey = AnalysisTaskKeys.active(contentHash, goalDigest);
        Boolean accepted = redisTemplate.opsForValue().setIfAbsent(
                activeKey, String.valueOf(mediaId), ACTIVE_TTL);
        if (!Boolean.TRUE.equals(accepted)) return AnalysisDispatchService.SubmissionResult.DUPLICATE;

        try {
            // 音视频共享同一组限流键,成本护栏统一。
            if (!quotaService.tryAcquireQuota(mediaFile.getUserId())) {
                redisTemplate.delete(activeKey);
                return AnalysisDispatchService.SubmissionResult.RATE_LIMITED;
            }
            if (revision != null) aiService.stageRevision(revision, resolvedMode);
            rabbitTemplate.convertAndSend(
                    audioExchange,
                    audioRoutingKey,
                    new AnalysisTaskMsg(mediaId, action, contentHash, goal, resolvedMode.name()));
        } catch (RuntimeException e) {
            redisTemplate.delete(activeKey);
            if (revision != null) aiService.cancelStagedRevision(mediaId, goal, resolvedMode);
            log.error("audio_dispatch_failed mediaId={} userId={}", mediaId, mediaFile.getUserId(), e);
            return AnalysisDispatchService.SubmissionResult.FAILED;
        }

        try {
            taskEventService.publishAnalysis(mediaId, goal, resolvedMode,
                    TaskStatus.of(TaskStatus.State.QUEUED, "任务已进入异步分析队列"), TaskStage.QUEUED);
        } catch (RuntimeException eventError) {
            log.warn("audio_queued_event_failed mediaId={} userId={}",
                    mediaId, mediaFile.getUserId(), eventError);
        }
        return AnalysisDispatchService.SubmissionResult.ACCEPTED;
    }

    private String contentHash(Long mediaId) {
        return AnalysisTaskKeys.normalizeContentHash(mediaId, mediaService.contentHash(mediaId));
    }
}
