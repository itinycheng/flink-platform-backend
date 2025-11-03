package com.flink.platform.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flink.platform.common.enums.JobType;
import com.flink.platform.dao.entity.JobInfo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** job config info Mapper. */
public interface JobInfoMapper extends BaseMapper<JobInfo> {

    @Select(
            """
                    <script>
                    select j.id, j.config, f.id as job_flow_id, f.status as job_flow_status
                    from t_job j, t_job_flow f
                    where j.flow_id = f.id
                    and j.config like CONCAT('%', #{flowId}, '%')
                    and j.type = #{jobType}
                    </script>
                    """)
    List<JobInfo> queryJobConfigAndFlowStatus(@Param("flowId") Long flowId, @Param("jobType") JobType jobType);
}
