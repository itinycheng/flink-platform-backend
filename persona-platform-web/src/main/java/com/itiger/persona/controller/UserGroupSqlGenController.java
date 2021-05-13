package com.itiger.persona.controller;

import com.itiger.persona.constants.UserGroupConst;
import com.itiger.persona.entity.response.ResultInfo;
import com.itiger.persona.parser.SqlSelect;
import com.itiger.persona.service.UserGroupSqlGenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author tiny.wang
 */
@RestController
@RequestMapping("/userGroup/sqlGenerator")
public class UserGroupSqlGenController {

    @Autowired
    private UserGroupSqlGenService sqlGenService;

    @PostMapping(value = "insertSelect")
    public ResultInfo insertSelect(@RequestBody SqlSelect sqlSelect) {
        sqlSelect.setFrom(UserGroupConst.SOURCE_TABLE_IDENTIFIER);
        String sqlString = sqlGenService.generateInsertSelect(sqlSelect);
        return ResultInfo.success(sqlString);
    }
}
