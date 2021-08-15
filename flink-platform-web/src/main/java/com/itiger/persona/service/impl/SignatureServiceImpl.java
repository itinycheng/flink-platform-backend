package com.itiger.persona.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.itiger.persona.entity.Signature;
import com.itiger.persona.mapper.SignatureMapper;
import com.itiger.persona.service.ISignatureService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author shik
 * @since 2020-10-16
 */
@Service
@DS("signature")
public class SignatureServiceImpl extends ServiceImpl<SignatureMapper, Signature> implements ISignatureService {

    @Autowired
    private SignatureMapper signatureMapper;

    @Override
    public List<Map<String,Object>> listAll() {
        return signatureMapper.selectValues();
    }
}
