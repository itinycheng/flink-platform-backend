package com.flink.platform.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.flink.platform.dao.entity.JobInfo;
import com.flink.platform.dao.entity.task.BaseJob;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** job config info Mapper. */
public interface JobInfoMapper extends BaseMapper<JobInfo> {

    @Results(
            id = "queryRunnableJobUsingSubFlow",
            value = {
                @Result(
                        property = "config",
                        column = "config",
                        typeHandler = JacksonTypeHandler.class,
                        javaType = BaseJob.class)
            })
    @Select("""
                    <script>
                    select j.id, j.config, f.id as job_flow_id, f.status as job_flow_status
                    from t_job j, t_job_flow f
                    where j.flow_id = f.id
                    and j.config like CONCAT('%', #{flowId}, '%')
                    and j.type = 'SUB_FLOW'
                    and f.status in ('ONLINE', 'SCHEDULING')
                    </script>
                    """)
    List<JobInfo> queryRunnableJobUsingJobFlow(@Param("flowId") Long flowId);
}
