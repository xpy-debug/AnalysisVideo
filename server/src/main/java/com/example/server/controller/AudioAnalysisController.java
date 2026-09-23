package com.example.server.controller;

import com.example.server.common.ErrorCode;
import com.example.server.common.Result;
import com.example.server.dto.AgentFeedback;
import com.example.server.dto.AnalysisMode;
import com.example.server.dto.MediaType;
import com.example.server.dto.TaskStatus;
import com.example.server.entity.MediaFile;
import com.example.server.exception.BusinessException;
import com.example.server.service.AgentCheckpointService;
import com.example.server.service.AiService;
import com.example.server.service.AnalysisDispatchService;
import com.example.server.service.AnalysisStatusService;
import com.example.server.service.AudioDispatchService;
import com.example.server.service.AuthService;
import com.example.server.service.MediaService;
import com.example.server.service.TaskEventService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 音频分析入口:与 {@link AnalysisController} 的提交/修订对称,但投递到音频队列。
 *
 * <p>只读类接口(agent-plan / agent-trace / agent-evaluation / history / follow-up / evidence-search)
 * 都以 (mediaId, goal, mode) 为作用域、与媒体类型无关,音频直接复用 {@link AnalysisController} 的那一套,
 * 因此这里只额外提供「提交 + 修订 + 状态 + 事件」四个类型相关端点。
 */
@RestController
@RequestMapping("/audio")
public class AudioAnalysisController {

    private static final int MAX_GOAL_LENGTH = 500;

    private final AiService aiService;
    private final AudioDispatchService audioDispatchService;
    private final AgentCheckpointService checkpointService;
    private final MediaService mediaService;
    private final TaskEventService taskEventService;
    private final AnalysisStatusService statusService;

    public AudioAnalysisController(AiService aiService,
                                   AudioDispatchService audioDispatchService,
                                   AgentCheckpointService checkpointService,
                                   MediaService mediaService,
                                   TaskEventService taskEventService,
                                   AnalysisStatusService statusService) {
        this.aiService = aiService;
        this.audioDispatchService = audioDispatchService;
        this.checkpointService = checkpointService;
        this.mediaService = mediaService;
        this.taskEventService = taskEventService;
        this.statusService = statusService;
    }

    @PostMapping("/ai")
    public ResponseEntity<Result<Void>> aiAnalyze(
            @RequestParam Long id,
            @RequestParam(defaultValue = "理解音频核心内容并生成结构化分析报告") String goal,
            @RequestParam(required = false) String mode,
            @RequestAttribute(AuthService.REQUEST_USER_ID) Long userId) {
        String normalizedGoal = normalizeText(goal, "分析目标");
        AnalysisMode analysisMode = AnalysisMode.fromRequest(mode);
        MediaFile mediaFile = requireAudio(id, userId);
        if (checkpointService.loadResult(id, normalizedGoal, analysisMode) != null) {
            return ResponseEntity.ok(Result.ok());
        }
        return submissionResponse(
                audioDispatchService.submit(mediaFile, normalizedGoal, null, analysisMode));
    }

    @PostMapping("/revise")
    public ResponseEntity<Result<Void>> reviseAgentResult(
            @Valid @RequestBody AgentFeedback feedback,
            @RequestParam(required = false) String mode,
            @RequestAttribute(AuthService.REQUEST_USER_ID) Long userId) {
        Integer rating = feedback.rating();
        if (rating != null && rating != -1 && rating != 1) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "rating 只能是 -1 或 1");
        }
        MediaFile mediaFile = requireAudio(feedback.mediaId(), userId);
        String revisedGoal = aiService.revisionGoal(feedback);
        return submissionResponse(audioDispatchService.submit(
                mediaFile, revisedGoal, feedback, AnalysisMode.fromRequest(mode)));
    }

    @GetMapping("/analysis-status")
    public Result<TaskStatus> analysisStatus(
            @RequestParam Long id,
            @RequestParam String goal,
            @RequestParam(required = false) String mode,
            @RequestAttribute(AuthService.REQUEST_USER_ID) Long userId) {
        requireAudio(id, userId);
        String normalizedGoal = normalizeText(goal, "分析目标");
        return Result.ok(statusService.current(id, normalizedGoal, AnalysisMode.fromRequest(mode)));
    }

    @GetMapping(value = "/analysis-events", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter analysisEvents(
            @RequestParam Long id,
            @RequestParam String goal,
            @RequestParam(required = false) String mode,
            @RequestAttribute(AuthService.REQUEST_USER_ID) Long userId) {
        requireAudio(id, userId);
        String normalizedGoal = normalizeText(goal, "分析目标");
        AnalysisMode analysisMode = AnalysisMode.fromRequest(mode);
        return taskEventService.subscribe(
                id,
                TaskEventService.ANALYSIS,
                normalizedGoal,
                analysisMode,
                statusService.current(id, normalizedGoal, analysisMode),
                statusService.stage(id, normalizedGoal, analysisMode));
    }

    /** 音频端点只接受音频媒体,避免视频被误投到音频队列。 */
    private MediaFile requireAudio(Long mediaId, Long userId) {
        MediaFile mediaFile = mediaService.requireOwnedMedia(mediaId, userId);
        if (!MediaType.fromNullable(mediaFile.getMediaType()).isAudio()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "该文件不是音频，请使用视频分析接口");
        }
        return mediaFile;
    }

    private String normalizeText(String value, String field) {
        if (value == null || value.isBlank() || value.length() > MAX_GOAL_LENGTH) {
            throw new IllegalArgumentException(field + "不能为空且不能超过 " + MAX_GOAL_LENGTH + " 字");
        }
        return value.trim();
    }

    private ResponseEntity<Result<Void>> submissionResponse(
            AnalysisDispatchService.SubmissionResult result) {
        return switch (result) {
            case ACCEPTED -> ResponseEntity.accepted().body(Result.ok());
            case RATE_LIMITED -> throw new BusinessException(ErrorCode.RATE_LIMITED, "系统繁忙，请稍后再试");
            case DUPLICATE -> throw new BusinessException(ErrorCode.CONFLICT, "相同音频和分析目标正在处理中");
            case FAILED -> throw new BusinessException(ErrorCode.INTERNAL_ERROR, "任务提交失败");
        };
    }
}
