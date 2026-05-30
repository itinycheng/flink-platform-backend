package com.flink.platform.common.util;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * IO helpers.
 */
public final class IOUtil {

    /**
     * Returns an {@link InputStream} that closes {@code resource} after closing {@code stream}.
     */
    public static InputStream closingWith(InputStream stream, AutoCloseable resource) {
        return new FilterInputStream(stream) {
            @Override
            public void close() throws IOException {
                IOException primary = null;
                try {
                    super.close();
                } catch (IOException e) {
                    primary = e;
                }

                try {
                    resource.close();
                } catch (Exception e) {
                    IOException wrapped = (e instanceof IOException) ? (IOException) e : new IOException(e);
                    if (primary != null) {
                        primary.addSuppressed(wrapped);
                    } else {
                        primary = wrapped;
                    }
                }

                if (primary != null) {
                    throw primary;
                }
            }
        };
    }
}
