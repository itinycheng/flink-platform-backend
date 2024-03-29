package com.flink.platform.web;

import com.flink.platform.dao.entity.result.ShellCallback;
import com.flink.platform.web.command.flink.FlinkCommandExecutor;
import com.flink.platform.web.util.CommandUtil;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

/** Command test. */
public class CommandTest {

    public static final String YARN_CLIENT_MESSAGE =
            "The Flink YARN session cluster has been started in detached mode. In order to stop Flink gracefully, use the following command:\n"
                    + "  $ echo \"stop\" | ./bin/yarn-session.sh -id application_1616984365313_0284\n"
                    + "  If this should not be possible, then you can also kill Flink via YARN's web interface or via:\n"
                    + "  $ yarn application -kill application_1616984365313_0284\n"
                    + "  Note that killing Flink might not clean up all job artifacts and temporary files.\n"
                    + "  2021-04-30 15:45:20,011 INFO  org.apache.flink.yarn.YarnClusterDescriptor                  [] - Found Web Interface vm-21-7.internal.com:34447 of application 'application_1616984365313_0284'.\n"
                    + "  Job has been submitted with JobID 698185836ffa4165f35c71627eb8c6f7\n"
                    + "  SLF4J: Class path contains multiple SLF4J bindings.";

    @Test
    public void testExtractAppIdAndJobId() {
        String appId = FlinkCommandExecutor.extractApplicationId(YARN_CLIENT_MESSAGE);
        String jobId = FlinkCommandExecutor.extractJobId(YARN_CLIENT_MESSAGE);
        Assert.assertEquals("application_1616984365313_0284", appId);
        Assert.assertEquals("698185836ffa4165f35c71627eb8c6f7", jobId);
    }

    @Test
    public void testTimeout() throws IOException, InterruptedException {
        ShellCallback exec = CommandUtil.exec("tree -r /", null, 5000);
        System.out.println(exec);
    }
}
