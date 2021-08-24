package com.flink.platform.web.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.flink.platform.web.entity.SignatureValue;
import com.flink.platform.web.mapper.SignatureValueMapper;
import com.flink.platform.web.service.ISignatureValueService;
import org.springframework.stereotype.Service;

/**
 * @Author Shik
 * @Title: SignatureValueServiceImpl
 * @ProjectName: flink-platform-backend
 * @Description: TODO
 * @Date: 2021/5/17 上午10:45
 */
@Service
@DS("signature")
public class SignatureValueServiceImpl extends ServiceImpl<SignatureValueMapper, SignatureValue> implements ISignatureValueService {
}
