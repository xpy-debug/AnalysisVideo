package com.example.server.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AgentCheckpointMapper {

    @Select("SELECT payload FROM agent_checkpoints WHERE media_id = #{mediaId} AND checkpoint_key = #{checkpointKey}")
    String findPayload(@Param("mediaId") Long mediaId,
                       @Param("checkpointKey") String checkpointKey);

    @Select("SELECT stage FROM agent_checkpoints WHERE media_id = #{mediaId} AND checkpoint_key = #{checkpointKey}")
    String findStage(@Param("mediaId") Long mediaId,
                     @Param("checkpointKey") String checkpointKey);

    @Insert("""
            INSERT INTO agent_checkpoints(media_id, checkpoint_key, stage, payload)
            VALUES(#{mediaId}, #{checkpointKey}, #{stage}, #{payload})
            ON DUPLICATE KEY UPDATE stage = VALUES(stage), payload = VALUES(payload), updated_at = CURRENT_TIMESTAMP(3)
            """)
    void upsert(@Param("mediaId") Long mediaId,
                @Param("checkpointKey") String checkpointKey,
                @Param("stage") String stage,
                @Param("payload") String payload);

    @Delete("DELETE FROM agent_checkpoints WHERE media_id = #{mediaId} AND checkpoint_key LIKE CONCAT(#{prefix}, '%')")
    void deleteByPrefix(@Param("mediaId") Long mediaId, @Param("prefix") String prefix);

    @Delete("DELETE FROM agent_checkpoints WHERE media_id = #{mediaId} AND checkpoint_key = #{checkpointKey}")
    void delete(@Param("mediaId") Long mediaId, @Param("checkpointKey") String checkpointKey);

    @Delete("DELETE FROM agent_checkpoints WHERE media_id = #{mediaId}")
    void deleteByMediaId(@Param("mediaId") Long mediaId);

    /**
     * 列出该媒体已成功产出的分析结果行（同一目标重复提交会被 upsert 覆盖，因此一条 = 一个目标）。
     *
     * <p>只取 {@code goal:*:result} 键，排除同前缀下的 plan/criticState/stage 行；按更新时间倒序，
     * 让最近一次分析排在最前。
     */
    @Select("""
            SELECT payload, stage, updated_at AS updatedAt
            FROM agent_checkpoints
            WHERE media_id = #{mediaId} AND checkpoint_key LIKE 'goal:%:result'
            ORDER BY updated_at DESC
            """)
    List<ResultRow> listResults(@Param("mediaId") Long mediaId);

    /** 历史结果查询的原始行：payload 为 AgentState JSON，交由上层解析。 */
    record ResultRow(String payload, String stage, LocalDateTime updatedAt) {
    }
}
