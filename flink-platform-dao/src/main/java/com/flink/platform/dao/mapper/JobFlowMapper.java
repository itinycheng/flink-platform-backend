package com.flink.platform.dao.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import com.flink.platform.dao.entity.JobFlow;
import com.flink.platform.dao.view.JobFlowDetails;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

/** job flow info Mapper. */
public interface JobFlowMapper extends BaseMapper<JobFlow> {

    @Select("""
            select f.*
            from t_job j, t_job_flow f
            where j.flow_id = f.id
            and j.id = #{jobId}
            limit 1
            """)
    JobFlow queryJobFlowByJobId(@Param("jobId") Long jobId);

    @Results({
        @Result(property = "config", column = "config", typeHandler = Jackson3TypeHandler.class),
        @Result(property = "tags", column = "tags", typeHandler = Jackson3TypeHandler.class),
        @Result(property = "alerts", column = "alerts", typeHandler = Jackson3TypeHandler.class),
        @Result(property = "timeout", column = "timeout", typeHandler = Jackson3TypeHandler.class),
        @Result(property = "params", column = "params", typeHandler = Jackson3TypeHandler.class),
    })
    @Select("""
            SELECT id, code, name, user_id, workspace_id, description, type, cron_expr,
                   priority, config, tags, alerts, timeout, params, status,
                   create_time, update_time,
                   (SELECT u.username FROM t_user u WHERE u.id = f.user_id) AS username
            FROM t_job_flow f ${ew.customSqlSegment}
            """)
    IPage<JobFlowDetails> selectPageDetails(
            IPage<JobFlowDetails> page, @Param(Constants.WRAPPER) Wrapper<JobFlow> wrapper);
}
