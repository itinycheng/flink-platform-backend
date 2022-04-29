package com.flink.platform.web.entity.request;

import com.flink.platform.common.util.Preconditions;
import lombok.Data;
import lombok.NoArgsConstructor;

/** user request. */
@Data
@NoArgsConstructor
public class ReactiveRequest {

    private Long datasourceId;

    private String content;

    public String validate() {
        String msg = contentNotNull();
        if (msg != null) {
            return msg;
        }

        return idNotNull();
    }

    public String idNotNull() {
        return Preconditions.checkNotNull(datasourceId, "The datasource id cannot be null");
    }

    public String contentNotNull() {
        return Preconditions.checkNotNull(content, "The content cannot be null");
    }
}
