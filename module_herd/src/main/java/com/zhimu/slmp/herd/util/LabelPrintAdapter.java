package com.zhimu.slmp.herd.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.pdf.PdfDocument;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PrintManager;
import android.print.pdf.PrintedPdfDocument;

import androidx.annotation.NonNull;

import java.io.FileOutputStream;
import java.io.IOException;

/**
 * 打印批次标签：把标签位图居中缩放到一页 PDF，交给系统打印服务（PrintManager），用户在系统打印页完成或取消。
 */
public class LabelPrintAdapter extends PrintDocumentAdapter {

    private final Context context;
    private final Bitmap label;
    private final String jobName;
    private PrintAttributes attributes;

    public LabelPrintAdapter(@NonNull Context context, @NonNull Bitmap label, @NonNull String jobName) {
        this.context = context;
        this.label = label;
        this.jobName = jobName;
    }

    /** 调起系统打印 */
    public static void print(@NonNull Context context, @NonNull Bitmap label, @NonNull String batchNo) {
        PrintManager pm = (PrintManager) context.getSystemService(Context.PRINT_SERVICE);
        if (pm == null) {
            return;
        }
        String name = "批次标签 " + batchNo;
        PrintAttributes attrs = new PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A6)
                .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME)
                .build();
        pm.print(name, new LabelPrintAdapter(context, label, name), attrs);
    }

    @Override
    public void onLayout(PrintAttributes oldAttributes, PrintAttributes newAttributes,
                         CancellationSignal cancellationSignal, LayoutResultCallback callback, Bundle extras) {
        attributes = newAttributes;
        if (cancellationSignal.isCanceled()) {
            callback.onLayoutCancelled();
            return;
        }
        PrintDocumentInfo info = new PrintDocumentInfo.Builder(jobName + ".pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_PHOTO)
                .setPageCount(1)
                .build();
        callback.onLayoutFinished(info, !newAttributes.equals(oldAttributes));
    }

    @Override
    public void onWrite(PageRange[] pages, ParcelFileDescriptor destination, CancellationSignal cancellationSignal,
                        WriteResultCallback callback) {
        PrintedPdfDocument pdf = new PrintedPdfDocument(context, attributes);
        try {
            PdfDocument.Page page = pdf.startPage(0);
            Canvas canvas = page.getCanvas();
            float pageW = canvas.getWidth();
            float pageH = canvas.getHeight();
            float scale = Math.min(pageW / label.getWidth(), pageH / label.getHeight()) * 0.9f;
            float w = label.getWidth() * scale;
            float h = label.getHeight() * scale;
            float left = (pageW - w) / 2f;
            float top = (pageH - h) / 2f;
            canvas.drawBitmap(label, null, new RectF(left, top, left + w, top + h), null);
            pdf.finishPage(page);
            if (cancellationSignal.isCanceled()) {
                callback.onWriteCancelled();
                return;
            }
            try (FileOutputStream out = new FileOutputStream(destination.getFileDescriptor())) {
                pdf.writeTo(out);
            }
            callback.onWriteFinished(new PageRange[]{PageRange.ALL_PAGES});
        } catch (IOException e) {
            callback.onWriteFailed(e.getMessage());
        } finally {
            pdf.close();
        }
    }
}
