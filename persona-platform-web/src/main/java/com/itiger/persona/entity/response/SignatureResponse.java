package com.itiger.persona.entity.response;

import com.itiger.persona.entity.Signature;
import com.itiger.persona.entity.SignatureValue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * @Author Shik
 * @Title: SignatureResponse
 * @ProjectName: persona-platform-backend
 * @Description: TODO
 * @Date: 2021/5/14 下午4:09
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
public class SignatureResponse extends Signature {

    private List<SignatureValue> values;

}
