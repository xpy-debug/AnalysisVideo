package com.example.server.dto;

import java.time.LocalDateTime;

/**
 * 一个视频的一次历史分析结果：当时的目标原文 + 产出的完整内容，供"历史版本"窗口展示。
 *
 * <p>任务身份 = (内容, 目标, 模式)，同一目标重复提交会被幂等覆盖，因此一条 = 一个不同目标的
 * 成功分析；{@link #stage} 用于区分"完成"与"带警告完成"。
 *
 * @param goal       当时的分析目标（用户原始要求）
 * @param title      产物标题
 * @param stage      完成阶段（ANALYSIS_COMPLETED / ANALYSIS_COMPLETED_WITH_WARNINGS）
 * @param analyzedAt 该结果最后一次产出的时间
 * @param markdown   结构化产物的 Markdown 全文
 */
public record AnalysisHistoryItem(
        String goal,
        String title,
        String stage,
        LocalDateTime analyzedAt,
        String markdown
) {
}
