package com.flink.platform.web;

import com.flink.platform.web.command.JobCommand;
import com.flink.platform.web.command.dependent.DependentCommand;
import com.flink.platform.web.common.ValueSortedMap;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SortedMapTest {

    @Test
    void test() {
        var map = new ValueSortedMap<Long, JobCommand>();
        var now = LocalDateTime.now();
        var dependentCommand = new DependentCommand(1, null, false, null);
        dependentCommand.setExpectedStopTime(now);
        map.put(1L, dependentCommand);

        dependentCommand = new DependentCommand(3, null, false, null);
        dependentCommand.setExpectedStopTime(now.plusSeconds(100));
        map.put(3L, dependentCommand);

        dependentCommand = new DependentCommand(4, null, false, null);
        map.put(4L, dependentCommand);

        dependentCommand = new DependentCommand(5, null, false, null);
        map.put(5L, dependentCommand);

        dependentCommand = new DependentCommand(2, null, false, null);
        dependentCommand.setExpectedStopTime(now.minusSeconds(100));
        map.put(2L, dependentCommand);

        dependentCommand = new DependentCommand(6, null, false, null);
        map.put(6L, dependentCommand);

        dependentCommand = new DependentCommand(7, null, false, null);
        dependentCommand.setExpectedStopTime(now);
        map.put(7L, dependentCommand);

        assertEquals(7, map.size());

        map.remove(4L);
        map.remove(1L);
        assertNotNull(map.removeFirst());
        assertEquals(map.getKvMap().size(), map.getValSet().size());
    }
}
