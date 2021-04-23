package com.itiger.persona.service;

import com.itiger.persona.common.util.FunctionUtil;
import com.itiger.persona.common.util.ZipUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipOutputStream;

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

    public String downloadFile(String hdfsFilePath) {

        return "local";
    }

    public List<Path> listVisibleFiles(String dir) throws IOException {
        return Arrays.stream(fileSystem.listStatus(new Path(dir),
                path -> !path.getName().startsWith(".")))
                .map(FileStatus::getPath)
                .collect(Collectors.toList());
    }

    public Pair<String, InputStream> inputStream(Path path) throws IOException {
        return Pair.of(path.getName(), fileSystem.open(path));
    }

    /**
     * it's better to lock on each file that needs download
     */
    public synchronized void copyVisibleHdfsFilesToLocalZipFile(File distZipFile, String hdfsDir) throws Exception {
        // copy visible hdfs files to local temp file first
        java.nio.file.Path localDataDir = Paths.get(distZipFile.getParent());
        java.nio.file.Path tempPath = Files.createTempFile(localDataDir, ".", ".tmp");
        File tempFile = tempPath.toFile();

        // do copy data action
        List<Path> files = listVisibleFiles(hdfsDir);
        try (FileOutputStream fos = new FileOutputStream(tempFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            files.stream()
                    .map(FunctionUtil.uncheckedFunction(this::inputStream))
                    .forEach(FunctionUtil.uncheckedConsumer((Pair<String, InputStream> pair) -> ZipUtil.writeToZip(pair.getLeft(), pair.getRight(), zos)));
            if (!tempFile.renameTo(distZipFile)) {
                throw new RuntimeException("rename tmp file: " + tempFile.getAbsolutePath() + "failed.");
            }
        } catch (Exception e) {
            Files.deleteIfExists(tempFile.toPath());
            FunctionUtil.rethrow(e);
        }
    }
}
