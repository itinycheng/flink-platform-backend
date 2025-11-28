package com.flink.platform.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.common.enums.JobFlowStatus;
import com.flink.platform.common.enums.JobFlowType;
import com.flink.platform.common.enums.JobStatus;
import com.flink.platform.dao.entity.JobRunInfo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** job run info Mapper. */
public interface JobRunInfoMapper extends BaseMapper<JobRunInfo> {

    @Select("""
                    <script>
                    SELECT t1.id, t1.name, t1.job_id, t1.flow_run_id, t1.user_id, t1.type, t1.version,
                    t1.deploy_mode, t1.exec_mode, t1.host, t1.status, t1.submit_time, t1.stop_time,t1.create_time
                    FROM t_job_run t1,
                    (select max(id) as id from t_job_run where 1 = 1
                         <if test="flowRunId != null">
                            and flow_run_id = #{flowRunId}
                         </if>
                        <if test="jobIds != null and jobIds.size() > 0">
                            and job_id in
                            <foreach collection="jobIds" item="jobId" open='(' close=')' separator=','>
                                #{jobId}
                            </foreach>
                        </if>
                    GROUP BY job_id) t2
                    WHERE t1.id = t2.id
                    </script>
                    """)
    List<JobRunInfo> lastJobRunList(@Param("flowRunId") Long flowRunId, @Param("jobIds") List<Long> jobIds);

    @Select("""
            <script>
                SELECT t1.id,
                    t1.name,
                    t1.job_id,
                    t1.flow_run_id,
                    t1.user_id,
                    t1.type,
                    t1.version,
                    t1.deploy_mode,
                    t1.exec_mode,
                    t1.host,
                    t1.status,
                    t1.submit_time,
                    t1.stop_time,
                    t1.create_time
                FROM t_job_run t1,
                    (
                        SELECT max(r.id) as run_id
                        FROM t_job_flow f,
                            t_job j,
                            t_job_run r
                        WHERE f.id = j.flow_id
                            AND j.id = r.job_id
                            <if test="jobFlowType != null">
                                AND f.`type` = #{jobFlowType}
                            </if>
                            <if test="jobFlowStatus != null">
                                AND f.`status` = #{jobFlowStatus}
                            </if>
                            <if test="jobStatus != null">
                                AND j.status = #{jobStatus}
                            </if>
                        GROUP BY r.job_id
                    ) t2
                WHERE t1.id = t2.run_id
                <if test="jobRunStatusList != null and jobRunStatusList.size() > 0">
                    AND t1.status in
                    <foreach collection="jobRunStatusList" item="jobRunStatus" open='(' close=')' separator=','>
                        #{jobRunStatus}
                    </foreach>
                </if>
            </script>
            """)
    List<JobRunInfo> queryLastJobRuns(
            @Param("jobFlowType") JobFlowType jobFlowType,
            @Param("jobFlowStatus") JobFlowStatus jobFlowStatus,
            @Param("jobStatus") JobStatus jobStatus,
            @Param("jobRunStatusList") List<ExecutionStatus> jobRunStatusList);
}
