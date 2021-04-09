package com.itiger.persona.service;

import com.alibaba.fastjson.JSON;
import com.itiger.persona.constants.CacheKeys;
import com.itiger.persona.entity.Signature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @Author Shik
 * @Title: RedisService
 * @ProjectName: datapipeline
 * @Description: TODO
 * @Date: 2020/11/23 下午3:02
 */
@Service
@Slf4j
public class RedisService {

    private static final int SIZE = 50000;

    @Autowired
    private ISignatureService iSignatureService;


    @Autowired
    private RedisTemplate redisTemplate;


    public void initSignatureInfo() {
        List<Signature> signatureList = this.iSignatureService.list();
        this.redisTemplate.delete(CacheKeys.SIGNATURE_INFO);
        signatureList.stream().parallel()
                .forEach(signature ->
                        redisTemplate.opsForHash().put(CacheKeys.SIGNATURE_INFO,
                                signature.getName(), JSON.toJSONString(signature)));
    }

}
