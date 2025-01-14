#!/bin/sh

if [ $# != 2 ]
then
    printf "\033[31mPlease enter the arguments...\033[0m\n"
    printf "\033[31mUSAGE:: sh %s [start|stop|restart] [test|prod|hk]\033[0m\n" "$0"
    exit 1
fi

SHELL_NAME=$0
COMMAND=$1  # 操作命令
ENV=$2    #环境

export JAVA_HOME=/usr/local/jdk1.8.0_161
export PATH=$JAVA_HOME/bin:"$PATH"

DEPLOY_DIR=$(cd "$(dirname "$0")/.." || { echo "Failed to find deploy directory."; exit 1; }; pwd)
PROJECT_NAME="flink-platform-web"
SERVER_LOG_DIR="/data0/logs/$PROJECT_NAME"
PID_FILE="$DEPLOY_DIR/data/$PROJECT_NAME.pid"

# SERVER_PORT
if [ "$ENV" = "prod" ]; then
    SERVER_PORT="8048"
else
    SERVER_PORT="9104"
fi

# JAR_FILE
jar_files=$(find "$DEPLOY_DIR/" -maxdepth 1 -name "$PROJECT_NAME-*.jar")
jar_count=$(find "$DEPLOY_DIR/" -maxdepth 1 -name "$PROJECT_NAME-*.jar" | wc -l)
if [ "$jar_count" -eq 1 ]; then
    JAR_FILE="$jar_files"
elif [ "$jar_count" -gt 1 ]; then
    printf "error: More than one jar file found!"
    printf "jar files: %s" "$jar_files"
    exit 1
else
    printf "error: No jar file found!"
    exit 1
fi

# Args:
printf "project name: %s \n" "$PROJECT_NAME"
printf "deploy dir: %s \n" "$DEPLOY_DIR"
printf "server log dir: %s \n" "$SERVER_LOG_DIR"
printf "pid file: %s \n" "$PID_FILE"
printf "jar file: %s \n" "$JAR_FILE"

# GC_LOG_PATH="$DEPLOY_DIR/logs/gc.log"
# GC_LOG_ARGS="-XX:ErrorFile=./java_error_%p.log -XX:+HeapDumpOnOutOfMemoryError -XX:+PrintGCDetails -Xloggc:${GC_LOG_PATH} -XX:+UseGCLogFileRotation -XX:NumberOfGCLogFiles=5 -XX:GCLogFileSize=20M -XX:+PrintGCApplicationStoppedTime -XX:+PrintGCDateStamps "
JAVA_OPTS="-Xms4096m -Xmx4096m -XX:+UseG1GC"

buildJavaOpts() {
    if [ "$ENV" = "test" ];then
      JAVA_OPTS="-DprojectName=$PROJECT_NAME -Dspring.profiles.active=$ENV -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"
    else
      JAVA_OPTS="${JAVA_OPTS} -DprojectName=$PROJECT_NAME -Dspring.profiles.active=$ENV"
    fi
}

# 进程启动成功或者失败，读取服务器部分日志
printServerLog() {
    sleep 2
    if [ -f "$DEPLOY_DIR/nohup.out" ]; then
      cat "$DEPLOY_DIR/nohup.out"
    fi
    if [ -f "$DEPLOY_DIR/logs/stdout.log" ]; then
      tail -n 50 "$DEPLOY_DIR/logs/stdout.log"
    fi
}

# 检查某个进程pid是否正在监听某个端口，一般用于服务部署启动是否成功检测
checkListenStatus() {
    checkPid=$1
    checkPort=$2

    if [ -z "$checkPort" ]; then
      return 0
    fi

    # 读取监听端口的进程pid，最多重试10次(变量: maxCheckTimes)，休眠间隔为5秒(变量: sleepTime)
    maxCheckTimes=10
    sleepTime=5

    printf "Detecting server listening status, port:%s\n" "${checkPort}"
    listenPid=$(lsof -ti:"$checkPort")
    checkTimes=0
    while [ -z "$listenPid" ] && [ $checkTimes -lt $maxCheckTimes ];
    do
      sleep ${sleepTime}
      printf "Detecting server listening status, port: %s, tried times: %s\n" "$checkPort" "$checkTimes"
      listenPid=$(lsof -ti:"$checkPort")
      checkTimes=$((checkTimes + 1))
    done

    # 检查监听端口进程pid是否跟当前进程一致
    if [ "$checkPid" = "$listenPid" ]; then
        printf "\033[32m Detecting server finished, Process(pid=%s) is listening port:%s\033[0m\n" "$listenPid" "$checkPort"
        return 0
    elif [ -n "$listenPid" ]; then
        printf "\033[31m Detecting server finished, Process(pid=%s) still listening port:%s\033[0m\n" "$listenPid" "$checkPort"
        return 1
    else
        printf "\033[31m Detecting server finished, Can't find any process listening port:%s\033[0m\n" "$checkPort"
        return 1
    fi
}

stopService() {
    cd "$DEPLOY_DIR" || { print "\033[31mCan not find the project directory...\033[0m\n"; exit 1; }

    # 检查pid文件是否存在，如果存在提取pid
    if [ -f "$PID_FILE" ];then
      PID=$(cat "$PID_FILE")
      rm -f "$PID_FILE"
    fi

    # 如果pid文件不存在，使用ps命令获取pid
    if [ -z "$PID" ];then
      printf "\033[31mCan not find the pid file...\033[0m\n"
      PID=$(jps -lv | grep "projectName=$PROJECT_NAME"|grep -v "$SHELL_NAME"| grep -v grep|awk '{print $2}')
      if [ -z "${PID}" ];then
        printf "\033[31m%s server did not run, kill aborted...\033[0m\n" "$PROJECT_NAME"
        return
      fi
    fi

    # 重试3次kill进程，如果进程未正常退出，使用强制性退出kill -9
    killTimes=5
    while [ "$killTimes" -ge 0 ]; do
      if ! kill -0 "$PID" >/dev/null 2>&1; then
        printf "\033[31m kill %s, pid:%s successfully! \033[0m\n" "$PROJECT_NAME" "$PID"
        return
      else
        printf "\033[31m start kill -15 %s, pid:%s ...\033[0m\n" "$PROJECT_NAME" "$PID"
        kill -15 "$PID"
        sleep 3
      fi
      killTimes=$((killTimes - 1))
    done

    
    if ! kill -0 "$PID" >/dev/null 2>&1; then
        printf "\033[31m kill %s, pid:%s successfully! \033[0m\n" "$PROJECT_NAME" "$PID"
        return
      else
        printf "\033[31m force kill -9 %s, pid:%s ...\033[0m\n" "$PROJECT_NAME" "$PID"
        kill -9 "$PID"
        sleep 3
    fi
}

prepareService() {
    if [ -d "$DEPLOY_DIR/logs" ];then
      rm -rf "$DEPLOY_DIR/logs"
    fi
    mkdir -p "$DEPLOY_DIR/data"
    # 创建日志目录的软连
    cd "$DEPLOY_DIR" || { printf "Failed to find deploy directory.\n"; exit 1; }
    mkdir -p "$SERVER_LOG_DIR"
    ln -s "$SERVER_LOG_DIR" logs
}

startService() {
    cd "$DEPLOY_DIR" || { printf "Failed to find deploy directory.\n"; exit 1; }

    # 检查pid文件
    if [ -f "$PID_FILE" ] && [ -s "$PID_FILE" ];
    then
        PID=$(cat "$PID_FILE")
        if kill -0 "$PID" >/dev/null 2>&1; then
            printf "\033[31m%s server is running, pid=%s...\033[0m\n" "$PROJECT_NAME" "$PID"
            exit 0
          else
            printf "\033[31m%s server is not running, but pid file exists, remove it...\033[0m\n" "$PROJECT_NAME"
            rm -rf "$PID_FILE"
        fi
    fi

    # 准备工作目录 & 适配JVM参数
    prepareService
    buildJavaOpts

    printf "\033[32mNow start %s server...\033[0m\n" "$PROJECT_NAME"
    printf "\033 nohup java %s -jar %s > nohup.out 2>&1 & \033[0m\n" "$JAVA_OPTS" "$JAR_FILE"
    nohup java $JAVA_OPTS -jar "$JAR_FILE" > nohup.out 2>&1 &
    PID=$!

    if [ $? -eq 0 ]
    then
        printf "%s" "$PID" > "$PID_FILE"
        printf "\033[32m%s server start, pid:%s\033[0m\n" $PROJECT_NAME $PID
    else
        printf "\033[31m%s server start error...\033[0m\n" $PROJECT_NAME
        printServerLog
        exit 1
    fi

    # 检测进程是否在监听端口
    if ! checkListenStatus "$PID" "$SERVER_PORT";
    then
      printf "\033[31mDetecting  %s server listening failed, port: %s \033[0m\n" $PROJECT_NAME $SERVER_PORT
      printServerLog
      exit 1
    else
      printf "\033[32mDetecting %s server listening successfully, port: %s \033[0m\n" $PROJECT_NAME $SERVER_PORT
      printServerLog
    fi
}

case $COMMAND in
start)
    startService;;
stop)
    stopService;;
restart)
    stopService
    startService;;
esac
