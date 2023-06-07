package com.flink.platform.web.entity.request;

import com.flink.platform.dao.entity.User;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Delegate;

import static com.flink.platform.common.util.Preconditions.checkNotNull;

/** user request. */
@NoArgsConstructor
public class UserRequest {

    @Getter @Delegate private final User user = new User();

    @Getter @Setter private String token;

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
        return checkNotNull(getId(), "The user id cannot be null");
    }

    public String usernameNotNull() {
        return checkNotNull(getUsername(), "The username cannot be null");
    }

    public String passwordNotNull() {
        return checkNotNull(getPassword(), "The password cannot be null");
    }

    public String emailNotNull() {
        return checkNotNull(getEmail(), "The email cannot be null");
    }
}
