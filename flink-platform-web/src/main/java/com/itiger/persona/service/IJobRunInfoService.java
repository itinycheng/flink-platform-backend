package com.itiger.persona.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.itiger.persona.entity.JobRunInfo;

/**
 * <p>
 * job run info 服务类
 * </p>
 *
 * @author shik
 * @since 2021-04-14
 */
public interface IJobRunInfoService extends IService<JobRunInfo> {

    /**
     * get latest by job id
     *
     * @param jobId job id
     * @return job run info
     */
    JobRunInfo getLatestByJobId(Long jobId);
}
