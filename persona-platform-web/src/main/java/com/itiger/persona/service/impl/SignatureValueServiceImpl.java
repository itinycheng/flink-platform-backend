package com.itiger.persona.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.itiger.persona.entity.SignatureValue;
import com.itiger.persona.mapper.SignatureValueMapper;
import com.itiger.persona.service.ISignatureValueService;
import org.springframework.stereotype.Service;

/**
 * @Author Shik
 * @Title: SignatureValueServiceImpl
 * @ProjectName: persona-platform-backend
 * @Description: TODO
 * @Date: 2021/5/17 上午10:45
 */
@Service
@DS("signature")
public class SignatureValueServiceImpl extends ServiceImpl<SignatureValueMapper, SignatureValue> implements ISignatureValueService {
}
