package com.flink.platform.storage.base;

import jakarta.annotation.Nonnull;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * file status.
 */
@Data
@NoArgsConstructor
public class StorageStatus {

    @Nonnull
    private Long byteLength;

    @Nonnull
    private LocalDateTime modificationTime;

    public static StorageStatus of(@Nonnull Long byteLength, @Nonnull LocalDateTime modificationTime) {
        StorageStatus storageStatus = new StorageStatus();
        storageStatus.byteLength = byteLength;
        storageStatus.modificationTime = modificationTime;
        return storageStatus;
    }
}
