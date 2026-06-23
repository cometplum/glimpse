package com.cometplum.glimpse;

import android.net.Uri;

public class ScreenshotItem {
    public long mediaId;
    public Uri uri;
    public String displayName;
    public String bucket;
    public String relativePath;
    public int width;
    public int height;
    public long takenAt;
    public String ocrText = "";
    public String category = "Unknown";
    public String tags = "";
    public String semanticText = "";
    public double confidence = 0.0;
}
