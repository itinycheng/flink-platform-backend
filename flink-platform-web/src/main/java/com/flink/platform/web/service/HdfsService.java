package com.flink.platform.web.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.LocalFileSystem;
import org.apache.hadoop.fs.Path;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.flink.platform.common.constants.Constant.DOT;

/**
 * service for upload/download hdfs
 *
 * @author tiny.wang
 */
@Slf4j
@Service
public class HdfsService {

    @Resource
    private FileSystem fileSystem;

    public void copyFileToLocalIfChanged(Path hdfsFile, Path localFile) throws IOException {
        boolean isCopy = true;
        LocalFileSystem local = FileSystem.getLocal(fileSystem.getConf());
        if (local.exists(localFile)) {
            FileStatus localFileStatus = local.getFileStatus(localFile);
            FileStatus hdfsFileStatus = fileSystem.getFileStatus(hdfsFile);
            isCopy = localFileStatus.getLen() != hdfsFileStatus.getLen();
        }
        if (isCopy) {
            fileSystem.copyToLocalFile(hdfsFile, localFile);
        }
    }

    public List<Path> listVisibleFiles(String dir) throws IOException {
        return Arrays.stream(fileSystem.listStatus(new Path(dir),
                path -> !path.getName().startsWith(DOT)))
                .map(FileStatus::getPath)
                .collect(Collectors.toList());
    }

    public Pair<String, InputStream> inputStream(Path path) throws IOException {
        return Pair.of(path.getName(), fileSystem.open(path));
    }

    public boolean exists(String dir) {
        try {
            return fileSystem.exists(new Path(dir));
        } catch (Exception e) {
            log.error("exception found", e);
            return false;
        }
    }

    public int lineNumber(Path file) {
        try (InputStream inputStream = inputStream(file).getRight();
             LineNumberReader lineNumberReader = new LineNumberReader(new InputStreamReader(inputStream))) {
            lineNumberReader.skip(Long.MAX_VALUE);
            return lineNumberReader.getLineNumber();
        } catch (Exception e) {
            throw new RuntimeException(String.format("calc line number of File: %s failed", file.toString()));
        }
    }
}
