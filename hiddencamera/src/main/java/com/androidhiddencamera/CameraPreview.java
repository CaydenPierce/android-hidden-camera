/*
 * Copyright 2017 Keval Patel.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.androidhiddencamera;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.hardware.Camera;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.androidhiddencamera.config.CameraResolution;
import com.androidhiddencamera.config.CameraRotation;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Created by Keval on 10-Nov-16.
 * This surface view works as the fake preview for the camera.
 *
 * @author {@link 'https://github.com/kevalpatel2106'}
 */

@SuppressLint("ViewConstructor")
class CameraPreview extends SurfaceView implements SurfaceHolder.Callback, Camera.PreviewCallback {
    private CameraCallbacks mCameraCallbacks;

    private SurfaceHolder mHolder;
    private Camera mCamera;

    private CameraConfig mCameraConfig;

    private volatile boolean safeToTakePicture = false;

    private int count = 0;

    private Long prevTime = 0L;

    CameraPreview(@NonNull Context context, CameraCallbacks cameraCallbacks) {
        super(context);

        mCameraCallbacks = cameraCallbacks;

        //Set surface holder
        initSurfaceView();
    }

    /**
     * Initialize the surface view holder.
     */
    private void initSurfaceView() {
        mHolder = getHolder();
        mHolder.addCallback(this);
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        //set a size so the preview is big
        //720p at 4:3 ratio, which is vuzix camera ratio, I think - cayden
//        int width = 960;
//        int height = 720;
//        mHolder.setFixedSize(width, height);
    }


