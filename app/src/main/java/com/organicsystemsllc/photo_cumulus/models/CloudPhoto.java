package com.organicsystemsllc.photo_cumulus.models;

/**
 * Created by Hugh on 1/31/2018.

 */


public class CloudPhoto {
    public String photoUrl;
    public String photoPath;
    public String databaseKey;

    public CloudPhoto(){};

    public CloudPhoto(String databaseKey, String photoUrl, String photoPath) {
        this.photoUrl = photoUrl;
        this.photoPath = photoPath;
        this.databaseKey = databaseKey;
    }
}
