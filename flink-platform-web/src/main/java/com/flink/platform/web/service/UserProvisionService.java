package com.flink.platform.web.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flink.platform.common.model.UserRoles;
import com.flink.platform.dao.entity.User;
import com.flink.platform.dao.service.UserService;
import com.flink.platform.web.config.auth.SsoUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Auto-provisions a local {@link User} from SSO attributes on first login. */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class UserProvisionService {

    private final UserService userService;

    public Long provision(SsoUser ssoUser) {
        // Lookup existed user by externalId or username
        var user = userService.getOne(new QueryWrapper<User>()
                .lambda()
                .select(User::getId, User::getExternalId, User::getUsername)
                .eq(User::getExternalId, ssoUser.externalId())
                .last("limit 1"));
        if (user == null) {
            user = userService.getOne(new QueryWrapper<User>()
                    .lambda()
                    .select(User::getId, User::getExternalId, User::getUsername)
                    .eq(User::getUsername, ssoUser.username())
                    .last("limit 1"));
        }

        if (user != null) {
            if (!ssoUser.username().equals(user.getUsername())
                    || !ssoUser.externalId().equals(user.getExternalId())) {
                user.setUsername(ssoUser.username());
                user.setExternalId(ssoUser.externalId());
                userService.updateById(user);
            }
            return user.getId();
        }

        // first login, create new user
        user = new User();
        user.setExternalId(ssoUser.externalId());
        user.setUsername(ssoUser.username());
        user.setEmail(ssoUser.email());
        // user.setStatus(NORMAL);
        user.setRoles(new UserRoles());
        userService.save(user);
        log.info("Auto-provisioned user: {}", ssoUser);
        return user.getId();
    }
}
