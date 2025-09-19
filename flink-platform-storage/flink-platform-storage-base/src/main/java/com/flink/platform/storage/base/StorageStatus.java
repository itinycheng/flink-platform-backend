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

    public static StorageStatus of(long byteLength, @Nonnull LocalDateTime modificationTime) {
        StorageStatus storageStatus = new StorageStatus();
        storageStatus.byteLength = byteLength;
        storageStatus.modificationTime = checkNotNull(modificationTime);
        return storageStatus;
    }
}
