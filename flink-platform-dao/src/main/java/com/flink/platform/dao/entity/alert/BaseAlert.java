package com.flink.platform.dao.entity.alert;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.flink.platform.common.enums.AlertType;
import lombok.Data;
import lombok.NoArgsConstructor;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

/** base alert. */
@Data
@NoArgsConstructor
@JsonTypeInfo(use = Id.NAME, include = As.EXISTING_PROPERTY, property = "type", visible = true)
@JsonSubTypes({
    @JsonSubTypes.Type(value = EmailAlert.class, name = "EMAIL"),
    @JsonSubTypes.Type(value = FeiShuAlert.class, name = "FEI_SHU"),
    @JsonSubTypes.Type(value = DingDingAlert.class, name = "DING_DING"),
    @JsonSubTypes.Type(value = SmsAlert.class, name = "SMS")
})
public class BaseAlert {

    private AlertType type;
}
