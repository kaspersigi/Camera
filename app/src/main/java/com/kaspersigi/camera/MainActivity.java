package com.kaspersigi.camera;

import android.annotation.SuppressLint;
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
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;
    private static final int STORAGE_PERMISSION_REQUEST_CODE = 101;
    private TextureView mTextureView;
    private Camera mCamera;
    private int mCameraId = Camera.CameraInfo.CAMERA_FACING_BACK; // 设置为后置摄像头

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mTextureView = findViewById(R.id.textureView);

        findViewById(R.id.button1).setOnClickListener(v -> requestCameraPermission()); // 请求相机权限
        findViewById(R.id.button2).setOnClickListener(v -> openCamera()); // 打开相机
        findViewById(R.id.button3).setOnClickListener(v -> setDisplayOrientation()); // 设置相机显示方向
        findViewById(R.id.button4).setOnClickListener(v -> configureParameters()); // 配置相机参数
        findViewById(R.id.button5).setOnClickListener(v -> startPreview()); // 启动相机预览
        findViewById(R.id.button6).setOnClickListener(v -> takePicture()); // 拍照
        findViewById(R.id.button7).setOnClickListener(v -> stopPreview()); // 停止预览
        findViewById(R.id.button8).setOnClickListener(v -> closeCamera()); // 关闭相机
    }

    private void requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            showToast("Camera permission granted"); // 相机权限已授权
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE); // 请求权限
        }

        // 请求存储权限
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            == PackageManager.PERMISSION_GRANTED) {
                showToast("Storage permission granted"); // 存储权限已授权
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION_REQUEST_CODE); // 请求权限
            }
        }

    }

    @SuppressLint("DefaultLocale")
    private void openCamera() {
        if (mCamera == null) {
            try {
                mCamera = Camera.open(mCameraId); // 打开相机
                showToast(String.format("Camera: %d opened successfully", mCameraId)); // 成功打开相机
            } catch (Exception e) {
                showToast(String.format("Camera: %d open failed: %s", mCameraId, e.getMessage())); // 打开相机失败
                e.printStackTrace();
            }
        } else {
            showToast(String.format("Camera: %d already opened", mCameraId)); // 相机已打开
        }
    }

    private void setDisplayOrientation() {
        if (mCamera == null) {
            showToast("Camera not opened");
            return;
        }

        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(mCameraId, info);

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
            mCamera.setDisplayOrientation(result); // 设置显示方向
            showToast("Camera orientation set to " + result); // 显示相机方向
        }
    }

    private void configureParameters() {
        if (mCamera != null) {
            try {
                Camera.Parameters params = mCamera.getParameters();

                // 检查设备是否支持自动对焦
                if (params.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                    params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                }
                mCamera.setParameters(params); // 应用参数
                showToast("Camera parameters set");
            } catch (Exception e) {
                Log.e(TAG, "Error configuring camera parameters", e);
                showToast("Error configuring camera parameters");
            }
        } else {
            showToast("Camera not opened yet");
        }
    }

    private boolean prepareSurfaceTexture() {
        if (mCamera == null) {
            showToast("Camera not opened");
            return false;
        }

        SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
        if (surfaceTexture == null) {
            showToast("SurfaceTexture not ready");
            return false;
        }

        try {
            mCamera.setPreviewTexture(surfaceTexture); // 设置预览纹理
            showToast("Surface prepared"); // 纹理准备完成
            return true;
        } catch (IOException e) {
            showToast("Failed to prepare surface");
            e.printStackTrace();
            return false;
        }
    }

    private void startPreview() {
        if (mCamera == null) {
            showToast("Camera not opened");
            return;
        }

        if (prepareSurfaceTexture()) { // 准备纹理
            try {
                mCamera.startPreview(); // 启动预览
                showToast("Preview started"); // 预览启动成功
            } catch (Exception e) {
                showToast("Failed to start preview"); // 启动预览失败
                e.printStackTrace();
            }
        }
    }

    private void takePicture() {
        if (mCamera == null) {
            showToast("Camera not opened");
            return;
        }

        long currentTime = System.currentTimeMillis(); // 获取当前时间戳
        String fileName = "Photo_" + currentTime + ".jpg"; // 构造文件名

        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, "Photo_" + currentTime); // 图片标题
        values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);  // 显示的文件名
        values.put(MediaStore.Images.Media.DESCRIPTION, "Captured by Camera API1"); // 图片描述
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg"); // 图片类型
        values.put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis()); // 图片添加时间
        Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values); // 插入 MediaStore

        Camera.PictureCallback pictureCallback = (data, camera) -> {
            try {
                OutputStream outputStream = getContentResolver().openOutputStream(uri); // 获取输出流
                outputStream.write(data); // 写入数据
                outputStream.close();
                showToast("Photo saved to gallery: " + fileName); // 图片保存成功
                mCamera.startPreview(); // 重启预览
            } catch (IOException e) {
                e.printStackTrace();
                showToast("Failed to save photo to gallery"); // 保存失败
            }
        };

        mCamera.takePicture(null, null, pictureCallback); // 拍照
    }

    private void stopPreview() {
        if (mCamera != null) {
            try {
                mCamera.stopPreview(); // 停止预览
                showToast("Preview stopped"); // 预览停止
            } catch (Exception e) {
                showToast("Preview not started or already stopped"); // 预览未启动或已停止
            }
        }
    }

    private void closeCamera() {
        if (mCamera != null) {
            mCamera.release(); // 释放相机资源
            mCamera = null;
            showToast("Camera closed"); // 相机关闭
        }
    }

    private void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        Log.i(TAG, msg); // 打印日志
    }
}