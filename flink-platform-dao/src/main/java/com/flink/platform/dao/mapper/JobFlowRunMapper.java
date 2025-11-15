package com.flink.platform.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.flink.platform.dao.entity.JobFlowRun;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

/** job flow run instance Mapper. */
public interface JobFlowRunMapper extends BaseMapper<JobFlowRun> {

    @Results(
            id = "querySharedVarsForUpdate",
            value = {
                @Result(
                        property = "shared_vars",
                        column = "shared_vars",
                        typeHandler = JacksonTypeHandler.class,
                        javaType = Map.class)
            })
    @Select("SELECT id, shared_vars FROM t_job_flow_run WHERE id = #{id} FOR UPDATE")
    JobFlowRun querySharedVarsForUpdate(@Param("id") Long id);
}
