package com.flink.platform.web.entity.request;

import com.flink.platform.common.util.Preconditions;
import com.flink.platform.dao.entity.Worker;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Delegate;

/** user request. */
@NoArgsConstructor
public class WorkerRequest {

    @Getter @Delegate private final Worker worker = new Worker();

    public String validateOnCreate() {
        String msg = nameNotNull();
        if (msg != null) {
            return msg;
        }

        msg = ipNotNull();
        if (msg != null) {
            return msg;
        }

        return portNotNull();
    }

    public String validateOnUpdate() {
        return idNotNull();
    }

    public String idNotNull() {
        return Preconditions.checkNotNull(getId(), "The worker id cannot be null");
    }

    public String nameNotNull() {
        return Preconditions.checkNotNull(getName(), "The name cannot be null");
    }

    public String ipNotNull() {
        return Preconditions.checkNotNull(getIp(), "The ip cannot be null");
    }

    public String portNotNull() {
        return Preconditions.checkNotNull(getPort(), "The port cannot be null");
    }
}
