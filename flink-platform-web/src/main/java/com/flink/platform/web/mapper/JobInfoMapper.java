package com.flink.platform.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flink.platform.web.entity.JobInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * job config info Mapper 接口
 * </p>
 *
 * @author shik
 * @since 2021-04-14
 */
@Mapper
public interface JobInfoMapper extends BaseMapper<JobInfo> {

}
