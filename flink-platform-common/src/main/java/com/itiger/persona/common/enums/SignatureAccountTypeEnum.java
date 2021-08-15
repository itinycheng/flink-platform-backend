package com.itiger.persona.common.enums;

/**
 * @Author Shik
 * @Title: SignatureTypeEnum
 * @ProjectName: datapipeline
 * @Description: TODO
 * @Date: 2021/3/30 下午4:43
 */
public enum SignatureAccountTypeEnum {

    COMMON("COMMON", "用户通用"),
    BUS("BUS", "综合账户"),
    IB("IB", "环球账户"),
    ;

    private String code;
    private String desc;

    SignatureAccountTypeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
