package sysu.lwt.gallery;

import android.content.Context;
import android.os.Environment;

import java.io.File;

/**
 * Created by 12136 on 2017/3/24.
 */

public class DiskCacheDir {
    public Context context;
    public String uniqueName;
    public DiskCacheDir(Context context, String uniqueName) {
        this.context = context;
        this.uniqueName = uniqueName;
    }

    public File getDiskCacheDir() {
        boolean externalStorageAvailable = Environment
                .getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
        final String cachePath;
        if (externalStorageAvailable) {
                cachePath = context.getExternalCacheDir().getPath();
        } else {
            cachePath = context.getCacheDir().getPath();
        }
        return new File(cachePath + File.separator + uniqueName);
    }
}
