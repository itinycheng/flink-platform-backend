package com.itiger.persona.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.itiger.persona.common.exception.DefinitionException;
import com.itiger.persona.entity.ReqToken;
import com.itiger.persona.mapper.ReqTokenMapper;
import com.itiger.persona.service.IReqTokenService;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author shik
 * @since 2021-03-03
 */
@Service
@DS("master_1")
@CacheConfig(cacheNames = "req_token", cacheManager = "token_cache")
public class ReqTokenServiceImpl extends ServiceImpl<ReqTokenMapper, ReqToken> implements IReqTokenService {

    @Override
    @Cacheable(key = "'token:'+#p0")
    public ReqToken getByToken(String token) {

        ReqToken one = super.getOne(new QueryWrapper<ReqToken>().lambda()
                .eq(ReqToken::getToken, token));

        return one;
    }

    @Override
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
