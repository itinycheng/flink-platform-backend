package com.itiger.persona.entity.request;

import com.itiger.persona.entity.Signature;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * @Author Shik
 * @Title: SignatureRequest
 * @ProjectName: datapipeline
 * @Description: TODO
 * @Date: 2021/3/31 下午12:03
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
public class SignatureRequest extends Signature {

    private List<String> accountTypeList;
}
