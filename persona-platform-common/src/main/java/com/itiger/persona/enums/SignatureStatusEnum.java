package com.itiger.persona.enums;

/**
 * @Author Shik
 * @Title: SignatureTypeEnum
 * @ProjectName: datapipeline
 * @Description: TODO
 * @Date: 2021/3/30 下午4:43
 */
public enum SignatureStatusEnum {

    VALID(1, "有效"),
    INVALID(0, "无效"),
    ;

    private int code;
    private String desc;

    SignatureStatusEnum(int code, String desc) {
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
