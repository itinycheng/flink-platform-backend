package com.flink.platform.web.controller;

import com.flink.platform.web.quartz.QuartzService;
import org.junit.jupiter.api.Test;

import static com.flink.platform.common.enums.ResponseStatus.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class QuartzControllerTest {

    private final QuartzController controller = new QuartzController(new QuartzService());

    @Test
    void returnsNullWhenCronHasNoFutureTriggerTime() {
        var result = controller.parseExpr("0 0 0 1 1 ? 2000");

        assertEquals(SUCCESS.getCode(), result.getCode());
        assertNotNull(result.getData());
    }
}
