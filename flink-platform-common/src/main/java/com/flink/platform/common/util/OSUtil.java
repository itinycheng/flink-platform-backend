package com.flink.platform.common.util;

import lombok.extern.slf4j.Slf4j;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import static com.flink.platform.common.constants.Constant.EMPTY;

/** OS utils. */
@Slf4j
public class OSUtil {

    /** Whether is macOS. */
    public static boolean isMacOS() {
        return getOSName().startsWith("Mac");
    }

    /** Whether is windows. */
    public static boolean isWindows() {
        return getOSName().startsWith("Windows");
    }

    /** Get current OS name. */
    public static String getOSName() {
        return System.getProperty("os.name");
    }

    /** Get hostname. */
    public static String getHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            log.error("Get hostname failed", e);
            return EMPTY;
        }
    }

    /** Get host ip. */
    public static String getFirstNoLoopbackIP4Address() {
        try {
            List<String> noLoopbackAddrs = new ArrayList<>();
            List<String> siteLocalAddrs = new ArrayList<>();
            for (InetAddress address : getAllHostAddress()) {
                if (!address.isLoopbackAddress() && address instanceof Inet4Address) {
                    if (address.isSiteLocalAddress()) {
                        siteLocalAddrs.add(address.getHostAddress());
                    } else {
                        noLoopbackAddrs.add(address.getHostAddress());
                    }
                }
            }

            return !noLoopbackAddrs.isEmpty()
                    ? noLoopbackAddrs.get(0)
                    : (!siteLocalAddrs.isEmpty() ? siteLocalAddrs.get(0) : null);
        } catch (Exception e) {
            throw new RuntimeException("Get ip address failed", e);
        }
    }

    private static List<InetAddress> getAllHostAddress() throws SocketException {
        Enumeration<NetworkInterface> networks = NetworkInterface.getNetworkInterfaces();
        List<InetAddress> addresses = new ArrayList<>();
        while (networks.hasMoreElements()) {
            NetworkInterface networkInterface = networks.nextElement();
            Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
            while (inetAddresses.hasMoreElements()) {
                InetAddress inetAddress = inetAddresses.nextElement();
                addresses.add(inetAddress);
            }
        }

        return addresses;
    }
}
