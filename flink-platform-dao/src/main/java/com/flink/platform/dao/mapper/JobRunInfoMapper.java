package com.flink.platform.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flink.platform.dao.entity.JobRunInfo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** job run info Mapper. */
public interface JobRunInfoMapper extends BaseMapper<JobRunInfo> {

    @Select(
            "<script>\n"
                    + "select id, name, job_id, flow_run_id, user_id, type, version, deploy_mode,\n"
                    + "exec_mode, host, status, submit_time, stop_time, create_time\n"
                    + "from t_job_run where id in (\n"
                    + "    select max(id) from t_job_run where flow_run_id = #{flowRunId}\n"
                    + "    <if test=\"jobIds != null and jobIds.size() > 0\">\n"
                    + "        and job_id in\n"
                    + "        <foreach collection=\"jobIds\" item=\"jobId\" open='(' close=')' separator=','>\n"
                    + "            #{jobId}\n"
                    + "        </foreach>\n"
                    + "    </if>\n"
                    + "    group by job_id\n"
                    + ")\n"
                    + "</script>\n")
    List<JobRunInfo> lastJobRunList(
            @Param("flowRunId") Long flowRunId, @Param("jobIds") List<Long> jobIds);
}
