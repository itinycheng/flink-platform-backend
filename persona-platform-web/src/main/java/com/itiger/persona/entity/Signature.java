package com.itiger.persona.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.itiger.persona.common.enums.DataType;
import com.itiger.persona.flink.udf.business.AbstractTableFunction;
import com.itiger.persona.flink.udf.common.SqlColumn;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.val;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.List;

/**
 * <p>
 *
 * </p>
 *
 * @author shik
 * @since 2020-10-16
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Signature implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键id
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 标签名称
     */
    private String name;

    /**
     * 标签状态，1:有效，0:无效
     */
    private Integer status;

    /**
     * 标签类型，1:统计标签，2:规则标签，3:预测标签
     */
    private Integer type;

    private DataType dataType;

    private String accountType;

    private String parser;

    @TableField(exist = false)
    private transient LabelParser labelParser;

    /**
     * 计算规则
     */
    private String rule;

    /**
     * 标签描述
     */
    @TableField(value = "`desc`")
    private String desc;

    /**
     * 创建时间
     */
    private Long createTime;

    /**
     * 修改时间
     */
    private Long updateTime;

    @SuppressWarnings("unchecked")
    public LabelParser getLabelParser() {
        if (this.labelParser != null) {
            return labelParser;
        }
        try {
            Class<?> udfClass = Class.forName(this.parser);
            val udfInstance = (AbstractTableFunction<?, ?>) udfClass.newInstance();
            Field tableClassField = udfClass.getField("tableClass");
            Object tableClass = tableClassField.get(udfInstance);
            Field tableColumnsField = udfClass.getField("tableColumns");
            Object tableColumns = tableColumnsField.get(udfInstance);
            Field functionNameField = udfClass.getField("functionName");
            Object functionName = functionNameField.get(udfInstance);
            this.labelParser = new LabelParser((String) functionName, udfClass,
                    (Class<?>) tableClass, (List<SqlColumn>) tableColumns);
            return labelParser;
        } catch (Exception ex) {
            throw new RuntimeException("parser class: {} cannot be parsed");
        }
    }

}
