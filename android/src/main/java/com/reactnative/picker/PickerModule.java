package com.reactnative.picker;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.MediaStore;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;
import com.yalantis.ucrop.UCrop;

import java.io.File;
import java.util.UUID;

/**
 * Created by ipusic on 5/16/16.
 */
public class PickerModule extends ReactContextBaseJavaModule implements ActivityEventListener {

    private static final int IMAGE_PICKER_REQUEST = 1;
    private static final String E_ACTIVITY_DOES_NOT_EXIST = "E_ACTIVITY_DOES_NOT_EXIST";
    private static final String E_PICKER_CANCELLED = "E_PICKER_CANCELLED";
    private static final String E_FAILED_TO_SHOW_PICKER = "E_FAILED_TO_SHOW_PICKER";
    private static final String E_NO_IMAGE_DATA_FOUND = "E_NO_IMAGE_DATA_FOUND";

    private Promise mPickerPromise;
    private Activity activity;

    private boolean cropping = false;
    private boolean multiple = false;
    private int width = 100;
    private int height = 100;

    public PickerModule(ReactApplicationContext reactContext) {
        super(reactContext);
        reactContext.addActivityEventListener(this);
    }

    @Override
    public String getName() {
        return "ImageCropPicker";
    }

    @ReactMethod
    public void openPicker(final ReadableMap options, final Promise promise) {
        activity = getCurrentActivity();

        if (activity == null) {
            promise.reject(E_ACTIVITY_DOES_NOT_EXIST, "Activity doesn't exist");
            return;
        }

        multiple = options.hasKey("multiple") && options.getBoolean("multiple");
        width = options.hasKey("width") ? options.getInt("width") : width;
        height = options.hasKey("height") ? options.getInt("height") : height;
        cropping = options.hasKey("cropping") ? options.getBoolean("cropping") : cropping;

        // Store the promise to resolve/reject when picker returns data
        mPickerPromise = promise;

        try {
            final Intent galleryIntent = new Intent(Intent.ACTION_PICK);
            galleryIntent.setType("image/*");
            galleryIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple);
            galleryIntent.setAction(Intent.ACTION_GET_CONTENT);

            final Intent chooserIntent = Intent.createChooser(galleryIntent, "Pick an image");
            activity.startActivityForResult(chooserIntent, IMAGE_PICKER_REQUEST);
        } catch (Exception e) {
            mPickerPromise.reject(E_FAILED_TO_SHOW_PICKER, e);
            mPickerPromise = null;
        }
    }

    private WritableMap getImage(Uri uri, boolean resolvePath) {
        WritableMap image = new WritableNativeMap();
        String path = uri.getPath();

        if (resolvePath) {
            path =  RealPathUtil.getRealPathFromURI(activity, uri);
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);

        image.putString("path", "file://" + path);
        image.putInt("width", options.outWidth);
        image.putInt("height", options.outHeight);

        return image;
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        if (requestCode == IMAGE_PICKER_REQUEST) {
            if (mPickerPromise != null) {
                if (resultCode == Activity.RESULT_CANCELED) {
                    mPickerPromise.reject(E_PICKER_CANCELLED, "Image picker was cancelled");
                } else if (resultCode == Activity.RESULT_OK) {
                    if (multiple) {
                        ClipData clipData = data.getClipData();
                        WritableArray result = new WritableNativeArray();

                        // only one image selected
                        if (clipData == null) {
                            result.pushMap(getImage(data.getData(), true));
                        } else {
                            for (int i = 0; i < clipData.getItemCount(); i++) {
                                result.pushMap(getImage(clipData.getItemAt(i).getUri(), true));
                            }
                        }

                        mPickerPromise.resolve(result);
                        mPickerPromise = null;
                    } else {
                        Uri uri = data.getData();

                        if (cropping) {
                            UCrop.Options options = new UCrop.Options();
                            options.setCompressionFormat(Bitmap.CompressFormat.JPEG);

                            UCrop.of(uri, Uri.fromFile(new File(activity.getCacheDir(), UUID.randomUUID().toString() + ".jpg")))
                                    .withMaxResultSize(width, height)
                                    .withAspectRatio(width, height)
                                    .withOptions(options)
                                    .start(activity);
                        } else {
                            mPickerPromise.resolve(getImage(uri, true));
                            mPickerPromise = null;
                        }
                    }
                }
            }
        } else if (requestCode == UCrop.REQUEST_CROP) {
            if (data != null) {
                final Uri resultUri = UCrop.getOutput(data);
                if (resultUri != null) {
                    mPickerPromise.resolve(getImage(resultUri, false));
                } else {
                    mPickerPromise.reject(E_NO_IMAGE_DATA_FOUND, "Cannot find image data");
                }
            } else {
                mPickerPromise.reject(E_NO_IMAGE_DATA_FOUND, "Image cropping rejected");
            }

            mPickerPromise = null;
        }
    }
}
