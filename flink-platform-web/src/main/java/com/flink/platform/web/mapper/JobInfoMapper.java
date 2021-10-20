package com.flink.platform.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flink.platform.web.entity.JobInfo;
import org.apache.ibatis.annotations.Mapper;

/** job config info Mapper. */
@Mapper
public interface JobInfoMapper extends BaseMapper<JobInfo> {}
