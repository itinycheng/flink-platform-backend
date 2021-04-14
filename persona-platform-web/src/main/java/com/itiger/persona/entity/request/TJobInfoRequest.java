package com.itiger.persona.entity.request;

import com.itiger.persona.entity.JobInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * @Author Shik
 * @Title: TJobInfoRequest
 * @ProjectName: persona-platform-backend
 * @Description: TODO
 * @Date: 2021/4/14 上午10:50
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
public class TJobInfoRequest extends JobInfo {
}
