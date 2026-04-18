package com.flink.platform.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import com.flink.platform.dao.entity.JobFlowRun;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** job flow run instance Mapper. */
public interface JobFlowRunMapper extends BaseMapper<JobFlowRun> {

    @Results(
            id = "queryParamsForUpdate",
            value = {
                @Result(
                        property = "params",
                        column = "params",
                        typeHandler = Jackson3TypeHandler.class,
                        javaType = Map.class)
            })
    @Select("SELECT id, params FROM t_job_flow_run WHERE id = #{id} FOR UPDATE")
    JobFlowRun queryParamsForUpdate(@Param("id") Long id);

    @Select("""
            <script>
                SELECT status, count(id) as count
                FROM t_job_flow_run
                WHERE workspace_id = #{workspaceId}
                <if test="startTime != null and endTime != null">
                    AND (end_time IS NULL OR end_time BETWEEN #{startTime} AND #{endTime})
                </if>
                GROUP BY status
            </script>
            """)
    List<Map<String, Object>> countJobFlowRunGroupByStatus(
            @Param("workspaceId") Long workspaceId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);
}
