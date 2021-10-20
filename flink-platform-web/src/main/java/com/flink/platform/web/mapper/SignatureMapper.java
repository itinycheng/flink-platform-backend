package com.flink.platform.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flink.platform.web.entity.Signature;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/** Signature mapper. */
@Repository
@Mapper
public interface SignatureMapper extends BaseMapper<Signature> {

    String SELECT_VALUES =
            "<script>"
                    + "select \n"
                    + "signature.*, \n"
                    + "signature_value.id as `values.id`,\n"
                    + "signature_value.sign_id as `values.sign_id`,\n"
                    + "signature_value.`value` as `values.value`,\n"
                    + "signature_value.`desc` as `values.desc`\n"
                    + "from signature\n"
                    + "inner join signature_value on signature.id = signature_value.sign_id"
                    + "</script>";

    @Select(SELECT_VALUES)
    List<Map<String, Object>> selectValues();
}
