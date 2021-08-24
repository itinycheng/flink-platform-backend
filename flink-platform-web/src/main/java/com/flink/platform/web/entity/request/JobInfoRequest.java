package com.flink.platform.web.entity.request;

import com.flink.platform.web.entity.JobInfo;
import com.flink.platform.web.enums.DeployMode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * @Author Shik
 * @Title: TJobInfoRequest
 * @ProjectName: flink-platform-backend
 * @Description: TODO
 * @Date: 2021/4/14 上午10:50
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
public class JobInfoRequest extends JobInfo {

    private String sqlMain;

    private DeployMode deployMode;

    private List<String> catalogIds;
}
