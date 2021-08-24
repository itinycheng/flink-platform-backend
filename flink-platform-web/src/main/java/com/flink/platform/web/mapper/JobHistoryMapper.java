package com.flink.platform.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flink.platform.web.entity.JobHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * job modify info Mapper 接口
 * </p>
 *
 * @author shik
 * @since 2021-04-14
 */
@Mapper
public interface JobHistoryMapper extends BaseMapper<JobHistory> {

}
