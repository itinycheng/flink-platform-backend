package com.flink.platform.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flink.platform.dao.entity.JobFlow;
import org.apache.ibatis.annotations.Param;
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
}
