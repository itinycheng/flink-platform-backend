package com.flink.platform.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import com.flink.platform.dao.entity.JobFlow;
import com.flink.platform.dao.query.JobFlowPageQuery;
import com.flink.platform.dao.view.JobFlowDetails;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

/** job flow info Mapper. */
public interface JobFlowMapper extends BaseMapper<JobFlow> {

    @Select("""
            <script>
            select f.*
            from t_job j, t_job_flow f
            where j.flow_id = f.id
            and j.id = #{jobId}
            limit 1
            </script>
            """)
    JobFlow queryJobFlowByJobId(@Param("jobId") Long jobId);

    @Results(
            id = "jobFlowDetailsMap",
            value = {
                @Result(property = "config", column = "config", typeHandler = Jackson3TypeHandler.class),
                @Result(property = "tags", column = "tags", typeHandler = Jackson3TypeHandler.class),
                @Result(property = "alerts", column = "alerts", typeHandler = Jackson3TypeHandler.class),
                @Result(property = "timeout", column = "timeout", typeHandler = Jackson3TypeHandler.class),
                @Result(property = "params", column = "params", typeHandler = Jackson3TypeHandler.class),
            })
    @Select("""
            <script>
            SELECT f.id, f.code, f.name, f.user_id, f.workspace_id, f.description,
                   f.type, f.cron_expr, f.priority, f.config, f.tags, f.alerts,
                   f.timeout, f.params, f.status, f.create_time, f.update_time,
                   u.username
            FROM t_job_flow f
            LEFT JOIN t_user u ON f.user_id = u.id
            WHERE f.workspace_id = #{q.workspaceId}
                <if test="q.id != null">AND f.id = #{q.id}</if>
                <if test="q.type != null">AND f.type = #{q.type}</if>
                <if test="q.name != null and q.name != ''">AND f.name LIKE CONCAT('%', #{q.name}, '%')</if>
                <if test="q.tag != null and q.tag != ''">AND f.tags LIKE CONCAT('%', #{q.tag}, '%')</if>
                <choose>
                    <when test="q.status != null">AND f.status = #{q.status}</when>
                    <otherwise>AND f.status != 'DELETE'</otherwise>
                </choose>
                <if test="q.sortByIdDesc">ORDER BY f.id DESC</if>
            </script>
            """)
    IPage<JobFlowDetails> selectDetailsPage(IPage<JobFlowDetails> page, @Param("q") JobFlowPageQuery query);
}
