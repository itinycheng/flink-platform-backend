package com.flink.platform.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flink.platform.dao.entity.JobRunInfo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** job run info Mapper. */
public interface JobRunInfoMapper extends BaseMapper<JobRunInfo> {

    @Select(
            """
                    <script>
                    select id, name, job_id, flow_run_id, user_id, type, version, deploy_mode,
                    exec_mode, host, status, submit_time, stop_time, create_time
                    from t_job_run where id in (
                        select max(id) from t_job_run where flow_run_id = #{flowRunId}
                        <if test="jobIds != null and jobIds.size() > 0">
                            and job_id in
                            <foreach collection="jobIds" item="jobId" open='(' close=')' separator=','>
                                #{jobId}
                            </foreach>
                        </if>
                        group by job_id
                    )
                    </script>
                    """)
    List<JobRunInfo> lastJobRunList(@Param("flowRunId") Long flowRunId, @Param("jobIds") List<Long> jobIds);
}
