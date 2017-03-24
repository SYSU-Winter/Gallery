package sysu.lwt.gallery;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.util.LruCache;
import android.util.Log;
import android.widget.ImageView;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by 12136 on 2017/3/24.
 */

public class ImageLoader {

    // 实现线程池的一些常量
    // 设置核心线程数位CPU核心数+1
    // 线程池最大容量为CPU核心数的2倍+1
    // 线程闲置超时时长为10秒
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    private static final int CORE_POOL_SIZE = CPU_COUNT + 1;
    private static final int MAX_POOL_SIZE = CPU_COUNT * 2 + 1;
    private static final long KEEP_ALIVE = 10L;

    // 使用线程池有助于提升整体效率
    // 没有使用AsyncTask的原因是在3.0以上的版本中AsyncTask不能够实现并发的效果
    private static final ThreadFactory sThreadFactory = new ThreadFactory() {
        private AtomicInteger count = new AtomicInteger(1);
        @Override
        public Thread newThread(@NonNull Runnable runnable) {
            return new Thread(runnable, "ImageLoader#" + count.getAndIncrement());
        }
    };

    /**
     * CORE_POOL_SIZE, 核心线程数，默认下核心线程会在线程池中一直存活
     * MAX_POOL_SIZE, 最大线程数，当活动线程达到这个数值时，后续的新任务会被阻塞
     * KEEP_ALIVE，非核心线程闲置时的超时时长，超过这个时长，非核心线程会被回收
     *              当ThreadPoolExecutor的allowCoreThreadTimeOut属性设置为true时，
     *              同样作用于核心线程
     * TimeUnit.SECONDS，用于指定keepAliveTime的时间单位
     * workQueue，线程池中的任务队列，通过线程池的execute方法提交的Runnable对象会被存储在这个参数中
     * sThreadFactory，线程工厂，为线程池提供创建新线程的功能
     */
    public static final Executor THREAD_POOL_EXECUTOR = new ThreadPoolExecutor(
            CORE_POOL_SIZE, MAX_POOL_SIZE, KEEP_ALIVE,
            TimeUnit.SECONDS,
            new LinkedBlockingDeque<Runnable>(), sThreadFactory);

