/*
 * Copyright (C) 2016 Bartosz Schiller.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.barteksc.pdfviewer.source;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;

import com.github.barteksc.pdfviewer.PageRenderHelper;
import com.shockwave.pdfium.PdfDocument;
import com.shockwave.pdfium.PdfiumCore;

import java.io.IOException;

public abstract class DocumentSource {

    private PdfDocument mPdfDocument;
    private PdfiumCore mPdfiumCore;

    abstract PdfDocument createDocument(Context context, PdfiumCore core, String password) throws IOException;

    public PdfDocument createSource(Context context, PdfiumCore core, String password) throws IOException {
        mPdfiumCore = core;
        mPdfDocument = createDocument(context, core, password);
        return mPdfDocument;
    }

    public PdfDocument getPdfDocument() {
        return mPdfDocument;
    }

    PdfiumCore getPdfiumCore() {
        return mPdfiumCore;
    }

    /**
     * 获取页面Bitmap，自动调整到对应的rect中
     * @param pageIndex
     * @param rect
     * @return
     */
    public Bitmap renderPage(int pageIndex, Rect rect) {
        return PageRenderHelper.renderPage(mPdfiumCore, mPdfDocument, pageIndex, rect);
    }

    /**
     * 根据大小，获取指定页面的fixed size
     * @param pageNumber
     * @param width
     * @param height
     * @return
     */
    public Rect fitInPage(int pageNumber, int width, int height) {
        return PageRenderHelper.fitInPage(mPdfiumCore.getPageSize(mPdfDocument, pageNumber), width, height);
    }

    public int getPageCount() {
        return mPdfiumCore.getPageCount(mPdfDocument);
    }
}
