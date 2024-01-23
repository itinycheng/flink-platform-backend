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
                    + "SELECT t1.id, t1.name, t1.job_id, t1.flow_run_id, t1.user_id, t1.type, t1.version,\n"
                    + "t1.deploy_mode, t1.exec_mode, t1.host, t1.status, t1.submit_time, t1.stop_time,t1.create_time\n"
                    + "FROM t_job_run t1,\n"
                    + "(select max(id) as id from t_job_run where flow_run_id = #{flowRunId}\n"
                    + "    <if test='jobIds != null and jobIds.size() > 0'>\n"
                    + "        and job_id in\n"
                    + "        <foreach collection='jobIds' item='jobId' open='(' close=')' separator=','>\n"
                    + "            #{jobId}\n"
                    + "        </foreach>\n"
                    + "    </if>\n"
                    + "GROUP BY job_id) t2\n"
                    + "WHERE t1.id = t2.id\n"
                    + "</script>\n")
    List<JobRunInfo> lastJobRunList(
            @Param("flowRunId") Long flowRunId, @Param("jobIds") List<Long> jobIds);
}
