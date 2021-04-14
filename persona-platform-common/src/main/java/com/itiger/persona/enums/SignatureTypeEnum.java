package com.itiger.persona.enums;

/**
 * @Author Shik
 * @Title: SignatureTypeEnum
 * @ProjectName: datapipeline
 * @Description: TODO
 * @Date: 2021/3/30 下午4:43
 */
public enum SignatureTypeEnum {

    STATISTICS(1, "统计标签"),
    RULE(2, "规则标签"),
    PREDICTOR(3, "预测标签"),
    ;

    private int code;
    private String desc;

    SignatureTypeEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }
}
