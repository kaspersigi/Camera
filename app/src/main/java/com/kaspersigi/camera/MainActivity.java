package com.kaspersigi.camera;

import android.annotation.SuppressLint;
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
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

@SuppressWarnings("deprecation") // Camera API1 已废弃，但很多老设备仍可用
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "CameraDemo";
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;
    private static final int STORAGE_PERMISSION_REQUEST_CODE = 101;

    // ---- 用 SurfaceView 承载预览画面（API1 需绑定到 SurfaceHolder）----
    private SurfaceView mSurfaceView;
    private SurfaceHolder mSurfaceHolder;
    private boolean mSurfaceReady = false; // 标记预览 Surface 是否已创建（避免尚未创建就 startPreview）

    private Camera mCamera;                // 相机句柄（API1）
    private int mCameraId = Camera.CameraInfo.CAMERA_FACING_BACK; // 选用后置摄像头

    // —— 我们挑选出来的尺寸（都优先 4:3，选最大的；否则选最大可用）—
    private Camera.Size mPreviewSize;
    private Camera.Size mPictureSize;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // 布局里需有 id 为 @+id/surfaceView 的 SurfaceView

        // 1) 绑定 SurfaceView 并监听其生命周期（创建/尺寸变化/销毁）
        mSurfaceView = findViewById(R.id.surfaceView);
        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                mSurfaceReady = true;
                Log.d(TAG, "SurfaceView surfaceCreated");
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                // 如果你想根据 UI 尺寸动态重选 PreviewSize，可以在这里 stopPreview -> configureParameters -> startPreview
                Log.d(TAG, "SurfaceView surfaceChanged: " + width + "x" + height);
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                mSurfaceReady = false;
                Log.d(TAG, "SurfaceView surfaceDestroyed");
            }
        });

        // 2) 绑定“八按钮拆解”逻辑
        findViewById(R.id.button1).setOnClickListener(v -> requestCameraPermission());  // 请求相机/存储权限
        findViewById(R.id.button2).setOnClickListener(v -> openCamera());               // 打开相机
        findViewById(R.id.button3).setOnClickListener(v -> setDisplayOrientation());    // 设置显示方向（与设备旋转对齐）
        findViewById(R.id.button4).setOnClickListener(v -> configureParameters());      // 选择尺寸 + 配置参数（如对焦）
        findViewById(R.id.button5).setOnClickListener(v -> startPreview());             // 开始预览
        findViewById(R.id.button6).setOnClickListener(v -> takePicture());              // 拍照
        findViewById(R.id.button7).setOnClickListener(v -> stopPreview());              // 停止预览
        findViewById(R.id.button8).setOnClickListener(v -> closeCamera());              // 关闭相机
    }

    // —— 步骤1：权限（相机 + 老系统存储）—
    private void requestCameraPermission() {
        // 相机权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            showToast("Camera permission granted");
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
        }

        // Android 13 以前：可能还需传统存储权限（Q+ 已走分区存储，不强依赖）
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S) {
            boolean readGranted  = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)  == PackageManager.PERMISSION_GRANTED;
            boolean writeGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
            if (readGranted && writeGranted) {
                showToast("Storage permission granted");
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        STORAGE_PERMISSION_REQUEST_CODE);
            }
        }
    }

    // —— 步骤2：打开相机（API1：Camera.open）—
    @SuppressLint("DefaultLocale")
    private void openCamera() {
        if (mCamera == null) {
            try {
                mCamera = Camera.open(mCameraId);
                showToast(String.format("Camera: %d opened successfully", mCameraId));
            } catch (Exception e) {
                showToast(String.format("Camera: %d open failed: %s", mCameraId, e.getMessage()));
                e.printStackTrace();
            }
        } else {
            showToast(String.format("Camera: %d already opened", mCameraId));
        }
    }

    // —— 步骤3：设置相机预览的显示方向（非传感器方向；用于“把画面转正”）—
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
            case Surface.ROTATION_0:   degrees = 0;   break;
            case Surface.ROTATION_90:  degrees = 90;  break;
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 270; break;
        }

        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360; // 镜像补偿
        } else {
            result = (info.orientation - degrees + 360) % 360;
        }

        mCamera.setDisplayOrientation(result);
        showToast("Camera orientation set to " + result);
    }

    // —— 步骤4：配置相机参数（选最大 4:3 的 Picture/Preview 尺寸 + 对焦等）—
    private void configureParameters() {
        if (mCamera == null) {
            showToast("Camera not opened yet");
            return;
        }

        try {
            Camera.Parameters params = mCamera.getParameters();

            // 1) 选择“最大 4:3”的拍照尺寸（若无 4:3 则取最大可用）
            mPictureSize = chooseLargest43OrMax(params.getSupportedPictureSizes());
            Log.i(TAG, "Chosen PictureSize = " + mPictureSize.width + "x" + mPictureSize.height);

            // 2) 选择“最大 4:3”的预览尺寸（若无 4:3 则取最大可用）
            mPreviewSize = chooseLargest43OrMax(params.getSupportedPreviewSizes());
            Log.i(TAG, "Chosen PreviewSize = " + mPreviewSize.width + "x" + mPreviewSize.height);

            // 3) 应用尺寸
            // ⚠️ 某些机型要求先 stopPreview 再 setParameters，否则可能失败或黑屏
            try { mCamera.stopPreview(); } catch (Exception ignore) {}
            params.setPictureSize(mPictureSize.width, mPictureSize.height);
            params.setPreviewSize(mPreviewSize.width, mPreviewSize.height);

            // 4) 对焦模式（示例：连续对焦）
            List<String> fModes = params.getSupportedFocusModes();
            if (fModes != null && fModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            }

            // 5) 应用参数
            mCamera.setParameters(params);
            showToast("Camera parameters set");

        } catch (Exception e) {
            Log.e(TAG, "Error configuring camera parameters", e);
            showToast("Error configuring camera parameters");
        }
    }

    // —— 预览前准备：把相机预览绑定到 SurfaceView 的 Holder（API1 用 setPreviewDisplay）—
    private boolean preparePreviewSurface() {
        if (mCamera == null) {
            showToast("Camera not opened");
            return false;
        }
        if (!mSurfaceReady || mSurfaceHolder == null) {
            showToast("SurfaceView not ready");
            return false;
        }
        try {
            mCamera.setPreviewDisplay(mSurfaceHolder); // 关键：API1 绑定预览输出到 SurfaceView
            return true;
        } catch (IOException e) {
            showToast("Failed to setPreviewDisplay");
            e.printStackTrace();
            return false;
        }
    }

    // —— 步骤5：开始预览（先 prepare，再 startPreview）—
    private void startPreview() {
        if (mCamera == null) {
            showToast("Camera not opened");
            return;
        }
        if (preparePreviewSurface()) {
            try {
                mCamera.startPreview(); // 进入预览流
                showToast("Preview started");
            } catch (Exception e) {
                showToast("Failed to start preview");
                e.printStackTrace();
            }
        }
    }

    // —— 步骤6：拍照并保存到相册（通过 MediaStore；API1 回调返回 JPEG 字节）—
    private void takePicture() {
        if (mCamera == null) {
            showToast("Camera not opened");
            return;
        }

        // 每次拍前设置 JPEG 旋转（保证保存的照片方向正确）
        try {
            Camera.Parameters p = mCamera.getParameters();
            p.setRotation(getJpegRotation()); // 0/90/180/270
            mCamera.setParameters(p);
        } catch (Exception e) {
            Log.w(TAG, "set jpeg rotation failed", e);
        }

        long currentTime = System.currentTimeMillis();
        String fileName = "Photo_" + currentTime + ".jpg";

        // 通过 MediaStore 注册一条即将写入的图片记录
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, "Photo_" + currentTime);
        values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Images.Media.DESCRIPTION, "Captured by Camera API1");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis());
        Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

        // 拍照回调：data 即 JPEG 数据
        Camera.PictureCallback pictureCallback = (data, camera) -> {
            try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
                if (outputStream != null) {
                    outputStream.write(data);
                    showToast("Photo saved to gallery: " + fileName);
                } else {
                    showToast("Failed to open output stream");
                }
                // API1：takePicture 后预览会停止，需要手动重启
                mCamera.startPreview();
            } catch (IOException e) {
                e.printStackTrace();
                showToast("Failed to save photo to gallery");
            }
        };

        // 触发拍照（不使用快门/原始回调，只保留 JPEG）
        mCamera.takePicture(null, null, pictureCallback);
    }

    // —— 步骤7：停止预览（不释放相机资源）—
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

    // —— 步骤8：关闭相机（释放硬件资源；下次需重新 open）—
    private void closeCamera() {
        if (mCamera != null) {
            try { mCamera.stopPreview(); } catch (Exception ignore) {}
            mCamera.release();
            mCamera = null;
            showToast("Camera closed");
        }
    }

    // —— 工具：选择“最大 4:3”，若无 4:3 则选择最大可用 —
    private Camera.Size chooseLargest43OrMax(List<Camera.Size> sizes) {
        if (sizes == null || sizes.isEmpty()) return null;

        Camera.Size best43 = null;
        Camera.Size bestAny = sizes.get(0);

        for (Camera.Size s : sizes) {
            long pixels = (long) s.width * (long) s.height;
            if ((long) bestAny.width * bestAny.height < pixels) {
                bestAny = s;
            }
            // 4:3 判断（避免浮点误差）
            if ((long) s.width * 3 == (long) s.height * 4) {
                if (best43 == null ||
                        (long) best43.width * best43.height < pixels) {
                    best43 = s;
                }
            }
        }
        return (best43 != null) ? best43 : bestAny;
    }

    // —— 工具：计算 JPEG 旋转角（保存的照片方向）—
    private int getJpegRotation() {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(mCameraId, info);

        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:   degrees = 0;   break;
            case Surface.ROTATION_90:  degrees = 90;  break;
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 270; break;
        }

        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            // API1 官方建议：前置 JPEG 旋转为 (orientation + degrees) % 360
            return (info.orientation + degrees) % 360;
        } else {
            // 后置 JPEG 旋转为 (orientation - degrees + 360) % 360
            return (info.orientation - degrees + 360) % 360;
        }
    }

    // —— 小工具：统一 Toast + Log 输出来便于调试 —
    private void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        Log.i(TAG, msg);
    }
}