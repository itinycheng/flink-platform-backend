package com.flink.platform.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flink.platform.web.entity.JobHistory;
import org.apache.ibatis.annotations.Mapper;

/** job modify info Mapper. */
@Mapper
public interface JobHistoryMapper extends BaseMapper<JobHistory> {}
