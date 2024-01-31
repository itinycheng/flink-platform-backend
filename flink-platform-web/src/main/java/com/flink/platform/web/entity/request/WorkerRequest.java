package com.flink.platform.web.entity.request;

import com.flink.platform.dao.entity.Worker;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Delegate;

import static com.flink.platform.common.util.Preconditions.requireNotNull;

/** user request. */
@Getter
@NoArgsConstructor
public class WorkerRequest {

    @Delegate
    private final Worker worker = new Worker();

    public String validateOnCreate() {
        String msg = nameNotNull();
        if (msg != null) {
            return msg;
        }

        msg = ipNotNull();
        if (msg != null) {
            return msg;
        }

        msg = portNotNull();
        if(msg != null){
            return msg;
        }

        return roleNotNull();
    }

    public String validateOnUpdate() {
        return idNotNull();
    }

    public String idNotNull() {
        return requireNotNull(getId(), "The worker id cannot be null");
    }

    public String nameNotNull() {
        return requireNotNull(getName(), "The name cannot be null");
    }

    public String ipNotNull() {
        return requireNotNull(getIp(), "The ip cannot be null");
    }

    public String portNotNull() {
        return requireNotNull(getPort(), "The port cannot be null");
    }

    public String roleNotNull(){
        return requireNotNull(getRole(), "The role cannot be null");
    }
}
