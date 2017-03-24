package sysu.lwt.gallery;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.FileDescriptor;

/**
 * Created by 12136 on 2017/3/23.
 */

public class ImageResizer {
    public ImageResizer() {}
    public Bitmap decodeBitmapFromResource(Resources res, int resId, int requestedWidth, int requestedHeight) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        // 设置inJustDecodeBounds=true，这样BitmapFactory只会解析图片的原始宽和高信息
        // 并不会真正加载图片，这是一个轻量级的操作
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(res, resId, options);

        // 计算采样率inSampleSize
        options.inSampleSize = calculateInSampleSize(options, requestedWidth, requestedHeight);

        // 将inJustDecodeBounds设置为false，根据计算出来的inSampleSize重新加载图片
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeResource(res, resId, options);
    }

    public Bitmap decodeBitmapFromFileDescriptor (FileDescriptor fileDescriptor,
                                                  int requestedWidth, int requestedHeight) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        // 设置inJustDecodeBounds=true，这样BitmapFactory只会解析图片的原始宽和高信息
        // 并不会真正加载图片，这是一个轻量级的操作
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFileDescriptor(fileDescriptor, null, options);

        // 计算采样率inSampleSize
        options.inSampleSize = calculateInSampleSize(options, requestedWidth, requestedHeight);

        // 将inJustDecodeBounds设置为false，根据计算出来的inSampleSize重新加载图片
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFileDescriptor(fileDescriptor, null, options);
    }

    private int calculateInSampleSize(BitmapFactory.Options options,
                                     int requestedWidth,
                                     int requestedHeight) {
        // 原始图片的宽高大小
        int height = options.outHeight;
        int width = options.outWidth;

        // inSampleSize为1时，采样后的图片为原始大小
        // inSampleSize必须大于1才有缩小的效果，采用率同时作用于宽、高，
        // 所以采样后的图片大小以采样率的2次方形式递减
        int inSampleSize = 1;

        if (height > requestedHeight || width > requestedWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // 在保证解析出的Bitmap的宽高分别大于目标的宽高的前提下，
            // 使inSampleSize尽可能大
            while ((halfHeight / inSampleSize) > requestedHeight
                     && (halfWidth / inSampleSize) > requestedWidth) {
                // inSampleSize的取值应该总为2的指数，否则会向下取整
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }
}
