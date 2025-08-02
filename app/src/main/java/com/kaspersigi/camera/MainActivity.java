package com.kaspersigi.camera;

import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.Manifest;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.io.OutputStream;

@SuppressWarnings("deprecation")
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "CameraDemo";

    private TextureView mTextureView;
    private Camera mCamera;
    private int cameraId = Camera.CameraInfo.CAMERA_FACING_BACK;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mTextureView = findViewById(R.id.textureView);

        findViewById(R.id.button1).setOnClickListener(v -> requestCameraPermission());
        findViewById(R.id.button2).setOnClickListener(v -> openCamera());
        findViewById(R.id.button3).setOnClickListener(v -> configureCameraParameters());
        findViewById(R.id.button4).setOnClickListener(v -> startPreview());
        findViewById(R.id.button5).setOnClickListener(v -> takePicture());
        findViewById(R.id.button6).setOnClickListener(v -> stopPreview());
        findViewById(R.id.button7).setOnClickListener(v -> releaseCamera());
        findViewById(R.id.button8).setOnClickListener(v -> closeCamera());
    }

    // 请求相机权限
    private void requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            showToast("Camera permission granted");
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 100);
        }
    }

    // 打开相机
    private void openCamera() {
        if (mCamera == null) {
            try {
                mCamera = Camera.open(cameraId);
                setCameraDisplayOrientation();
                showToast("Camera opened");
            } catch (Exception e) {
                showToast("Camera open failed: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            showToast("Camera already opened");
        }
    }

    // 设置相机显示方向
    private void setCameraDisplayOrientation() {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);

        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;

        switch (rotation) {
            case Surface.ROTATION_0: degrees = 0; break;
            case Surface.ROTATION_90: degrees = 90; break;
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 270; break;
        }

        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;
        } else {
            result = (info.orientation - degrees + 360) % 360;
        }

        if (mCamera != null) {
            mCamera.setDisplayOrientation(result);
            Log.i(TAG, "Camera orientation set to " + result);
        }
    }

    // 配置相机参数
    private void configureCameraParameters() {
        if (mCamera != null) {
            Camera.Parameters params = mCamera.getParameters();
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            mCamera.setParameters(params);
            showToast("Camera parameters set");
        } else {
            showToast("Camera not opened yet");
        }
    }

    // 启动相机预览
    private void startPreview() {
        if (mCamera == null) {
            showToast("Camera not opened");
            return;
        }

        SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
        if (surfaceTexture == null) {
            showToast("SurfaceTexture not ready");
            return;
        }

        try {
            mCamera.setPreviewTexture(surfaceTexture);
            mCamera.startPreview();
            showToast("Preview started");
        } catch (IOException e) {
            showToast("Failed to start preview");
            e.printStackTrace();
        }
    }

    // 拍照并保存图片到相册
    private void takePicture() {
        if (mCamera == null) {
            showToast("Camera not opened");
            return;
        }

        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, "Photo_" + System.currentTimeMillis());
        values.put(MediaStore.Images.Media.DESCRIPTION, "Captured by Camera API1");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis());
        Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

        Camera.PictureCallback pictureCallback = (data, camera) -> {
            try {
                OutputStream outputStream = getContentResolver().openOutputStream(uri);
                outputStream.write(data);
                outputStream.close();
                showToast("Photo saved to gallery: " + uri.toString());
                mCamera.startPreview();
            } catch (IOException e) {
                e.printStackTrace();
                showToast("Failed to save photo to gallery");
            }
        };

        mCamera.takePicture(null, null, pictureCallback);
    }

    // 停止预览
    private void stopPreview() {
        if (mCamera != null) {
            try {
                mCamera.stopPreview();
                showToast("Preview stopped");
            } catch (Exception e) {
                showToast("Preview not started or already stopped");
            }
        }
    }

    // 释放相机资源
    private void releaseCamera() {
        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
            showToast("Camera released");
        }
    }

    // 关闭相机
    private void closeCamera() {
        releaseCamera();
    }

    // 显示Toast消息
    private void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        Log.i(TAG, msg);
    }

    @Override
    protected void onPause() {
        super.onPause();
        releaseCamera();
    }
}