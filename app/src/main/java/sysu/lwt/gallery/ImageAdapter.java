package sysu.lwt.gallery;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;

import java.util.List;

/**
 * Created by 12136 on 2017/3/24.
 */

public class ImageAdapter extends BaseAdapter {
    private Context context;
    private Drawable default_image;
    private List<String> urlList;
    private ImageLoader imageLoader;
    private int imageWidth = 0;
    private boolean mIsGridViewIdle = true;
    private boolean mCanGetBitmapFromNetWork = false;

    public ImageAdapter (Context context, List<String> list,
                         ImageLoader imageLoader, int imageWidth,
                         boolean mIsGridViewIdle, boolean mCanGetBitmapFromNetWork) {
        this.context = context;
        default_image = context.getResources().getDrawable(R.drawable.image_default);
        this.urlList = list;
        this.imageLoader = imageLoader;
        this.imageWidth = imageWidth;
        this.mCanGetBitmapFromNetWork = mCanGetBitmapFromNetWork;
        this.mIsGridViewIdle = mIsGridViewIdle;
    }
    @Override
    public int getCount() {
        return urlList.size();
    }
    @Override
    public String getItem(int i) {
        return urlList.get(i);
    }
    @Override
    public long getItemId(int i) {
        return i;
    }
    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        // 新声明一个View变量和ViewHolder变量
        View convertView;
        ViewHolder viewHolder;

        // 当View为空时才加载布局，并且创建一个ViewHolder，获得布局中的两个控件
        if (view == null) {
            // 通过inflate方法加载布局，context这个参数需要使用这个adapter的Activity传入
            convertView = LayoutInflater.from(context).inflate(R.layout.item, null);
            viewHolder = new ViewHolder();
            viewHolder.imageView = (ImageView) convertView.findViewById(R.id.image);
            // setTag方法是将处理好的viewHolder放入view中
            convertView.setTag(viewHolder);
        } else { // 否则，让convertView等于view, 然后从中取出ViewHolder即可
            convertView = view;
            viewHolder = (ViewHolder) convertView.getTag();
        }
        ImageView imageView = viewHolder.imageView;
        final String tag = (String)imageView.getTag();
        final String uri = getItem(i);
        if (!uri.equals(tag)) {
            imageView.setImageDrawable(default_image);
        }
        if (mIsGridViewIdle && mCanGetBitmapFromNetWork) {
            imageView.setTag(uri);
            imageLoader.bindBitmap(uri, imageView, imageWidth, imageWidth);
        }
        return convertView;
    }

    private class ViewHolder {
        private ImageView imageView;
    }
}
