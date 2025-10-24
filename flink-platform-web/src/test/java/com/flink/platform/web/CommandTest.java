package com.flink.platform.web;

import com.flink.platform.web.command.flink.FlinkYarnTask;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Command test. */
public class CommandTest {

    public static final String YARN_CLIENT_MESSAGE =
            """
            The Flink YARN session cluster has been started in detached mode. In order to stop Flink gracefully, use the following command:
              $ echo "stop" | ./bin/yarn-session.sh -id application_1616984365313_0284
              If this should not be possible, then you can also kill Flink via YARN's web interface or via:
              $ yarn application -kill application_1616984365313_0284
              Note that killing Flink might not clean up all job artifacts and temporary files.
              2021-04-30 15:45:20,011 INFO  org.apache.flink.yarn.YarnClusterDescriptor                  [] - Found Web Interface vm-21-7.internal.com:34447 of application 'application_1616984365313_0284'.
              Job has been submitted with JobID 698185836ffa4165f35c71627eb8c6f7
              SLF4J: Class path contains multiple SLF4J bindings.""";

    @Test
    void extractAppIdAndJobId() {
        String appId = FlinkYarnTask.extractApplicationId(YARN_CLIENT_MESSAGE);
        String jobId = FlinkYarnTask.extractJobId(YARN_CLIENT_MESSAGE);
        assertEquals("application_1616984365313_0284", appId);
        assertEquals("698185836ffa4165f35c71627eb8c6f7", jobId);
    }
}
