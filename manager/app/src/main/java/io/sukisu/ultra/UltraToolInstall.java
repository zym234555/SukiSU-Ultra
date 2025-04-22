package io.sukisu.ultra;

import static com.sukisu.ultra.ui.util.KsuCliKt.getKpmmgrPath;

public class UltraToolInstall {
    private static final String OUTSIDE_KPMMGR_PATH = "/data/adb/ksu/bin/kpmmgr";
    public static void tryToInstall() {
        String kpmmgrPath = getKpmmgrPath();
        if (!UltraShellHelper.isPathExists(OUTSIDE_KPMMGR_PATH)) {
            UltraShellHelper.CopyFileTo(kpmmgrPath, OUTSIDE_KPMMGR_PATH);
            UltraShellHelper.runCmd("chmod a+rx " + OUTSIDE_KPMMGR_PATH);
        }
    }
}
