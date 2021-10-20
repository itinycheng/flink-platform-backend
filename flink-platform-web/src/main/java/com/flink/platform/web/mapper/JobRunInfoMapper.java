package com.flink.platform.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flink.platform.web.entity.JobRunInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.springframework.stereotype.Repository;

/** job run info Mapper. */
@Repository
@Mapper
public interface JobRunInfoMapper extends BaseMapper<JobRunInfo> {

    /** get latest run info by job_id. */
    @Select("select * from t_job_run_info where job_id = #{jobId} order by id desc limit 1")
    JobRunInfo selectLatestByJobId(Long jobId);

    /** update result size. */
    @Update("update t_job_run_info set result_size = #{size} where id = #{id}")
    int updateResultSize(@Param("id") Long id, @Param("size") Long size);
}
