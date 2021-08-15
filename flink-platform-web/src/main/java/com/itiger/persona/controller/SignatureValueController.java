package com.itiger.persona.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.itiger.persona.common.enums.ResponseStatus;
import com.itiger.persona.common.exception.DefinitionException;
import com.itiger.persona.entity.SignatureValue;
import com.itiger.persona.entity.response.ResultInfo;
import com.itiger.persona.service.ISignatureValueService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Objects;

/**
 * @Author Shik
 * @Title: SignatureValueController
 * @ProjectName: flink-platform-backend
 * @Description: TODO
 * @Date: 2021/5/17 下午2:46
 */
@RestController
@RequestMapping("/signature-value")
public class SignatureValueController {

    @Autowired
    private ISignatureValueService iSignatureValueService;

    @GetMapping("list")
    public ResultInfo list(HttpServletRequest request) {

        List<SignatureValue> list = this.iSignatureValueService.list();

        return ResultInfo.success(list);

    }

    @GetMapping("list/{sign_id}")
    public ResultInfo list(@PathVariable Long sign_id, HttpServletRequest request) {

        List<SignatureValue> list = this.iSignatureValueService.list(new QueryWrapper<SignatureValue>().lambda().eq(SignatureValue::getSignId, sign_id));

        return ResultInfo.success(list);

    }

    @PostMapping
    public ResultInfo saveOrUpdate(HttpServletRequest request, @RequestBody SignatureValue signatureValue) {
        if (StringUtils.isNotBlank(signatureValue.getValue())) {

            // save
            if (Objects.isNull(signatureValue.getId())) {
                SignatureValue one = this.iSignatureValueService.getOne(
                        new QueryWrapper<SignatureValue>().lambda()
                                .eq(SignatureValue::getSignId, signatureValue.getSignId())
                                .eq(SignatureValue::getValue, signatureValue.getValue()));

                if (Objects.isNull(one)) {
                    Boolean save = this.iSignatureValueService.save(signatureValue);
                    return ResultInfo.success(save);
                } else {
                    throw new DefinitionException(ResponseStatus.ERROR_PARAMETER);
                }
            } else {
                // update
                Boolean save = this.iSignatureValueService.updateById(signatureValue);
                return ResultInfo.success(save);
            }
        } else {
            throw new DefinitionException(ResponseStatus.ERROR_PARAMETER);
        }

    }


}
