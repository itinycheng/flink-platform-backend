package com.itiger.persona.controller;

import com.itiger.persona.constants.UserGroupConst;
import com.itiger.persona.entity.response.ResultInfo;
import com.itiger.persona.parser.SqlIdentifier;
import com.itiger.persona.parser.SqlSelect;
import com.itiger.persona.service.UserGroupSqlGenService;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;

import static com.itiger.persona.constants.UserGroupConst.COMMON;
import static com.itiger.persona.constants.UserGroupConst.UUID;

/**
 * @author tiny.wang
 */
@RestController
@RequestMapping("/userGroup/sqlGenerator")
public class UserGroupSqlGenController {

    @Resource
    private UserGroupSqlGenService sqlGenService;

    @PostMapping(value = "insertSelect")
    public ResultInfo insertSelect(@RequestBody SqlSelect sqlSelect) {
        // set default table for query from
        sqlSelect.setFrom(UserGroupConst.SOURCE_TABLE_IDENTIFIER);
        // set default select list
        List<SqlIdentifier> identifiers = sqlSelect.getWhere().exhaustiveSqlIdentifiers();
        String qualifier = CollectionUtils.isEmpty(identifiers) ? COMMON : identifiers.get(0).getQualifier();
        sqlSelect.setSelectList(Collections.singletonList(SqlIdentifier.of(qualifier, UUID)));
        // generate sql
        String sqlString = sqlGenService.generateInsertSelect(sqlSelect);
        return ResultInfo.success(sqlString);
    }
}
