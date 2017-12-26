package com.github.barteksc.pdfviewer.scroll;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;

import com.github.barteksc.pdfviewer.PDFView;
import com.github.barteksc.pdfviewer.RenderingTaskQueue;
import com.github.barteksc.pdfviewer.source.DocumentSource;
import com.github.barteksc.pdfviewer.util.Constants;
import com.shockwave.pdfium.util.Size;

import java.util.ArrayList;

/**
 * @author leixin
 * 缩略图滑块
 */
public class ThumbSeekScrollHandle extends View implements ScrollHandle {

    static final float CURRENTSELECTEDSCALE = 1.25f;

    private int viewWidth = 0;

    private DocumentSource documentSource;

    private SparseArray<Bitmap> imageCached = new SparseArray<>();

    private RenderingTaskQueue<RenderInfo> renderTask = new RenderingTaskQueue<>(5, RenderingTaskQueue.FIFO);

    private PdfRenderThread pdfPageRender;

    // 需要显示的缩略图页数
    private ArrayList<Integer> mThumberPages;

    private int mHoriztalPadding = 15;

    private PDFView pdfView;

    private Size mThumbsize;

    private Paint paint;

    private float currentProgress;

    private int currentPage = 0;

    private int pageCount;

    private boolean canceled = false;

    public ThumbSeekScrollHandle(@NonNull Context context) {
        this(context, null);
    }

    public ThumbSeekScrollHandle(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setClickable(true);
        mThumberPages = new ArrayList<>();
        pdfPageRender = new PdfRenderThread();
        paint = new Paint();
        paint.setColor(Color.WHITE);
    }

    public void initSeekBar(DocumentSource source, Size thumbSize) {
        this.mThumbsize = thumbSize;
        documentSource = source;
        calThumberPages(mThumberPages, pageCount = source.getPageCount());
        pdfPageRender.start();
        invalidate();
    }

    void onPageRender(Bitmap bitmap, Rect rect, int pageNumber, int originWidth, int originHeight){
        post(new Runnable() {
            @Override
            public void run() {
                postInvalidate();
            }
        });
    }

    private boolean isPDFViewReady() {
        return pdfView != null && pdfView.getPageCount() > 0 && !pdfView.documentFitsView();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!isPDFViewReady())
            return;

        // 绘制底部bitmap
        int left = calcLeftStart();
        int top = (getMeasuredHeight() - mThumbsize.getHeight()) / 2;
        for (int i=0; i<mThumberPages.size(); i++) {
            if (i== 0) {
                left = left + mHoriztalPadding;
            } else {
                left = left + (mHoriztalPadding + mThumbsize.getWidth());
            }
            int pageNumber = mThumberPages.get(i);

            Bitmap bitmap = imageCached.get(pageNumber);
            Rect rect = new Rect(left, top, left + mThumbsize.getWidth(), top + mThumbsize.getHeight());
            if (bitmap != null && !bitmap.isRecycled()) {
                canvas.drawBitmap(bitmap, new Rect(0, 0, mThumbsize.getWidth(), mThumbsize.getHeight()), rect, paint);
            } else {
                addRenderTask(pageNumber, mThumbsize.getWidth(), mThumbsize.getHeight());
                canvas.drawRect(rect, paint);
            }
        }

