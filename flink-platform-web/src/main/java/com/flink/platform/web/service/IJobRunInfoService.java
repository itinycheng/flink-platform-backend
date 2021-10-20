package com.flink.platform.web.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.flink.platform.web.entity.JobRunInfo;

/** job run info. */
public interface IJobRunInfoService extends IService<JobRunInfo> {

    /** get latest by job id. */
    JobRunInfo getLatestByJobId(Long jobId);
}
