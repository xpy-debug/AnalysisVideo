package com.example.server.consumer;

import com.example.server.dto.AgentState;
import com.example.server.dto.AnalysisMode;
import com.example.server.dto.AnalysisTaskMsg;
import com.example.server.dto.TaskStage;
import com.example.server.dto.TaskStatus;
import com.example.server.service.AgentCheckpointService;
import com.example.server.service.AgentLoopService;
import com.example.server.service.AiService;
import com.example.server.service.FailedAnalysisTaskService;
import com.example.server.service.MediaService;
import com.example.server.service.TaskEventService;
import com.example.server.utils.AnalysisTaskKeys;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.NoSuchElementException;

/**
 * 音频分析消费者:与 {@link VideoAnalysisConsumer} 同构,但只消费音频队列,失败收敛到音频死信队列。
 *
 * <p>之所以是「受控复制」而不是抽象基类:音频链路要与视频链路彻底解耦,任何一侧的调整都不应
 * 牵动另一侧。下游 {@link AiService#asyncAnalyze} 会按媒体类型选择音频上下文构建器(只跑 ASR),
 * 因此这里与视频消费者共享的是同一套编排与 checkpoint/事件机制,差异只在进度文案与死信目标。
 */
@Component
public class AudioAnalysisConsumer {

    private static final Logger log = LoggerFactory.getLogger(AudioAnalysisConsumer.class);
    /** 与 application.properties 的 spring.rabbitmq.listener.simple.retry.max-attempts 保持一致。 */
    private static final int MAX_DELIVERY_ATTEMPTS = 3;
    private static final Duration ACTIVE_TTL = Duration.ofHours(6);
    /** cause 链遍历深度上限，防御异常自引用导致的死循环。 */
    private static final int MAX_CAUSE_DEPTH = 16;

    private final AiService aiService;
    private final RedissonClient redissonClient;
    private final StringRedisTemplate redisTemplate;
    private final AgentCheckpointService checkpointService;
    private final RabbitTemplate rabbitTemplate;
    private final FailedAnalysisTaskService failedTaskService;
    private final MediaService mediaService;
    private final TaskEventService taskEventService;
    private final String deadLetterExchange;
    private final String deadLetterRoutingKey;

    public AudioAnalysisConsumer(AiService aiService,
                                 RedissonClient redissonClient,
                                 StringRedisTemplate redisTemplate,
                                 AgentCheckpointService checkpointService,
                                 RabbitTemplate rabbitTemplate,
                                 FailedAnalysisTaskService failedTaskService,
                                 MediaService mediaService,
                                 TaskEventService taskEventService,
                                 @Value("${app.mq.audio-analysis-dead.exchange:audio-analysis.dead.exchange}")
                                 String deadLetterExchange,
                                 @Value("${app.mq.audio-analysis-dead.routing-key:audio.analysis.dead}")
                                 String deadLetterRoutingKey) {
        this.aiService = aiService;
        this.redissonClient = redissonClient;
        this.redisTemplate = redisTemplate;
        this.checkpointService = checkpointService;
        this.rabbitTemplate = rabbitTemplate;
        this.failedTaskService = failedTaskService;
        this.mediaService = mediaService;
        this.taskEventService = taskEventService;
        this.deadLetterExchange = deadLetterExchange;
        this.deadLetterRoutingKey = deadLetterRoutingKey;
    }

