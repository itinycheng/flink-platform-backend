package com.itiger.persona.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * hive service
 *
 * @author tiny.wang
 */
@Slf4j
@Service
@DS("hive")
@CacheConfig(cacheManager = "cache_manager")
public class HiveService {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Cacheable("hiveRtn")
    public List<String> getList(String sql) {
        return jdbcTemplate.queryForList(sql, String.class);
    }

}