    private static final String TAG = "ImageLoader";
    // 这里的imageloader_uri在ids.xml中定义，为应用提供相关资源的位移资源id
    private static final int TAG_KEY_URI = R.id.imageloader_uri;

    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            LoaderResult result = (LoaderResult) msg.obj;
            ImageView imageView = result.imageView;
            String uri = (String) imageView.getTag(TAG_KEY_URI);
            // 在给ImageView设置图片之前，检查它的url有没有发生变化
            // 如果有变化就不设置图片了，这样就解决了列表的错位问题
            if (uri.equals(result.uri)) {
                imageView.setImageBitmap(result.bitmap);
            } else {
                Log.w(TAG, "uri has changed");
            }
        }
    };

    private Context mContext;
    private LruCache<String, Bitmap> mMemoryCache;

    private DiskLruCache diskLruCache;
    private boolean IsDiskLruCacheCreated = false;

    GetAvailableMemorySize getAvailableMemorySize;

    // 设置磁盘缓存区大小为50M
    private static final long DISK_CACHE_SIZE = 1024 * 1024 * 50;

    // 初始化是创建LruCache和DiskLruCache，分别实现内存缓存和磁盘缓存
    private ImageLoader(Context context) {
        mContext = context.getApplicationContext();
        // 以KB为单位的最大当前进程可用内存
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        // 设置ImageLoader的内存缓存容量为当前进程可用内存的1/8
        int cacheSize = maxMemory / 8;

        // 使用LruCache实现内存缓存
        // 只需重写sizeOf方法，计算缓存对象大小，注意单位要保持一致
        mMemoryCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getRowBytes() * bitmap.getHeight() / 1024;
            }
        };

        // 创建DiskLruCache实现磁盘缓存
        DiskCacheDir disk = new DiskCacheDir(mContext, "bitmap");
        File diskCacheDir = disk.getDiskCacheDir();
        // 如果目录不存在，就创建一个
        if (!diskCacheDir.exists()) {
            diskCacheDir.mkdirs();
        }
        // 如果磁盘可用空间大于50M, 那么就调用DiskLruCache的open方法创建自身
        getAvailableMemorySize = new GetAvailableMemorySize(diskCacheDir);
        if (getAvailableMemorySize.getUsableSpace() > DISK_CACHE_SIZE) {
            try {
                diskLruCache = DiskLruCache.open(diskCacheDir, 1, 1, DISK_CACHE_SIZE);
                IsDiskLruCacheCreated = true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static ImageLoader build(Context context) {
        return new ImageLoader(context);
    }

    // 内存缓存的添加
    private void addBitmapToMemoryCache(String key, Bitmap bitmap) {
        if (getBitmapFromMemCache(key) == null) {
            mMemoryCache.put(key, bitmap);
        }
    }

    // 内存缓存读取
    private Bitmap getBitmapFromMemCache(String key) {
        return mMemoryCache.get(key);
    }

    UrlToMD5 urlToMD5;
    private static final int DISK_CACHE_INDEX = 0;

    // 磁盘缓存添加
    public Bitmap loadBitmapFromHttp(String url, int requestedWidth, int requestedHeight)
                                    throws IOException {
        // loadBitmap的实现不能在主线程中调用，否则抛出异常
        // 通过检查当前线程的Looper是否为主线程的Looper来判断当前线程是不是主线程
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new RuntimeException("can not visit network from UI Thread.");
        }
        if (diskLruCache == null) {
            return null;
        }

        // 将url转换成md5
        urlToMD5 = new UrlToMD5(url);
        String key = urlToMD5.hashKeyFromUrl();
        // 通过editor完成缓存添加的操作
        // DiskLruCache不允许同时编辑一个对象，所以如果这个缓存正在被编辑
        // 呢么edit()会返回一个null
        DiskLruCache.Editor editor = diskLruCache.edit(key);
        if (editor != null) {
            // 得到一个文件输入流
            // 由于前面open方法创建DiskLruCache时设置了一个节点只能有一个数据
            // 所以这里的常量DISK_CACHE_INDEX是0就好了
            OutputStream outputStream = editor.newOutputStream(DISK_CACHE_INDEX);
            // 提交写入操作
            if (downloadUrlToStream(url, outputStream)) {
                editor.commit();
            } else { // 如果图片下载发生了异常，通过abort()回退操作
                editor.abort();
            }
            diskLruCache.flush();
        }
        return loadBitmapFromDiskCache(url, requestedWidth, requestedHeight);
    }

    private static final int IO_BUFFER_SIZE = 8 * 1024;
    // 将图片从文件输出流写入到文件系统中
    private boolean downloadUrlToStream(String urlString,
                                       OutputStream outputStream) {
        HttpURLConnection urlConnection = null;
        BufferedOutputStream out = null;
        BufferedInputStream in = null;

        try {
            final URL url = new URL(urlString);
            urlConnection = (HttpURLConnection) url.openConnection();
            in = new BufferedInputStream(urlConnection.getInputStream(),
                    IO_BUFFER_SIZE);
            out = new BufferedOutputStream(outputStream, IO_BUFFER_SIZE);

            int b;
            while ((b = in.read()) != -1) {
                out.write(b);
            }
            return true;
        } catch (IOException e) {
            Log.e(TAG, "downloadBitmap failed." + e);
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
            try {
                if (in != null) {
                    in.close();
                }
                if (out != null) {
                    out.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    private ImageResizer imageResizer = new ImageResizer();

    private Bitmap loadBitmapFromDiskCache(String url, int requestedWidth,
                                           int requestedHeight) throws IOException {
        // loadBitmap的实现不能在主线程中调用，否则抛出异常
        // 通过检查当前线程的Looper是否为主线程的Looper来判断当前线程是不是主线程
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Log.w(TAG, "load bitmap from UI Thread, it's not recommended!");
        }
        if (diskLruCache == null) {
            return null;
        }

        Bitmap bitmap = null;
        urlToMD5 = new UrlToMD5(url);
        String key = urlToMD5.hashKeyFromUrl();
        // 磁盘缓存的读取透过SnapShot来完成，
        DiskLruCache.Snapshot snapShot = diskLruCache.get(key);
        if (snapShot != null) {
            // 通过snapshot得到磁盘缓存对象对应的FileInputStream
            FileInputStream fileInputStream = (FileInputStream)snapShot.getInputStream(DISK_CACHE_INDEX);
            // FileInputStream无法便捷地进行压缩，所以通过FileDescriptor来加载压缩后的图片
            FileDescriptor fileDescriptor = fileInputStream.getFD();
            bitmap = imageResizer.decodeBitmapFromFileDescriptor(fileDescriptor,
                    requestedWidth, requestedHeight);
            // 最后将加载出来的Bitmap添加到内存缓存中
            if (bitmap != null) {
                addBitmapToMemoryCache(key, bitmap);
            }
        }
        return bitmap;
    }

    private Bitmap loadBitmapFromMemCache(String url) {
        urlToMD5 = new UrlToMD5(url);
        final String key = urlToMD5.hashKeyFromUrl();
        return getBitmapFromMemCache(key);
    }

    public Bitmap loadBitmap(String uri, int requestedWidth, int requestedHeight) {
        // 首先尝试从内存缓存中读取图片
        Bitmap bitmap = loadBitmapFromMemCache(uri);
        if (bitmap != null) {
            Log.d(TAG, "loadBitmapFromMemCache,url:" + uri);
            return bitmap;
        }
        // 接着尝试从磁盘缓存中读取图片
        try {
            bitmap = loadBitmapFromDiskCache(uri, requestedWidth, requestedHeight);
            if (bitmap != null) {
                Log.d(TAG, "loadBitmapFromDisk,url:" + uri);
                return bitmap;
            }
            // 最后尝试从网络拉去图片
            bitmap = loadBitmapFromHttp(uri, requestedWidth, requestedHeight);
            Log.d(TAG, "loadBitmapFromHttp,url:" + uri);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (bitmap == null && !IsDiskLruCacheCreated) {
            Log.w(TAG, "encounter error, DiskLruCache is not created.");
            bitmap = downloadBitmapFromUrl(uri);
        }
        return bitmap;
    }

    private Bitmap downloadBitmapFromUrl(String urlString) {
        Bitmap bitmap = null;
        HttpURLConnection urlConnection = null;
        BufferedInputStream in = null;

        try {
            final URL url = new URL(urlString);
            urlConnection = (HttpURLConnection) url.openConnection();
            in = new BufferedInputStream(urlConnection.getInputStream(),
                    IO_BUFFER_SIZE);
            bitmap = BitmapFactory.decodeStream(in);
        } catch (final IOException e) {
            Log.e(TAG, "Error in downloadBitmap: " + e);
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
            try {
                if (in != null) in.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return bitmap;
    }

    // 异步加载接口
    public void bindBitmap(final String uri, final ImageView imageView,
                           final int requestedWidth, final int requestedHeight) {
        imageView.setTag(TAG_KEY_URI, uri);
        // 首先尝试从内存缓存中读取图片，如果读取成功就直接返回结果
        Bitmap bitmap = loadBitmapFromMemCache(uri);
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap);
            return;
        }
        // 读取不成功的情况下，会在线程池中调用loadBitmap方法
        // 当图片加载成功后再将图片、图片地址以及需要绑定的ImageView封装成一个LoaderResult对象
        // 然后再通过mHandler向主线程发送一个消息，这样就可以在主线程给ImageView设置图片了
        Runnable loadBitmapTask = new Runnable() {
            @Override
            public void run() {
                Bitmap bitmap = loadBitmap(uri, requestedWidth, requestedHeight);
                if (bitmap != null) {
                    LoaderResult result = new LoaderResult(imageView, uri, bitmap);
                    mHandler.obtainMessage(1, result).sendToTarget();
                }
            }
        };
        THREAD_POOL_EXECUTOR.execute(loadBitmapTask);
    }
}
