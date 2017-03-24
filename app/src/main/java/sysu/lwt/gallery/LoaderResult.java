package sysu.lwt.gallery;

import android.graphics.Bitmap;
import android.widget.ImageView;

/**
 * Created by 12136 on 2017/3/24.
 */

public class LoaderResult {
    public ImageView imageView;
    public String uri;
    public Bitmap bitmap;

    public LoaderResult(ImageView imageView, String uri, Bitmap bitmap) {
        this.imageView = imageView;
        this.uri = uri;
        this.bitmap = bitmap;
    }
}
