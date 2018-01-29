package org.godotengine.godot.gpgs;

import org.godotengine.godot.GodotLib;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;

import com.google.android.gms.common.images.ImageManager;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;

public class GodotCache {
    private static final String TAG = "gpgs";

    public static final String GODOT_SUB_FOLDER = "files";
    public static final String CACHE_FOLDER = "gpgs_lib_cache";

    private Activity activity = null;
    private int instance_id = 0;

    private File cacheDir;

    public GodotCache(Activity activity, int instance_id){
        this.activity = activity;
        this.instance_id = instance_id;

        prepareStorage();
    }

    public void sendURIImage(final Uri uri, final String fileName, final String godotFunction, final String extraInfo){
    	final File file = new File(cacheDir, fileName);

		if (file.exists()){
			Log.d(TAG, "Image already cached and available: " + file.getAbsolutePath());
			GodotLib.calldeferred(instance_id, godotFunction, new Object[] { extraInfo, CACHE_FOLDER, fileName});
		}else{
			activity.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					ImageManager manager = ImageManager.create(activity);
					manager.loadImage(new ImageManager.OnImageLoadedListener() {
						@Override
						public void onImageLoaded(Uri uri, Drawable drawable, boolean b) {
							Log.d(TAG, "saveURIImage(): uri = " + uri.toString());
							Log.d(TAG, "saveURIImage(): b = " + b);

							Bitmap image = drawableToBitmap(drawable);
							saveBitmapToFolder(image, file);
							GodotLib.calldeferred(instance_id, godotFunction, new Object[] { extraInfo, CACHE_FOLDER, fileName});
						}
					}, uri);
				}
			});
		}
    }

    public Bitmap getBitmap(String fileName){
        if (fileName.contains("/")){
            Log.d(TAG, "fileName is formatted as a path: " + fileName);
            fileName = fileName.substring(fileName.lastIndexOf("/")+1);
            Log.d(TAG, "Overriding path parameters in fileName and trying to retrieve file from cache: " + fileName);
        }
        File imgLoc = new File(cacheDir, fileName);
        Log.d(TAG, "Getting image bitmap from: " + imgLoc.getAbsolutePath());
        return BitmapFactory.decodeFile(new File(cacheDir, fileName).getAbsolutePath());
    }

    public boolean hasFile(String fileName){
    	File file = new File(cacheDir, fileName);
    	return file.exists();
	}

    public static void clearCache(Activity activity){
        File cacheDir = new File(activity.getApplicationInfo().dataDir + File.separator + GODOT_SUB_FOLDER);
        if (cacheDir.isDirectory()){
            for(File tempFile : cacheDir.listFiles()) {
                Log.d(TAG, "cached file deleted: " + tempFile.getPath());
                tempFile.delete();
            }
        }
    }

    private void prepareStorage() {
        String srcDir = activity.getApplicationInfo().dataDir + File.separator + GODOT_SUB_FOLDER;

        cacheDir = new File(srcDir, CACHE_FOLDER);
        if (!cacheDir.exists()){
			if (cacheDir.mkdirs()) { //make the directory
				Log.d(TAG, "Directory Created: " + cacheDir.getAbsolutePath());
			} else {
				Log.d(TAG, "Failed to create directory: " + cacheDir.getAbsolutePath());
			}
		}else{
			Log.d(TAG, "Directory already exists: " + cacheDir.getAbsolutePath());
		}

    }

    private void saveBitmapToFolder(Bitmap bitmap, File file){
        // Delete any previously existing image with the same name
        if (file.exists())
            file.delete();

        // Write the new image
        try {
            FileOutputStream out = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
            out.close();
            Log.d(TAG,"Image stored at: " + file.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Bitmap drawableToBitmap (Drawable drawable) {
        Bitmap bitmap = null;

        if (drawable instanceof BitmapDrawable) {
            Log.d(TAG,"drawbaleToBitmap(): drawable is instance of BitmapDrawable");
            BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
            if(bitmapDrawable.getBitmap() != null) {
                Log.d(TAG,"drawbaleToBitmap(): bitmapDrawable is not null");
                return bitmapDrawable.getBitmap();
            }
        }

        Log.d(TAG,"drawbaleToBitmap(): drawable is not an instance of BitmapDrawable or bitmapDrawable was null");
        if(drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) {
            bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888); // Single color bitmap will be created of 1x1 pixel
        } else {
            bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        }

        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }
}
