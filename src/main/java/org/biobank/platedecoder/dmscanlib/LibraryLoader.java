package org.biobank.platedecoder.dmscanlib;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LibraryLoader {

    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(LibraryLoader.class);

    private static final boolean IS_MS_WINDOWS = System.getProperty("os.name").startsWith("Windows");

    private static final boolean IS_LINUX = System.getProperty("os.name").startsWith("Linux");

    private static final boolean IS_ARCH_64_BIT = System.getProperty("os.arch").equals("amd64");

    /**
     * Loads the native library for scanning and decoding.
     */
    public static void load() {
        // LOG.debug("java.library.path: {}", System.getProperty("java.library.path"));

        try {
            System.loadLibrary("dmtx");
        } catch (UnsatisfiedLinkError e) {
            System.err.println("Could not load native code library.\n" + e);
            System.exit(1);
        }
    }

    /**
     * Returns true if running on MS Windows.
     *
     * @return {@code true} if the operating system is running MS Windows.
     */
    public static boolean runningMsWindows() {
        return IS_MS_WINDOWS;
    }

}
