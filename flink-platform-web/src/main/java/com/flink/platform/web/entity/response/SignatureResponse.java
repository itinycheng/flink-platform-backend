package com.flink.platform.web.entity.response;

import com.flink.platform.web.entity.Signature;
import com.flink.platform.web.entity.SignatureValue;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

/** signature response. */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
public class SignatureResponse extends Signature {

    private List<SignatureValue> values;
}
