package com.flink.platform.dao.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import com.flink.platform.dao.entity.JobFlow;
import com.flink.platform.dao.view.JobFlowDetails;
import com.flink.platform.dao.view.JobFlowLatestRun;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** job flow info Mapper. */
public interface JobFlowMapper extends BaseMapper<JobFlow> {

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

    @Select("""
            <script>
                SELECT f.id AS flow_id, f.cron_expr AS flow_cron_expr,
                       r.id AS latest_run_id, r.status AS latest_status, r.create_time AS latest_create_time
                FROM t_job_flow f
                    LEFT JOIN (SELECT flow_id, max(id) AS id
                               FROM t_job_flow_run
                               WHERE flow_id IN
                               <foreach collection="flowIds" item="flowId" open='(' close=')' separator=','>
                                   #{flowId}
                               </foreach>
                               GROUP BY flow_id) m ON m.flow_id = f.id
                    LEFT JOIN t_job_flow_run r ON r.id = m.id
                WHERE f.id IN
                <foreach collection="flowIds" item="flowId" open='(' close=')' separator=','>
                    #{flowId}
                </foreach>
            </script>
            """)
    List<JobFlowLatestRun> selectLatestRunPerFlow(@Param("flowIds") List<Long> flowIds);
}
