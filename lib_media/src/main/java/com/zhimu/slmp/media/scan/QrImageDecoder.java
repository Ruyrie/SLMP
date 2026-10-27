package com.zhimu.slmp.media.scan;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.exifinterface.media.ExifInterface;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;

/** Bounded, URI-based QR decoding. EXIF transforms operate on luminance, without a second bitmap. */
public final class QrImageDecoder {
    static final int MAX_SIDE = 2048;
    static final long MAX_PIXELS = 4L * 1024 * 1024;

    private QrImageDecoder() { }

    @WorkerThread
    @Nullable
    public static String decode(@NonNull ContentResolver resolver, @NonNull Uri uri) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = open(resolver, uri)) {
            BitmapFactory.decodeStream(input, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("Unreadable image");
        int orientation = orientation(resolver, uri);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight);
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap = null;
        try {
            try (InputStream input = open(resolver, uri)) {
                bitmap = BitmapFactory.decodeStream(input, null, options);
            }
            if (bitmap == null) throw new IOException("Unreadable image");
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            if (width > MAX_SIDE || height > MAX_SIDE || (long) width * height > MAX_PIXELS) {
                throw new IOException("Image exceeds QR decoding limit");
            }
            int[] pixels = new int[width * height];
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
            bitmap.recycle();
            bitmap = null;
            return decodeArgb(pixels, width, height, orientation);
        } catch (OutOfMemoryError error) {
            throw new IOException("Insufficient image decoding memory", error);
        } finally {
            if (bitmap != null) bitmap.recycle();
        }
    }

    static int sampleSize(int width, int height) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Invalid image dimensions");
        int sample = 1;
        while (true) {
            long w = ((long) width + sample - 1) / sample;
            long h = ((long) height + sample - 1) / sample;
            if (w <= MAX_SIDE && h <= MAX_SIDE && w * h <= MAX_PIXELS) return sample;
            sample *= 2;
        }
    }

    @Nullable
    static String decodeArgb(int[] pixels, int width, int height, int orientation) {
        LuminanceSource source = luminance(pixels, width, height, orientation);
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, true);
        hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
        QRCodeReader reader = new QRCodeReader();
        for (LuminanceSource candidate : new LuminanceSource[]{source, source.invert()}) {
            try {
                Result result = reader.decode(new BinaryBitmap(new HybridBinarizer(candidate)), hints);
                return result.getText();
            } catch (ReaderException ignored) {
                reader.reset();
            }
            try {
                Result result = reader.decode(new BinaryBitmap(new GlobalHistogramBinarizer(candidate)), hints);
                return result.getText();
            } catch (ReaderException ignored) {
                reader.reset();
            }
        }
        return null;
    }

    static LuminanceSource luminance(int[] pixels, int width, int height, int orientation) {
        if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS
                || pixels.length != (long) width * height) throw new IllegalArgumentException("Invalid pixel buffer");
        boolean swapped = orientation >= ExifInterface.ORIENTATION_TRANSPOSE
                && orientation <= ExifInterface.ORIENTATION_ROTATE_270;
        int outputWidth = swapped ? height : width;
        int outputHeight = swapped ? width : height;
        byte[] luminance = new byte[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = pixels[y * width + x];
                int alpha = pixel >>> 24;
                int red = (pixel >> 16) & 255;
                int green = (pixel >> 8) & 255;
                int blue = pixel & 255;
                // Transparent QR margins must be white rather than becoming a black surround.
                int gray = (((red + green * 2 + blue) / 4) * alpha + 255 * (255 - alpha)) / 255;
                int dx = x;
                int dy = y;
                switch (orientation) {
                    case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: dx = width - 1 - x; break;
                    case ExifInterface.ORIENTATION_ROTATE_180: dx = width - 1 - x; dy = height - 1 - y; break;
                    case ExifInterface.ORIENTATION_FLIP_VERTICAL: dy = height - 1 - y; break;
                    case ExifInterface.ORIENTATION_TRANSPOSE: dx = y; dy = x; break;
                    case ExifInterface.ORIENTATION_ROTATE_90: dx = height - 1 - y; dy = x; break;
                    case ExifInterface.ORIENTATION_TRANSVERSE: dx = height - 1 - y; dy = width - 1 - x; break;
                    case ExifInterface.ORIENTATION_ROTATE_270: dx = y; dy = width - 1 - x; break;
                    default: break;
                }
                luminance[dy * outputWidth + dx] = (byte) gray;
            }
        }
        return new PlanarYUVLuminanceSource(luminance, outputWidth, outputHeight,
                0, 0, outputWidth, outputHeight, false);
    }

    private static InputStream open(ContentResolver resolver, Uri uri) throws IOException {
        InputStream input = resolver.openInputStream(uri);
        if (input == null) throw new IOException("Image URI cannot be opened");
        return input;
    }

    private static int orientation(ContentResolver resolver, Uri uri) {
        try (InputStream input = open(resolver, uri)) {
            return new ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        } catch (IOException | RuntimeException ignored) {
            return ExifInterface.ORIENTATION_NORMAL;
        }
    }
}
