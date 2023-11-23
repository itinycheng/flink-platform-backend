package com.flink.platform.web.entity.request;

import com.flink.platform.dao.entity.User;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Delegate;

import static com.flink.platform.common.util.Preconditions.requireNotNull;

/** user request. */
@Getter
@NoArgsConstructor
public class UserRequest {

    @Delegate
    private final User user = new User();

    @Setter
    private String token;

    public String validateOnCreate() {
        String msg = usernameNotNull();
        if (msg != null) {
            return msg;
        }

        msg = passwordNotNull();
        if (msg != null) {
            return msg;
        }

        return emailNotNull();
    }

    public String validateOnUpdate() {
        return idNotNull();
    }

    public String idNotNull() {
        return requireNotNull(getId(), "The user id cannot be null");
    }

    public String usernameNotNull() {
        return requireNotNull(getUsername(), "The username cannot be null");
    }

    public String passwordNotNull() {
        return requireNotNull(getPassword(), "The password cannot be null");
    }

    public String emailNotNull() {
        return requireNotNull(getEmail(), "The email cannot be null");
    }
}
