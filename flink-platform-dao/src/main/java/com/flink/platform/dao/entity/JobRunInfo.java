package com.flink.platform.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.flink.platform.common.enums.DeployMode;
import com.flink.platform.common.enums.ExecutionMode;
import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.common.enums.JobType;
import com.flink.platform.dao.entity.result.JobCallback;
import com.flink.platform.dao.entity.task.BaseJob;
import com.flink.platform.dao.handler.Jackson3TypeHandler;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.time.DurationFormatUtils;

import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

import static com.flink.platform.common.constants.Constant.EMPTY;
import static org.apache.commons.lang3.StringUtils.defaultString;

/** Job run info. */
@Data
@NoArgsConstructor
@TableName(value = "t_job_run", autoResultMap = true)
public class JobRunInfo implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String name;

    private Long jobId;

    private Long flowRunId;

    private Long userId;

    private JobType type;

    private String version;

    private DeployMode deployMode;

    private ExecutionMode execMode;

    @TableField(typeHandler = Jackson3TypeHandler.class)
    private BaseJob config;

    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> params;

    private String subject;

    @TableField(typeHandler = Jackson3TypeHandler.class)
    private LongArrayList routeUrl;

    private String host;

    private ExecutionStatus status;

    /** store json data of JobStatistics. */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private JobCallback backInfo;

    /** submit time. */
    private LocalDateTime submitTime;

    /** end time. */
    @TableField("stop_time")
    private LocalDateTime endTime;

    @Setter(AccessLevel.NONE)
    private LocalDateTime createTime;

    @JsonIgnore
    public String getJobCode() {
        return "job_" + jobId;
    }

    public String getDuration() {
        if (submitTime == null || endTime == null) {
            return EMPTY;
        }

        try {
            Duration duration = Duration.between(submitTime, endTime);
            return DurationFormatUtils.formatDuration(duration.toMillis(), "HH:mm:ss");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Estimated data length. <br>
     * The type of back_info column is TEXT and max size of TEXT is 64K.<br>
     * Storing data in HDFS is a better solution.
     */
    public void setTrimmedBackInfo(JobCallback backInfo) {
        if (backInfo != null) {
            int maxLen = 50_000;
            int count = 0;

            // error msg.
            String errMsg = defaultString(backInfo.getErrMsg());
            count = count + errMsg.length();
            if (count >= maxLen) {
                backInfo.setErrMsg(errMsg.substring(0, maxLen));
                backInfo.setMessage(null);
                backInfo.setStdMsg(null);
            }

            // message.
            if (count < maxLen) {
                String message = defaultString(backInfo.getMessage());
                int prevCount = count;
                count = count + message.length();
                if (count >= maxLen) {
                    backInfo.setMessage(message.substring(0, maxLen - prevCount));
                    backInfo.setStdMsg(null);
                }
            }

            // std msg.
            if (count < maxLen) {
                String stdMsg = defaultString(backInfo.getStdMsg());
                int prevCount = count;
                count = count + stdMsg.length();
                if (count >= maxLen) {
                    backInfo.setStdMsg(stdMsg.substring(0, maxLen - prevCount));
                }
            }
        }
        setBackInfo(backInfo);
    }
}
