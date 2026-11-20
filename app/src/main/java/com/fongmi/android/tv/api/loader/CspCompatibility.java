package com.fongmi.android.tv.api.loader;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

final class CspCompatibility {

    private static final String HELPER_CATALOG = "go2.nxog.top/zm/go";
    private static final String ARM_HELPER = "pvideo-armeabi-v7a";
    private static final String PROCESS_CLEANUP = "pkill -f ";

    private CspCompatibility() {
    }

    static boolean isAffectedOnArmTv(File file) throws IOException {
        try (ZipFile jar = new ZipFile(file)) {
            ZipEntry dex = jar.getEntry("classes.dex");
            if (dex == null) return false;
            try (InputStream input = jar.getInputStream(dex)) {
                return isAffectedDex(input);
            }
        } catch (ZipException e) {
            try (InputStream input = new FileInputStream(file)) {
                return input.read() == 'd' && input.read() == 'e' && input.read() == 'x' && isAffectedDex(input);
            }
        }
    }

    static boolean isAffectedDex(InputStream input) throws IOException {
        byte[] buffer = new byte[16 * 1024];
        String tail = "";
        boolean catalog = false;
        boolean helper = false;
        boolean cleanup = false;
        int count;
        while ((count = input.read(buffer)) != -1) {
            String chunk = tail + new String(buffer, 0, count, StandardCharsets.ISO_8859_1);
            catalog |= chunk.contains(HELPER_CATALOG);
            helper |= chunk.contains(ARM_HELPER);
            cleanup |= chunk.contains(PROCESS_CLEANUP);
            if (catalog && helper && cleanup) return true;
            tail = chunk.substring(Math.max(0, chunk.length() - HELPER_CATALOG.length() + 1));
        }
        return false;
    }
}
