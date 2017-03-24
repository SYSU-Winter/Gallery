package sysu.lwt.gallery;

import android.os.Build;
import android.os.StatFs;

import java.io.File;

/**
 * Created by 12136 on 2017/3/24.
 */

public class GetAvailableMemorySize {
    public File path;
    public GetAvailableMemorySize(File path) {
        this.path = path;
    }

    public long getUsableSpace() {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
//            return path.getUsableSpace();
//        }
        final StatFs stats = new StatFs(path.getPath());
        return (long) stats.getBlockSize() * (long) stats.getAvailableBlocks();
    }
}
