# Configuration Details

> The core configuration file of the current project
> is `flink-platform-web/src/main/resources/application-dev.yml`, we
> will explain the main content of this file with the following words.

- gRPC configuration, the project use gRPC to perform jobs on specified project instances.

```yaml
# Each project instance is both server and client.
grpc:
  server:
    port: 9898  # gRPC server port.
  client: # gRPC client port.
    local-grpc-server:
      address: static://127.0.0.1:9898
      enableKeepAlive: true
      keepAliveWithoutCalls: true
      negotiationType: plaintext
```

- The environment configuration for flink job submit.

```yaml
flink:
  local:

    # File dir under the project root path, used to store sql files generated during flink sql submission.
    sql-dir: sql_file

    # File dir under the project root path, store the resource files required for the job to run, download the latest resources from HDFS.
    jar-dir: job_jar

  # Flink env configuration, the corresponding class is `com.flink.platform.web.config.FlinkConfig`.
  sql112:

    # Flink version.
    version: 1.12.0

    # Flink command path in local disk.
    command-path: /data0/app/flink-1.12.0/bin/flink

    # Absolute jar path, used to submit flink sql to cluster, must be a hdfs path.
    jar-file: hdfs:///flink/jars/job_jar/flink-platform-core.jar

    # Main class for submitting flink sql.
    class-name: com.flink.platform.core.Sql112Application

    # Equal to configuration `yarn.provided.lib.dirs`.
    lib-dirs: hdfs:///flink/jars/flink_1.12.0/lib/

  # Flink multi-version configuration.
  sql113:
    version: 1.13.2
    command-path: /data0/app/flink-1.13.2/bin/flink
    jar-file: hdfs:///flink/jars/job_jar/flink-platform-core.jar
    class-name: com.flink.platform.core.Sql113Application
    lib-dirs: hdfs:///flink/jars/flink_1.13.2/lib/
```

- HDFS configuration, the extrnal file system that the project depends on, all user-uploaded
  resources
  and `flink-platform-core.jar` are stored in this HDFS.

```yaml
hadoop:
  # The username for submit job to hadoop cluster.
  username: admin

  # The HDFS root path for storing resource files(refer to: t_resource), such as: user jar, etc.
  hdfsFilePath: hdfs:///flink-platform/

  # The directory under the project root path on local disk, currently only used for tmp file storage during resource upload.  
  localDirName: data_dir

  # HDFS configuration, All uploaded resources and `flink-platform-core.jar` are saved in this HDFS.
  # Be careful: maybe different from the hadoop cluster where the job runs.
  properties:
    fs.hdfs.impl: org.apache.hadoop.hdfs.DistributedFileSystem
    fs.defaultFS: hdfs://nameservice1
    ha.zookeeper.quorum: 0.0.0.0:2181,0.0.0.0:2181,0.0.0.0:2181
    dfs.client.failover.proxy.provider.nameservice1: org.apache.hadoop.hdfs.server.namenode.ha.ConfiguredFailoverProxyProvider
    dfs.ha.automatic-failover.enabled.nameservice1: true
    dfs.nameservices: nameservice1
    dfs.ha.namenodes.nameservice1: namenode39,namenode127
    dfs.namenode.rpc-address.nameservice1.namenode39: 0.0.0.0:8020
    dfs.namenode.rpc-address.nameservice1.namenode127: 0.0.0.0:8020
    dfs.namenode.servicerpc-address.nameservice1.namenode39: 0.0.0.0:8022
    dfs.namenode.servicerpc-address.nameservice1.namenode127: 0.0.0.0:8022
```

- Worker configuration, workflow execution related configuration, the corresponding class
  is `com.flink.platform.web.config.WorkerConfig`.

```yaml
worker:
  # Set the thread pool size for executing job flow. 
  flowExecThreads: 100

  # The number of jobs that one job flow can execute in parallel.
  perFlowExecThreads: 5

  # Retry times when submitting job or getting job status fails.
  errorRetries: 3

  # Streaming job can exist in a workflow, we must give a terminal status to keep the execution of downstream nodes.
  # This is a condition that after streaming job has been running normally for 300000 millisecond we will set the status of steaming job to SUCCESS.
  streamingJobToSuccessMills: 300000

  # Set the thread pool size for reactive mode.
  reactiveExecThreads: 10

  # Timeout for submitting flink job from the command line.
  flinkSubmitTimeoutMills: 300000

  # Timeout for shell job execution.
  maxShellExecTimeoutMills: 28800000
```
