package com.example.cameraxapp;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraX;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageAnalysisConfig;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureConfig;
import androidx.camera.core.Preview;
import androidx.camera.core.PreviewConfig;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Rational;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.graphics.Matrix;
import android.widget.Toast;

import java.io.File;
import java.util.concurrent.Executor;

public class MainActivity extends AppCompatActivity {
    private final static int REQUEST_CODE_PERMISSION = 10;
    private final static String[] REQUIRED_PERMISSIONS = new String[]{Manifest.permission.CAMERA};

    private TextureView viewFinder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        viewFinder = findViewById(R.id.view_finder);
        viewFinder.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                // Every time the provided texture view changes, recompute layout
                updateTransform();
            }
        });
        if (allPermissionsGranted()) {
            viewFinder.post(new Runnable() {
                @Override
                public void run() {
                    startCamera();
                }
            });
        } else {
            ActivityCompat.requestPermissions(
                    this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSION);
        }
    }

    private void updateTransform() {
        Matrix matrix = new Matrix();

        // Compute the center of the view finder
        float centerX = viewFinder.getWidth() / 2;
        float centerY = viewFinder.getHeight() / 2;

        // Correct preview output to account for display rotation
        int rotationDegrees;
        switch (viewFinder.getDisplay().getRotation()) {
            case Surface.ROTATION_0:
                rotationDegrees = 0;
                break;
            case Surface.ROTATION_90:
                rotationDegrees = 90;
                break;
            case Surface.ROTATION_180:
                rotationDegrees = 180;
                break;
            case Surface.ROTATION_270:
                rotationDegrees = 270;
                break;
            default:
                return;
        }
        matrix.postRotate(rotationDegrees, centerX, centerY);

        // Finally, apply transformations to our TextureView
        viewFinder.setTransform(matrix);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CODE_PERMISSION) {
            if (allPermissionsGranted()) {
                viewFinder.post(new Runnable() {
                    @Override
                    public void run() {
                        startCamera();
                    }
                });
            }
        }
    }

    private void startCamera() {
        // Create configuration object for the viewfinder use case
        PreviewConfig previewConfig = new PreviewConfig.Builder()
                .setTargetAspectRatio(new Rational(1, 1))
                .setTargetResolution(new Size(640, 640))
                .build();

        // Build the viewfinder use case
        Preview preview = new Preview(previewConfig);

        // Everytime the viewfinder is updated, recompute the layout
        preview.setOnPreviewOutputUpdateListener(new Preview.OnPreviewOutputUpdateListener() {
            @Override
            public void onUpdated(Preview.PreviewOutput output) {
                // To update the SurfaceTexture, we have to remove it and re-add it
                ViewGroup parent = (ViewGroup) viewFinder.getParent();
                parent.removeView(viewFinder);
                parent.addView(viewFinder, 0);

                viewFinder.setSurfaceTexture(output.getSurfaceTexture());
                updateTransform();
            }
        });

        // Create configuration object for the image capture use case
        ImageCaptureConfig imageCaptureConfig = new ImageCaptureConfig.Builder()
                .setTargetAspectRatio(new Rational(1, 1))
                .setCaptureMode(ImageCapture.CaptureMode.MIN_LATENCY)
                .build();
        final ImageCapture imageCapture = new ImageCapture(imageCaptureConfig);
        findViewById(R.id.capture_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                File file = new File(getExternalMediaDirs()[0], System.currentTimeMillis() + ".jpg");
                imageCapture.takePicture(file, new ImageCapture.OnImageSavedListener() {
                    @Override
                    public void onImageSaved(@NonNull File file) {
                        Toast.makeText(MainActivity.this, "Photo saved as " + file.getAbsolutePath(), Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(@NonNull ImageCapture.ImageCaptureError imageCaptureError, @NonNull String message, @Nullable Throwable cause) {
                        Toast.makeText(MainActivity.this, "Couldn't save photo: " + message, Toast.LENGTH_SHORT).show();
                        if (cause != null)
                            cause.printStackTrace();
                    }
                });
            }
        });

        // Setup image analysis pipeline that computes average pixel luminance
        // TODO add analyzerThread and setCallbackHandler as in the original example in Kotlin
        ImageAnalysisConfig analysisConfig = new ImageAnalysisConfig.Builder()
                .setImageReaderMode(ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE)
                .build();

        // Build the image analysis use case and instantiate our analyzer
        ImageAnalysis imageAnalysis = new ImageAnalysis(analysisConfig);
        imageAnalysis.setAnalyzer(new LuminosityAnalyzer());

        // Bind use cases to lifecycle
        CameraX.bindToLifecycle(this, preview, imageCapture, imageAnalysis);
    }

    private boolean allPermissionsGranted() {
        for (String perm : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(getBaseContext(), perm) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }
}
