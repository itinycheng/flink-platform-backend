package com.flink.platform.dao;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flink.platform.dao.entity.JobInfo;
import com.flink.platform.dao.entity.LongArrayList;
import org.junit.Test;

/** Unit test for simple App. */
public class MapperInitializerTest {
    /** Rigorous Test :-). */
    @Test
    public void shouldAnswerWithTrue() throws JsonProcessingException {
        LongArrayList longs = new LongArrayList();
        longs.add(1L);
        longs.add(2L);

        JobInfo jobInfo = new JobInfo();
        jobInfo.setId(3L);

        ObjectMapper objectMapper = new ObjectMapper();
        String s = objectMapper.writeValueAsString(jobInfo);
        System.out.println(s);
        JobInfo jobInfo1 = objectMapper.readValue(s, JobInfo.class);
        System.out.println(jobInfo1);
    }
}
