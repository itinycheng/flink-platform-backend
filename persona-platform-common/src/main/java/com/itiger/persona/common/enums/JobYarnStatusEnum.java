package com.itiger.persona.common.enums;

public enum JobYarnStatusEnum {
    UNKNOWN(0, "UNKNOWN"),
    RUNNING(1, "RUNNING"),
    FINISHED(2, "FINISHED"),
    FAILED(3, "FAILED"),
    KILLED(4, "KILLED"),
    NEW(5, "NEW"),
    NEW_SAVING(6, "NEW_SAVING"),
    SUBMITTED(7, "SUBMITTED"),
    ACCEPTED(8, "ACCEPTED"),
    ;

    private int code;
    private String desc;

    JobYarnStatusEnum(int code, String desc) {
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

    public static Integer getCodeByDesc(String desc) {
        for (JobYarnStatusEnum enums : values()) {
            if (desc.equals(enums.getDesc())) {
                return enums.getCode();
            }
        }
        return null;
    }

    public static String getDescByCode(Integer code) {
        for (JobYarnStatusEnum enums : values()) {
            if (code.equals(enums.getCode())) {
                return enums.getDesc();
            }
        }
        return null;
    }

}
