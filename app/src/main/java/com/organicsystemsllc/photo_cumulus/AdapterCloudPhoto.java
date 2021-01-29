package com.organicsystemsllc.photo_cumulus;

import android.content.Context;
import android.net.Uri;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import com.organicsystemsllc.photo_cumulus.models.CloudPhoto;
import com.squareup.picasso.Picasso;
import java.util.ArrayList;
import java.util.HashMap;


/**
 *
 * Created by Hugh on 12/12/2017.
 *
 */

public class AdapterCloudPhoto extends RecyclerView.Adapter<AdapterCloudPhoto.ImageViewHolder> {

    private static final String TAG = AdapterCloudPhoto.class.getSimpleName();
    private ArrayList<CloudPhoto> mImageFiles = new ArrayList<>();
    private HashMap<String, CloudPhoto> mHashMap = new HashMap<>();
    private Context mContext;
    private ImageAdapterOnClickHandler mClickHandler;

    public interface ImageAdapterOnClickHandler {
        void onImageClick(CloudPhoto cloudPhoto);
    }

    AdapterCloudPhoto(Context context) {
        this.mContext = context;
        this.mClickHandler = (ImageAdapterOnClickHandler) context;
    }

    @Override
    public ImageViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        Context context = parent.getContext();
        View view = LayoutInflater.from(context)
                .inflate(R.layout.list_item_cloud_photo, parent, false);
        return new ImageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ImageViewHolder holder, int position) {

        CloudPhoto cloudPhoto = mImageFiles.get(position);
        String imageUrl = cloudPhoto.photoUrl;

        Log.i(TAG, imageUrl);

        Picasso.with(mContext)
            .load(Uri.parse(imageUrl))
            .resize(200, 200)
            .placeholder(R.drawable.ic_image_placeholder)
            .centerCrop()
            .into(holder.mImage);

        holder.itemView.setTag(cloudPhoto);

    }

    @Override
    public int getItemCount() {
        if (mImageFiles != null) {
            return mImageFiles.size();
        }
        return 0;
    }

    void addImageUrl(CloudPhoto cloudPhoto) {
        String key = cloudPhoto.databaseKey;
        mHashMap.put(key, cloudPhoto);
        mImageFiles.add(cloudPhoto);
        notifyDataSetChanged();
    }


    void removeImageUrl(String key) {
        CloudPhoto cloudPhoto = mHashMap.remove(key);
        mImageFiles.remove(cloudPhoto);
        notifyDataSetChanged();
    }


    public class ImageViewHolder extends RecyclerView.ViewHolder
            implements View.OnClickListener {

        final ImageView mImage;

        ImageViewHolder(View itemView) {
            super(itemView);
            this.mImage = itemView.findViewById(R.id.iv_cloud_photo);
            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            int adapterPosition = getAdapterPosition();
            CloudPhoto cloudPhoto = mImageFiles.get(adapterPosition);
            mClickHandler.onImageClick(cloudPhoto);
        }
    }

}
