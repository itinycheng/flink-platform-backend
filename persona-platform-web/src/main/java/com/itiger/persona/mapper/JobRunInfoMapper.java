package com.itiger.persona.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itiger.persona.entity.JobRunInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * job run info Mapper 接口
 * </p>
 *
 * @author shik
 * @since 2021-04-14
 */
@Repository
@Mapper
public interface JobRunInfoMapper extends BaseMapper<JobRunInfo> {
    String SELECT_JOB_STATES = "<script>" +
            "select \n" +
            "back_info \n" +
            "id \n" +
            "status \n" +
            "from signature\n" +
            "where status in (2,3,4)\n" +
            "</script>";
    @Select(SELECT_JOB_STATES)
    List<Map<String,Object>> selectJobStates();

    String SELECT_BY_ID = "<script>" +
            "select \n" +
            "* \n" +
            "from signature\n" +
            "where id = #{id}\n" +
            "</script>";
    @Select(SELECT_BY_ID)
    List<Map<String,Object>> selectById(@Param("id") Integer id);

    String UPDATE_JOB_STATES = "<script>" +
            "update \n" +
            "signature\n" +
            "set status = #{status} \n" +
            "where id = #{id}\n" +
            "</script>";
    @Update(UPDATE_JOB_STATES)
    void updateJobState(@Param("id") Integer id, @Param("status") Integer status);
}
