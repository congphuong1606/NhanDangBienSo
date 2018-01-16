package org.tensorflow.demo.model;

import android.graphics.Bitmap;

/**
 * Created by Ominext on 1/15/2018.
 */

public class BienSo {
    int id;
    Bitmap bitmap;

    public BienSo() {
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Bitmap getBitmap() {
        return bitmap;
    }

    public void setBitmap(Bitmap bitmap) {
        this.bitmap = bitmap;
    }

    public BienSo(int id, Bitmap bitmap) {

        this.id = id;
        this.bitmap = bitmap;
    }
}
