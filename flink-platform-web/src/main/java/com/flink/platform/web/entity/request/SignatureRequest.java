package com.flink.platform.web.entity.request;

import com.flink.platform.web.entity.Signature;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

/** Signature request. */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
public class SignatureRequest extends Signature {

    private List<String> accountTypeList;
}
