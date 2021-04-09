package com.itiger.persona.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.itiger.persona.entity.Signature;
import com.itiger.persona.service.ISignatureService;
import com.itiger.persona.service.mapper.SignatureMapper;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author shik
 * @since 2020-10-16
 */
@Service
@DS("master_signature")
public class SignatureServiceImpl extends ServiceImpl<SignatureMapper, Signature> implements ISignatureService {

}
