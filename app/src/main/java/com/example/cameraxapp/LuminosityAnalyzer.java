package com.example.cameraxapp;

import android.util.Log;

import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class LuminosityAnalyzer implements ImageAnalysis.Analyzer {
    private long lastAnalyzedTimestamp = 0L;

    /**
     * Helper extension function used to extract a byte array from an
     * image plane buffer
     */
    private byte[] byteBufferToByteArray(ByteBuffer buffer) {
        buffer.rewind();
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);
        return data;
    }

    @Override
    public void analyze(ImageProxy image, int rotationDegrees) {
        long currentTimestamp = System.currentTimeMillis();
        // Calculate the average luma no more often than every second
        if (currentTimestamp - lastAnalyzedTimestamp >= TimeUnit.SECONDS.toMillis(1)) {
            // Since format in ImageAnalysis is YUV, image.planes[0]
            // contains the Y (luminance) plane
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            // Extract image data from callback object
            byte[] data = byteBufferToByteArray(buffer);
            // Convert the data into an array of pixel values
            // NOTE: this is translated from the following kotlin code, ain't sure about it being right
            // val pixels = data.map { it.toInt() and 0xFF }
            int[] pixels = new int[data.length];
            int pos = 0;
            for (byte b : data) {
                pixels[pos] = b & 0xFF;
                pos++;
            }
            // Compute average luminance for the image
            double luma = Arrays.stream(pixels).average().orElse(Double.NaN);
            // Log the new luma value
            Log.d("CameraXApp", "Average luminosity: " + luma);
            // Update timestamp of last analyzed frame
            lastAnalyzedTimestamp = currentTimestamp;
        }
    }
}
