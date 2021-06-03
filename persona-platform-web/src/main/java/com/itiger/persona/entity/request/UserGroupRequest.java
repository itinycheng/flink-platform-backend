package com.itiger.persona.entity.request;

import com.itiger.persona.parser.SqlSelect;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * @author tiny.wang
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode
public class UserGroupRequest {

    private Long id;

    private String name;

    private String description;

    private String cronExpr;

    private String createUser;

    private String updateUser;

    private SqlSelect select;

}
