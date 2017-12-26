package com.github.barteksc.pdfviewer;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.Log;

import com.shockwave.pdfium.PdfDocument;
import com.shockwave.pdfium.PdfiumCore;
import com.shockwave.pdfium.util.Size;

/**
 * @author leixin
 *
 * 单独页面加载工具
 */
public class PageRenderHelper {

    /**
     * 获取页面Bitmap，自动调整到对应的rect中
     * @param pdfiumCore
     * @param pdfDocument
     * @param pageIndex
     * @param rect
     * @return
     */
    public static Bitmap renderPage(PdfiumCore pdfiumCore, PdfDocument pdfDocument, int pageIndex, Rect rect) {
        Bitmap bitmap = Bitmap.createBitmap(rect.width(), rect.height(), Bitmap.Config.ARGB_8888);
        if (pdfiumCore != null && pdfDocument != null) {
            if (!pdfDocument.hasPage(pageIndex)) {
                pdfiumCore.openPage(pdfDocument, pageIndex);
            }
            pdfiumCore.renderPageBitmap(pdfDocument, bitmap, pageIndex, 0, 0, rect.width() ,rect.height());
        }
        return bitmap;
    }

    /**
     * 调整页面适应Rect
     * @param pageSize 原始页面尺寸
     * @param width
     * @param height
     * @return
     */
    public static Rect fitInPage(Size pageSize, int width, int height) {
        float scale = Math.min((float) width / pageSize.getWidth(),(float)height / pageSize.getHeight());
        int realWidth = (int) (pageSize.getWidth() * scale);
        int realHeight = (int) (pageSize.getHeight() * scale);
        int left = (int) ((width - realWidth) / 2);
        int top = (int) ((height - realHeight) / 2);
        Log.d("EgeioFileSource", " =========================>>>>>>>>>> page rect left " + left + " top " + top + " realWidth " + realWidth + " realHeight " + realHeight);
        return new Rect(left, top, left + realWidth, top + realHeight);
    }
}