    @Override
    protected void onLayout(boolean b, int i, int i1, int i2, int i3) {
        //Do nothing
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        //Do nothing
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
        if (mCamera == null) {  //Camera is not initialized yet.
            mCameraCallbacks.onCameraError(CameraError.ERROR_CAMERA_OPEN_FAILED);
            return;
        } else if (surfaceHolder.getSurface() == null) { //Surface preview is not initialized yet
            mCameraCallbacks.onCameraError(CameraError.ERROR_CAMERA_OPEN_FAILED);
            return;
        }

        // stop preview before making changes
        try {
            mCamera.stopPreview();
        } catch (Exception e) {
            // Ignore: tried to stop a non-existent preview
        }

        // Make changes in picture size
        Camera.Parameters parameters = mCamera.getParameters();
        List<Camera.Size> pictureSizes = mCamera.getParameters().getSupportedPictureSizes();

        //Sort descending
        Collections.sort(pictureSizes, new PictureSizeComparator());

        //set the camera image size based on config provided
        Camera.Size cameraSize;
        switch (mCameraConfig.getResolution()) {
            case CameraResolution.HIGH_RESOLUTION:
                cameraSize = pictureSizes.get(0);   //Highest res
                break;
            case CameraResolution.MEDIUM_RESOLUTION:
                cameraSize = pictureSizes.get(pictureSizes.size() / 2);     //Resolution at the middle
                break;
            case CameraResolution.LOW_RESOLUTION:
                cameraSize = pictureSizes.get(pictureSizes.size() - 1);       //Lowest res
                break;
            default:
                throw new RuntimeException("Invalid camera resolution.");
        }
        parameters.setPictureSize(cameraSize.width, cameraSize.height);


        // Make changes in preview size
        List<Camera.Size> previewSizes = mCamera.getParameters().getSupportedPreviewSizes();
        //run below to see all sizes available. For Vuzix BLade (non-upgraded), they are:
        //03-04 23:14:36.223 4595-4595/com.example.wearableaidisplaymoverio D/CameraPreview: 0 size: 176x144
        //03-04 23:14:36.223 4595-4595/com.example.wearableaidisplaymoverio D/CameraPreview: 1 size: 320x240
        //03-04 23:14:36.223 4595-4595/com.example.wearableaidisplaymoverio D/CameraPreview: 2 size: 640x480
        //03-04 23:14:36.223 4595-4595/com.example.wearableaidisplaymoverio D/CameraPreview: 3 size: 720x480
        //03-04 23:14:36.223 4595-4595/com.example.wearableaidisplaymoverio D/CameraPreview: 4 size: 800x480
        //03-04 23:14:36.223 4595-4595/com.example.wearableaidisplaymoverio D/CameraPreview: 5 size: 1280x720
        //03-04 23:14:36.223 4595-4595/com.example.wearableaidisplaymoverio D/CameraPreview: 6 size: 1408x792
        //03-04 23:14:36.223 4595-4595/com.example.wearableaidisplaymoverio D/CameraPreview: 7 size: 1920x1080
        //
//        for (int k = 0; k < previewSizes.size(); k++){
//            Log.d("CameraPreview", Integer.toString(k) + " size: " + previewSizes.get(k).width + "x" + previewSizes.get(k).height);
//        }
        int size_idx = 5;
        parameters.setPreviewSize(previewSizes.get(size_idx).width,previewSizes.get(size_idx).height);

        //set recording hint so higher frame rate
        parameters.setRecordingHint(true);

        // Set the focus mode.
        List<String> supportedFocusModes = parameters.getSupportedFocusModes();
        if (supportedFocusModes.contains(mCameraConfig.getFocusMode())) {
            parameters.setFocusMode(mCameraConfig.getFocusMode());
        }

        requestLayout();

        mCamera.setParameters(parameters);

        try {
            mCamera.setDisplayOrientation(90);
            mCamera.setPreviewDisplay(surfaceHolder);
            mCamera.setPreviewDisplay(surfaceHolder);
            mCamera.setPreviewCallback(this);
            mCamera.setPreviewCallback(this);
            mCamera.startPreview();

            safeToTakePicture = true;
        } catch (IOException | NullPointerException e) {
            //Cannot start preview
            mCameraCallbacks.onCameraError(CameraError.ERROR_CAMERA_OPEN_FAILED);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // Surface will be destroyed when we return, so stop the preview.
        // Call stopPreview() to stop updating the preview surface.
        if (mCamera != null) mCamera.stopPreview();
    }

    /**
     * Initialize the camera and start the preview of the camera.
     *
     * @param cameraConfig camera config builder.
     */
    void startCameraInternal(@NonNull CameraConfig cameraConfig) {
        mCameraConfig = cameraConfig;

        if (safeCameraOpen(mCameraConfig.getFacing())) {
            if (mCamera != null) {
                requestLayout();

                try {
                    mCamera.setPreviewDisplay(mHolder);
                    mCamera.startPreview();
                } catch (IOException e) {
                    e.printStackTrace();
                    mCameraCallbacks.onCameraError(CameraError.ERROR_CAMERA_OPEN_FAILED);
                }
            }
        } else {
            mCameraCallbacks.onCameraError(CameraError.ERROR_CAMERA_OPEN_FAILED);
        }
    }

    private boolean safeCameraOpen(int id) {
        boolean qOpened = false;

        try {
            stopPreviewAndFreeCamera();

            mCamera = Camera.open(id);
            qOpened = (mCamera != null);
        } catch (Exception e) {
            Log.e("CameraPreview", "failed to open Camera");
            e.printStackTrace();
        }

        return qOpened;
    }

    boolean isSafeToTakePictureInternal() {
        return safeToTakePicture;
    }

    void takePictureInternal() {
        safeToTakePicture = false;
        if (mCamera != null) {
            mCamera.takePicture(null, null, new Camera.PictureCallback() {
                @Override
                public void onPictureTaken(final byte[] bytes, Camera camera) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            //Convert byte array to bitmap
                            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

                            //Rotate the bitmap
                            Bitmap rotatedBitmap;
                            if (mCameraConfig.getImageRotation() != CameraRotation.ROTATION_0) {
                                rotatedBitmap = HiddenCameraUtils.rotateBitmap(bitmap, mCameraConfig.getImageRotation());

                                //noinspection UnusedAssignment
                                bitmap = null;
                            } else {
                                rotatedBitmap = bitmap;
                            }

                            //Save image to the file.
                            if (HiddenCameraUtils.saveImageFromFile(rotatedBitmap,
                                    mCameraConfig.getImageFile(),
                                    mCameraConfig.getImageFormat())) {
                                //Post image file to the main thread
                                new android.os.Handler(Looper.getMainLooper()).post(new Runnable() {
                                    @Override
                                    public void run() {
                                        mCameraCallbacks.onImageCapture(mCameraConfig.getImageFile());
                                    }
                                });
                            } else {
                                //Post error to the main thread
                                new android.os.Handler(Looper.getMainLooper()).post(new Runnable() {
                                    @Override
                                    public void run() {
                                        mCameraCallbacks.onCameraError(CameraError.ERROR_IMAGE_WRITE_FAILED);
                                    }
                                });
                            }

                            mCamera.startPreview();
                            safeToTakePicture = true;
                        }
                    }).start();
                }
            });
        } else {
            mCameraCallbacks.onCameraError(CameraError.ERROR_CAMERA_OPEN_FAILED);
            safeToTakePicture = true;
        }
    }

    /**
     * When this function returns, mCamera will be null.
     */
    void stopPreviewAndFreeCamera() {
        safeToTakePicture = false;
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }

    //custom code here so that we can grab frames live from the camera preview
    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        mCameraCallbacks.onPreviewFrame(data, camera);
        Log.d("CameraPreview", "" + data[0] + " " + data[1]);
        Log.d("CameraPreview", "image: " + count);
        count++;
        
        //get frame rate
        Long nowTime = System.currentTimeMillis();
        Log.d("CameraPreview", "FPS: " + Long.toString(1000 / (nowTime - prevTime)));
        prevTime = nowTime;

        //convert to bitmap
//        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
//        //get size
//        if (bitmap != null){
//            int width = bitmap.getWidth();
//            int height = bitmap.getHeight();
//            Log.d("CameraPreview", Integer.toString(width) + "x" + Integer.toString(height));
//        } else {
//            Log.d("CameraPreview", "bitmap was null");
//        }
    }

}
