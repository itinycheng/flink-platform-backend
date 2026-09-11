package com.flink.platform.dao.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import com.flink.platform.dao.entity.JobFlowDag;
import com.flink.platform.dao.entity.JobInfo;
import com.flink.platform.dao.entity.task.BaseJob;
import com.flink.platform.dao.view.JobDetails;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** job config info Mapper. */
public interface JobInfoMapper extends BaseMapper<JobInfo> {

    @Results({
        @Result(
                property = "config",
                column = "config",
                typeHandler = Jackson3TypeHandler.class,
                javaType = BaseJob.class),
        @Result(
                property = "jobFlowDag",
                column = "job_flow_dag",
                typeHandler = Jackson3TypeHandler.class,
                javaType = JobFlowDag.class)
    })
    @Select("""
            select j.id, j.config, f.id as job_flow_id,
                   f.type as job_flow_type, f.flow as job_flow_dag
            from t_job j, t_job_flow f
            where j.flow_id = f.id
            and j.config like CONCAT('%', #{flowId}, '%')
            and j.type = 'SUB_FLOW'
            and f.status in ('ONLINE', 'SCHEDULING')
            """)
    List<JobDetails> queryRunnableJobUsingJobFlow(@Param("flowId") Long flowId);

    @Select("""
            SELECT id, name, description, flow_id, user_id, workspace_id, type, version,
                   deploy_mode, exec_mode, status, create_time, update_time
            FROM t_job ${ew.customSqlSegment}
            """)
    IPage<JobDetails> selectPageDetails(IPage<JobDetails> page, @Param(Constants.WRAPPER) Wrapper<JobInfo> wrapper);
}
