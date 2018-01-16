package org.tensorflow.demo.adapter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import org.tensorflow.demo.R;
import org.tensorflow.demo.model.BienSo;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Random;

/**
 * Created by Ominext on 1/15/2018.
 */

public class BienSoAdapter  extends RecyclerView.Adapter<BienSoAdapter.PicViewHolder> {
    View v;
    private ArrayList<BienSo> pics;
    Context context;


    public BienSoAdapter(ArrayList<BienSo> pics) {
        this.pics = pics;
    }


    @Override
    public PicViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_bienso, parent, false);
        context = v.getContext();
        return new PicViewHolder(v);
    }

    public byte[] convertBitmapToByte(Bitmap bitmap){
        ByteArrayOutputStream baos=new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG,100, baos);
        return baos.toByteArray();
    }
    @Override
    public void onBindViewHolder(PicViewHolder holder, int position) {
        BienSo pic = pics.get(position);
        holder.imvbienso.setImageBitmap(pic.getBitmap());
        Random r = new Random();
        int red=r.nextInt(255 - 0 + 1)+0;
        int green=r.nextInt(255 - 0 + 1)+0;
        int blue=r.nextInt(255 - 0 + 1)+0;

        GradientDrawable draw = new GradientDrawable();
        draw.setShape(GradientDrawable.OVAL);
        draw.setColor(Color.rgb(red,green,blue));
        holder.mTextView.setBackground(draw);
        holder.mTextView.setText(String.valueOf(pic.getId()));
//        Glide.with(context)
//                .load(convertBitmapToByte(pic.getBitmap()))
//                .asGif()
//                .into(holder.imvbienso);

    }

    @Override
    public int getItemCount() {
        return pics.size();
    }


    public class PicViewHolder extends RecyclerView.ViewHolder {
        ImageView imvbienso;
        public TextView mTextView;


        public PicViewHolder(View itemView) {
            super(itemView);
           imvbienso=(ImageView)itemView.findViewById(R.id.imv_bienso);
            mTextView=(TextView)itemView.findViewById(R.id.tv);
        }
    }
}