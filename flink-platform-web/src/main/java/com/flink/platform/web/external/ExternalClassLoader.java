package com.flink.platform.web.external;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.stream.Stream;

/** External classloader. */
public class ExternalClassLoader extends URLClassLoader {

    private static final ClassLoader PLATFORM_LOADER;

    private final ClassLoader packagesClassLoader;

    private final String[] allowedPaths;

    public ExternalClassLoader(ClassLoader packagesClassLoader, String[] allowedPaths) {
        this(new URL[] {}, packagesClassLoader, allowedPaths);
    }

    public ExternalClassLoader(URL[] urls, ClassLoader packagesClassLoader, String[] allowedPaths) {
        super(urls, PLATFORM_LOADER);
        this.packagesClassLoader = packagesClassLoader;
        this.allowedPaths = allowedPaths;
    }

    public void addResource(final String jarPath) {
        try {
            super.addURL(new File(jarPath).toURI().toURL());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> c = findLoadedClass(name);
            if (c == null && isAllowedPackages(name)) {
                if (packagesClassLoader != null) {
                    c = packagesClassLoader.loadClass(name);
                }
            }

            if (c != null) {
                if (resolve) {
                    resolveClass(c);
                }
                return c;
            }

            return super.loadClass(name, resolve);
        }
    }

    private boolean isAllowedPackages(final String name) {
        if (allowedPaths != null) {
            return Stream.of(allowedPaths).allMatch(s -> s.startsWith(name));
        } else {
            return false;
        }
    }

    static {
        ClassLoader platformLoader = null;
        try {
            platformLoader = (ClassLoader)
                    ClassLoader.class.getMethod("getPlatformClassLoader").invoke(null);
        } catch (NoSuchMethodException e) {
            // on Java 8 and before
        } catch (Exception e) {
            throw new IllegalStateException("Cannot retrieve platform classloader on Java 9+", e);
        }
        PLATFORM_LOADER = platformLoader;
    }
}
