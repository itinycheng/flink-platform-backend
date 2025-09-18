package com.flink.platform.storage.base;

import jakarta.annotation.Nonnull;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import static com.flink.platform.common.util.Preconditions.checkNotNull;

/**
 * file status.
 */
@Data
@NoArgsConstructor
public class StorageStatus {

    private long byteLength;

    private LocalDateTime modificationTime;

    private String name;

    public static StorageStatus of(@Nonnull String name, long byteLength, @Nonnull LocalDateTime modificationTime) {
        StorageStatus storageStatus = new StorageStatus();
        storageStatus.name = checkNotNull(name);
        storageStatus.byteLength = byteLength;
        storageStatus.modificationTime = checkNotNull(modificationTime);
        return storageStatus;
    }
}
