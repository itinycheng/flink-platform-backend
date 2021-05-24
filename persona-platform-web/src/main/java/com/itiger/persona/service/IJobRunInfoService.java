package com.itiger.persona.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.itiger.persona.entity.JobRunInfo;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * job run info 服务类
 * </p>
 *
 * @author shik
 * @since 2021-04-14
 */
public interface IJobRunInfoService extends IService<JobRunInfo> {

    List<Map<String,Object>> listAllRunningJobs();

    List<Map<String,Object>> selectById(Integer id);

    void updateJobState(Integer id, Integer status) throws Exception;
}
