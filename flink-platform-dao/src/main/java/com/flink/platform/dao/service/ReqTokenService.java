package com.flink.platform.dao.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.flink.platform.common.exception.DefinitionException;
import com.flink.platform.dao.entity.ReqToken;
import com.flink.platform.dao.mapper.ReqTokenMapper;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/** Req token service impl. */
@Service
@DS("master_1")
@CacheConfig(cacheNames = "req_token", cacheManager = "token_cache")
public class ReqTokenService extends ServiceImpl<ReqTokenMapper, ReqToken> {

    @Cacheable(key = "'token:'+#p0")
    public ReqToken getByToken(String token) {

        return super.getOne(new QueryWrapper<ReqToken>().lambda().eq(ReqToken::getToken, token));
    }

    @CachePut(key = "'token:'+#reqToken.token")
    public ReqToken saveCache(ReqToken reqToken) {
        boolean save = super.save(reqToken);
        if (save) {
            return reqToken;
        } else {
            throw new DefinitionException();
        }
    }
}
