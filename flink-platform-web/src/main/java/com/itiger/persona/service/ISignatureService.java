package com.itiger.persona.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.itiger.persona.entity.Signature;
import com.itiger.persona.entity.response.SignatureResponse;

import java.util.List;
import java.util.Map;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author shik
 * @since 2020-10-16
 */
public interface ISignatureService extends IService<Signature> {

    List<Map<String,Object>> listAll();
}
