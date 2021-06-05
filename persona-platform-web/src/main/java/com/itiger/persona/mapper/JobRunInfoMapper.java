package com.itiger.persona.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itiger.persona.entity.JobRunInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.springframework.stereotype.Repository;

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

    /**
     * get latest run info by job_id
     *
     * @param jobId job id
     * @return job run info
     */
    @Select("select * from t_job_run_info where job_id = #{jobId} order by id desc limit 1")
    JobRunInfo selectLatestByJobId(Long jobId);

    /**
     * update result size
     *
     * @param id   id
     * @param size result size
     * @return updated items size
     */
    @Update("update t_job_run_info set result_size = #{size} where id = #{id}")
    int updateResultSize(Long id, Long size);
}