        drawCurrentPage(canvas, currentPage, pageCount);
    }

    protected void drawCurrentPage(Canvas canvas, int currentPage, int pageCount) {
        int top = (getMeasuredHeight() - (int)(mThumbsize.getHeight() * CURRENTSELECTEDSCALE)) / 2;
        int left = calcLeftStart() + (currentPage * calcDrawWidth() / pageCount) ;
        int currentSelectedWidth = (int) (mThumbsize.getWidth() * CURRENTSELECTEDSCALE);
        int currentSelectedHeight = (int) (mThumbsize.getHeight() * CURRENTSELECTEDSCALE);
        left = left - (currentSelectedWidth/2);
        if (left < 0) left = 0;

        Bitmap bitmap = imageCached.get(currentPage);
        Rect rect = new Rect(left, top, left + currentSelectedWidth, top + currentSelectedHeight);
        if (bitmap != null && !bitmap.isRecycled()) {
            canvas.drawBitmap(bitmap, new Rect(0, 0, currentSelectedWidth, currentSelectedHeight), rect, paint);
        } else {
           addRenderTask(currentPage, mThumbsize.getWidth(), mThumbsize.getHeight());
            canvas.drawRect(rect, paint);
        }
    }

    @Override
    public void setScroll(float position) {

    }

    @Override
    public void setupLayout(PDFView pdfView) {
        this.pdfView = pdfView;
    }

    @Override
    public void destroyLayout() {

    }

    @Override
    public void setPageNum(int pageNum) {

    }

    @Override
    public boolean shown() {
        return false;
    }

    @Override
    public void show() {
        setVisibility(View.VISIBLE);
    }

    @Override
    public void hide() {
        setVisibility(View.GONE);
    }

    @Override
    public void hideDelayed() {

    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        if (!isPDFViewReady()) {
            return super.onTouchEvent(event);
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                pdfView.stopFling();
            case MotionEvent.ACTION_MOVE:
                currentProgress = (event.getX() / viewWidth);
                currentPage = (int)(currentProgress * pdfView.getPageCount());
//                Message message = pdfPageRender.obtainMessage(MSG_REQUEST_LAYOUT, currentProgress);
//                pdfPageRender.sendMessage(message);
                pdfView.setPositionOffset(currentProgress, false);
                postInvalidate();
                return true;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                hideDelayed();
                return true;
        }

        return super.onTouchEvent(event);
    }

    private int calcMaxSize() {
        viewWidth = getMeasuredWidth();
        if (viewWidth > Constants.Thumb.MAX_WIDTH) {
            viewWidth = Constants.Thumb.MAX_WIDTH;
        }
        return (viewWidth - mHoriztalPadding) / (mThumbsize.getWidth() + mHoriztalPadding);
    }

    private int calcLeftStart() {
        // left的启示距离视元素为定
        return (viewWidth - getPaddingLeft() - calcDrawWidth() - getPaddingRight()) / 2;
    }

    private int calcDrawWidth() {
        return (mThumberPages.size() - 1) * (mThumbsize.getWidth() + mHoriztalPadding) + mThumbsize.getWidth();
    }

    private void calThumberPages(ArrayList<Integer> mThumberPages, int pageSize){
        if(mThumberPages != null){
            int maxthumbs = calcMaxSize();
            mThumberPages.clear();
            // 如果当前页数在最大页数范围内，则全部显示
            if (pageSize <= maxthumbs) {
                for (int i = 0; i < pageSize; i++) {
                    mThumberPages.add(i);
                }
            }
            // 否则取部分显示缩略图
            else {
                int i = 0;
                float curIndex = 0;
                float gap = (pageSize - 1f) / (maxthumbs - 1f);
                while (i < maxthumbs - 1){
                    int index = Math.round(curIndex);
                    if(index < pageSize) {
                        mThumberPages.add(index);
                    }
                    curIndex += gap;
                    i++;
                }
                mThumberPages.add(pageSize - 1);
            }
        }
    }

    public void destory() {
        canceled = true;
        imageCached.clear();
    }

    /**
     * 执行页面获取请求
     * @param pageNumber
     * @param width
     * @param height
     */
    void addRenderTask(int pageNumber, int width, int height) {
        if (renderTask.hasInQueue(pageNumber))
            return;
        RenderInfo task = new RenderInfo(pageNumber, width, height);
        renderTask.pushTask(task, pageNumber);
        if (pdfPageRender == null || !pdfPageRender.isAlive()) {
            pdfPageRender = new PdfRenderThread();
            pdfPageRender.start();
        }
    }

    class PdfRenderThread extends Thread {

        @Override
        public void run() {
            RenderInfo task;
            while ((task = renderTask.pollTask()) != null) {
                final Rect rect = documentSource.fitInPage(task.pageNumber, task.width, task.height);
                Bitmap renderBitmap = imageCached.get(task.pageNumber);
                if (renderBitmap == null || renderBitmap.isRecycled()) {
                    imageCached.put(task.pageNumber, documentSource.renderPage(task.pageNumber, rect));
                }
                onPageRender(imageCached.get(task.pageNumber), rect, task.pageNumber, task.width, task.height);
            }
        }
    }

    class RenderInfo {
        public int pageNumber;
        public int width;
        public int height;

        public RenderInfo(int page, int width, int height) {
            this.pageNumber = page;
            this.width = width;
            this.height = height;
        }
    }
}