    @RabbitListener(queues = "${app.mq.audio-analysis.queue:audio-analysis.queue}")
    public void onMessage(AnalysisTaskMsg msg) {
        String rejection = rejectionReason(msg);
        if (rejection != null) {
            discardPoisonMessage(msg, rejection);
            return;
        }
        Long mediaId = msg.getMediaId();
        AnalysisMode mode = AnalysisMode.fromNullable(msg.getMode());
        String contentHash = AnalysisTaskKeys.normalizeContentHash(mediaId, msg.getContentHash());
        String goalDigest = AnalysisTaskKeys.goalDigest(msg.getUserGoal(), mode);
        String lockKey = AnalysisTaskKeys.lock(contentHash, goalDigest);
        String activeKey = AnalysisTaskKeys.active(contentHash, goalDigest);
        String completedKey = AnalysisTaskKeys.completed(contentHash, goalDigest);
        String attemptsKey = AnalysisTaskKeys.attempts(contentHash, goalDigest);
        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired = false;
        boolean retrying = false;
        long attempt = 0;
        try {
            acquired = lock.tryLock();
            if (!acquired) {
                log.info("audio_analysis_skipped mediaId={} acquired={}", mediaId, acquired);
                return;
            }
            if (!mediaService.exists(mediaId)) {
                log.info("audio_analysis_discarded_deleted_media mediaId={}", mediaId);
                return;
            }
            Long currentAttempt = redisTemplate.opsForValue().increment(attemptsKey);
            attempt = currentAttempt == null ? 1 : currentAttempt;
            redisTemplate.expire(attemptsKey, ACTIVE_TTL);
            taskEventService.publishAnalysis(mediaId, msg.getUserGoal(), mode,
                    TaskStatus.of(TaskStatus.State.PROCESSING, "音频分析任务开始执行"),
                    TaskStage.CONSUMING);
            if (msg.isRevision()) {
                if (!checkpointService.beginStagedRevision(mediaId, msg.getUserGoal(), mode)) {
                    throw new IllegalStateException("修订任务状态不存在，等待消息队列重试");
                }
                redisTemplate.delete(completedKey);
            } else {
                String completedMediaId = redisTemplate.opsForValue().get(completedKey);
                if (completedMediaId != null) {
                    Long sourceMediaId = parseMediaId(completedMediaId, completedKey);
                    AgentState reusable = sourceMediaId == null ? null
                            : checkpointService.loadResult(sourceMediaId, msg.getUserGoal(), mode);
                    if (reusable != null && reusable.result() != null
                            && aiService.reuseResult(mediaId, sourceMediaId, reusable, mode)) {
                        taskEventService.publishAnalysis(mediaId, msg.getUserGoal(), mode,
                                TaskStatus.completed(reusable), TaskStage.COMPLETED_REUSED);
                        log.info("audio_analysis_reused mediaId={} sourceMediaId={}", mediaId, sourceMediaId);
                        return;
                    }
                    redisTemplate.delete(completedKey);
                }
            }
            saveStage(mediaId, msg.getUserGoal(), mode, TaskStage.CONSUMING);
            aiService.asyncAnalyze(mediaId, msg.getUserGoal(), mode);
            if (msg.isRevision()) {
                checkpointService.completeStagedRevision(mediaId, msg.getUserGoal(), mode);
            }
            if (!mediaService.exists(mediaId)) {
                mediaService.purgeRuntimeArtifacts(mediaId);
                log.info("audio_analysis_cleanup_after_media_deleted mediaId={}", mediaId);
                return;
            }
            redisTemplate.opsForValue().set(
                    completedKey, String.valueOf(mediaId), Duration.ofDays(7));
            AgentState completed = checkpointService.loadResult(mediaId, msg.getUserGoal(), mode);
            if (completed != null && completed.result() != null) {
                taskEventService.publishAnalysis(mediaId, msg.getUserGoal(), mode,
                        TaskStatus.completed(completed), TaskStage.COMPLETED);
            }
        } catch (AgentLoopService.BudgetExceededException e) {
            saveStage(mediaId, msg.getUserGoal(), mode, TaskStage.BUDGET_EXHAUSTED);
            taskEventService.publishAnalysis(mediaId, msg.getUserGoal(), mode,
                    TaskStatus.of(TaskStatus.State.FAILED, e.getMessage()),
                    TaskStage.BUDGET_EXHAUSTED);
            log.warn("audio_analysis_budget_exhausted mediaId={} reason={}", mediaId, e.getMessage());
            return;
        } catch (Exception e) {
            boolean permanent = isPermanentFailure(e);
            if (!permanent && acquired && attempt > 0 && attempt < MAX_DELIVERY_ATTEMPTS) {
                retrying = true;
                redisTemplate.expire(activeKey, ACTIVE_TTL);
                saveStage(mediaId, msg.getUserGoal(), mode, TaskStage.RETRYING);
                taskEventService.publishAnalysis(mediaId, msg.getUserGoal(), mode,
                        TaskStatus.of(TaskStatus.State.PROCESSING, "本次执行失败，等待消息队列重试"),
                        TaskStage.RETRYING);
                log.warn("audio_analysis_retry_scheduled mediaId={} attempt={}", mediaId, attempt, e);
                throw new IllegalStateException("音频分析消费失败，交由消息队列重试", e);
            }
            if (acquired && (permanent || attempt >= MAX_DELIVERY_ATTEMPTS)) {
                try {
                    try {
                        failedTaskService.record(msg, attempt, e);
                    } catch (RuntimeException recordError) {
                        e.addSuppressed(recordError);
                        log.error("failed_analysis_record_write_failed mediaId={}", mediaId, recordError);
                    }
                    rabbitTemplate.convertAndSend(deadLetterExchange, deadLetterRoutingKey, msg);
                    saveStage(mediaId, msg.getUserGoal(), mode, TaskStage.DEAD_LETTERED);
                    taskEventService.publishAnalysis(mediaId, msg.getUserGoal(), mode,
                            TaskStatus.of(TaskStatus.State.FAILED, "分析失败，已进入人工处理队列"),
                            TaskStage.DEAD_LETTERED);
                    log.error("audio_analysis_dead_lettered mediaId={} attempts={} permanent={}",
                            mediaId, attempt, permanent, e);
                    return;
                } catch (RuntimeException deadLetterError) {
                    retrying = true;
                    deadLetterError.addSuppressed(e);
                    log.error("audio_analysis_dead_letter_dispatch_failed mediaId={}", mediaId, deadLetterError);
                    throw deadLetterError;
                }
            }
            log.error("audio_analysis_consume_failed mediaId={}", mediaId, e);
            throw new IllegalStateException("音频分析消费失败", e);
        } finally {
            if (acquired) {
                if (!retrying) redisTemplate.delete(java.util.List.of(activeKey, attemptsKey));
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        }
    }

    /** 结构性校验：返回拒绝原因，合法则返回 null。 */
    private String rejectionReason(AnalysisTaskMsg msg) {
        if (msg == null) return "消息体为空";
        if (msg.getMediaId() == null) return "缺少 mediaId";
        if (msg.getUserGoal() == null || msg.getUserGoal().isBlank()) return "缺少分析目标";
        if (!msg.hasSupportedAction()) return "不支持的 action=" + msg.getAction();
        return null;
    }

    /**
     * 毒消息收敛。结构性非法的消息重投多少次都不会变好，因此落台账 + 转投失败主题后正常返回
     * （相当于 ACK）。台账与失败主题只要有一个写成功就可以确认，两个都失败则拒绝确认，
     * 否则消息会被静默丢弃且无处可查。
     */
    private void discardPoisonMessage(AnalysisTaskMsg msg, String reason) {
        log.error("audio_analysis_poison_message reason={} payload={}", reason, describe(msg));
        if (msg == null) return;

        IllegalArgumentException error =
                new IllegalArgumentException("invalid audio analysis message: " + reason);
        boolean recorded = false;
        boolean deadLettered = false;
        try {
            failedTaskService.record(msg, 0, error);
            recorded = true;
        } catch (RuntimeException recordError) {
            log.error("poison_message_record_failed payload={}", describe(msg), recordError);
        }
        try {
            rabbitTemplate.convertAndSend(deadLetterExchange, deadLetterRoutingKey, msg);
            deadLettered = true;
        } catch (RuntimeException dispatchError) {
            log.error("poison_message_dead_letter_failed payload={}", describe(msg), dispatchError);
        }

        if (!recorded && !deadLettered) {
            throw new IllegalStateException(
                    "毒消息无法收敛：失败台账与失败主题均不可用，拒绝确认以避免消息丢失", error);
        }
        releasePoisonTaskState(msg);
    }

    /** 毒消息在 try 之前就返回，走不到 finally 的清理逻辑，这里补上幂等键的释放与终态事件。 */
    private void releasePoisonTaskState(AnalysisTaskMsg msg) {
        if (msg.getMediaId() == null || msg.getUserGoal() == null || msg.getUserGoal().isBlank()) {
            return;
        }
        try {
            AnalysisMode mode = AnalysisMode.fromNullable(msg.getMode());
            String contentHash = AnalysisTaskKeys.normalizeContentHash(
                    msg.getMediaId(), msg.getContentHash());
            String goalDigest = AnalysisTaskKeys.goalDigest(msg.getUserGoal(), mode);
            redisTemplate.delete(java.util.List.of(
                    AnalysisTaskKeys.active(contentHash, goalDigest),
                    AnalysisTaskKeys.attempts(contentHash, goalDigest)));
            saveStage(msg.getMediaId(), msg.getUserGoal(), mode, TaskStage.DEAD_LETTERED);
            taskEventService.publishAnalysis(msg.getMediaId(), msg.getUserGoal(), mode,
                    TaskStatus.of(TaskStatus.State.FAILED, "任务消息非法，已终止"),
                    TaskStage.DEAD_LETTERED);
        } catch (RuntimeException e) {
            log.warn("poison_message_state_release_failed payload={}", describe(msg), e);
        }
    }

    /** 只记录消息摘要，不打印完整目标文本，避免日志里混入长文本或用户敏感内容。 */
    private String describe(AnalysisTaskMsg msg) {
        if (msg == null) return "null";
        return "mediaId=" + msg.getMediaId()
                + " action=" + msg.getAction()
                + " contentHash=" + msg.getContentHash()
                + " goalLength=" + (msg.getUserGoal() == null ? 0 : msg.getUserGoal().length());
    }

    /**
     * 判断是否为「重投也不会成功」的失败。必须沿 cause 链向下找：AiService / AudioContextService /
     * SegmentedTranscriptionService 会把根因层层包进 IllegalStateException。
     */
    private boolean isPermanentFailure(Throwable error) {
        Throwable current = error;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (current instanceof IllegalArgumentException
                    || current instanceof SecurityException
                    || current instanceof NoSuchElementException) {
                return true;
            }
            if (current.getCause() == current) break;
            current = current.getCause();
        }
        return false;
    }

    private Long parseMediaId(String value, String completedKey) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            redisTemplate.delete(completedKey);
            log.warn("invalid_completed_media_reference key={} value={}", completedKey, value);
            return null;
        }
    }

    private void saveStage(Long mediaId, String goal, AnalysisMode mode, TaskStage stage) {
        try {
            checkpointService.saveStage(mediaId, goal, mode, stage);
        } catch (RuntimeException e) {
            log.warn("analysis_stage_checkpoint_failed mediaId={} stage={}", mediaId, stage, e);
        }
    }
}
