/**
 * Copyright 2016 Bartosz Schiller
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.barteksc.pdfviewer;

import android.graphics.PointF;
import android.graphics.RectF;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import com.github.barteksc.pdfviewer.model.LinkTapEvent;
import com.github.barteksc.pdfviewer.scroll.ScrollHandle;
import com.github.barteksc.pdfviewer.util.Constants;
import com.shockwave.pdfium.PdfDocument;
import com.shockwave.pdfium.util.SizeF;

import static com.github.barteksc.pdfviewer.util.Constants.Pinch.MAXIMUM_ZOOM;
import static com.github.barteksc.pdfviewer.util.Constants.Pinch.MINIMUM_ZOOM;

/**
 * This Manager takes care of moving the PDFView,
 * set its zoom track user actions.
 */
class DragPinchManager implements GestureDetector.OnGestureListener, GestureDetector.OnDoubleTapListener, ScaleGestureDetector.OnScaleGestureListener, View.OnTouchListener {

    private PDFView pdfView;
    private AnimationManager animationManager;

    private GestureDetector gestureDetector;
    private ScaleGestureDetector scaleGestureDetector;

    private boolean scrolling = false;
    private boolean scaling = false;
    private boolean enabled = false;

    // 记录当前滚动方向
    enum Director {Left, Right, Up, Down, None}
    private Director director = Director.None;
    /**
     * 是否可以滚动到下一页
     */
    private boolean scrollNext = false;

    DragPinchManager(PDFView pdfView, AnimationManager animationManager) {
        this.pdfView = pdfView;
        this.animationManager = animationManager;
        gestureDetector = new GestureDetector(pdfView.getContext(), this);
        scaleGestureDetector = new ScaleGestureDetector(pdfView.getContext(), this);
        pdfView.setOnTouchListener(this);
    }

    void enable() {
        enabled = true;
    }

    void disable() {
        enabled = false;
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
        boolean onTapHandled = pdfView.callbacks.callOnTap(e);
        boolean linkTapped = checkLinkTapped(e.getX(), e.getY());
        if (!onTapHandled && !linkTapped) {
            ScrollHandle ps = pdfView.getScrollHandle();
            if (ps != null && !pdfView.documentFitsView()) {
                if (!ps.shown()) {
                    ps.show();
                } else {
                    ps.hide();
                }
            }
        }
        pdfView.performClick();
        return true;
    }

    private boolean checkLinkTapped(float x, float y) {
        PdfFile pdfFile = pdfView.pdfFile;
        float mappedX = -pdfView.getCurrentXOffset() + x;
        float mappedY = -pdfView.getCurrentYOffset() + y;
        int page = pdfFile.getPageAtOffset(pdfView.isSwipeVertical() ? mappedY : mappedX, pdfView.getZoom());
        SizeF pageSize = pdfFile.getScaledPageSize(page, pdfView.getZoom());
        int pageX, pageY;
        if (pdfView.isSwipeVertical()) {
            pageX = (int) pdfFile.getSecondaryPageOffset(page, pdfView.getZoom());
            pageY = (int) pdfFile.getPageOffset(page, pdfView.getZoom());
        } else {
            pageY = (int) pdfFile.getSecondaryPageOffset(page, pdfView.getZoom());
            pageX = (int) pdfFile.getPageOffset(page, pdfView.getZoom());
        }
        for (PdfDocument.Link link : pdfFile.getPageLinks(page)) {
            RectF mapped = pdfFile.mapRectToDevice(page, pageX, pageY, (int) pageSize.getWidth(),
                    (int) pageSize.getHeight(), link.getBounds());
            if (mapped.contains(mappedX, mappedY)) {
                pdfView.callbacks.callLinkHandler(new LinkTapEvent(x, y, mappedX, mappedY, mapped, link));
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onDoubleTap(MotionEvent e) {
        if (!pdfView.isDoubletapEnabled()) {
            return false;
        }

        if (pdfView.getZoom() < pdfView.getMidZoom()) {
            pdfView.zoomWithAnimation(e.getX(), e.getY(), pdfView.getMidZoom());
        } else if (pdfView.getZoom() < pdfView.getMaxZoom()) {
            pdfView.zoomWithAnimation(e.getX(), e.getY(), pdfView.getMaxZoom());
        } else {
            pdfView.resetZoomWithAnimation();
        }
        return true;
    }

    @Override
    public boolean onDoubleTapEvent(MotionEvent e) {
        return false;
    }

    @Override
    public boolean onDown(MotionEvent e) {
        animationManager.stopFling();
        return true;
    }

    @Override
    public void onShowPress(MotionEvent e) {

    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        return false;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        scrolling = true;

        if (pdfView.isSwipeVertical()) {
            if (distanceY > 0)
                director = Director.Down;
            else
                director = Director.Up;
        } else {
            if (distanceX > 0)
                director = Director.Right;
            else
                director = Director.Left;
        }

        if (pdfView.isZooming() || pdfView.isSwipeEnabled()) {
            pdfView.moveRelativeTo(-distanceX, -distanceY);
        }
        if (!scaling || pdfView.doRenderDuringScale()) {
            pdfView.loadPageByOffset();
        }
        return true;
    }

    private void onScrollEnd(MotionEvent event) {
        if (pdfView.alwaysScrollToPageStart() && !pdfView.isZooming()) {
            checkLatestScrollPosition();
        }

        pdfView.loadPages();
        hideHandle();
    }

    @Override
    public void onLongPress(MotionEvent e) {

    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        if (!pdfView.isSwipeEnabled()) {
            return false;
        }
        int xOffset = (int) pdfView.getCurrentXOffset();
        int yOffset = (int) pdfView.getCurrentYOffset();

        float minX, minY;
        PdfFile pdfFile = pdfView.pdfFile;
        if (pdfView.isSwipeVertical()) {
            minX = -(pdfView.toCurrentScale(pdfFile.getMaxPageWidth()) - pdfView.getWidth());
            minY = -(pdfFile.getDocLen(pdfView.getZoom()) - pdfView.getHeight());
        } else {
            minX = -(pdfFile.getDocLen(pdfView.getZoom()) - pdfView.getWidth());
            minY = -(pdfView.toCurrentScale(pdfFile.getMaxPageHeight()) - pdfView.getHeight());
        }

        animationManager.startFlingAnimation(xOffset, yOffset, (int) (velocityX), (int) (velocityY),
                (int) minX, 0, (int) minY, 0);

        if (pdfView.alwaysScrollToPageStart() && !pdfView.isZooming()) {
            if ((pdfView.isSwipeVertical()
                    && (Math.abs(e2.getY() - e1.getY()) > Constants.Pinch.MINMUM_DISTENCE
                    && velocityX > Constants.Pinch.MINMUM_VELOCITY))
                    || (!pdfView.isSwipeVertical()
                    && (Math.abs(e2.getX() - e1.getX()) > Constants.Pinch.MINMUM_DISTENCE
                    || velocityY > Constants.Pinch.MINMUM_VELOCITY))) {
                scrollNext = true;
            }
        }
        return true;
    }

    @Override
    public boolean onScale(ScaleGestureDetector detector) {
        float dr = detector.getScaleFactor();
        float wantedZoom = pdfView.getZoom() * dr;
        if (wantedZoom < MINIMUM_ZOOM) {
            dr = MINIMUM_ZOOM / pdfView.getZoom();
        } else if (wantedZoom > MAXIMUM_ZOOM) {
            dr = MAXIMUM_ZOOM / pdfView.getZoom();
        }
        pdfView.zoomCenteredRelativeTo(dr, new PointF(detector.getFocusX(), detector.getFocusY()));
        return true;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        scaling = true;
        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {
        pdfView.loadPages();
        hideHandle();
        scaling = false;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (!enabled) {
            return false;
        }

        boolean retVal = scaleGestureDetector.onTouchEvent(event);
        retVal = gestureDetector.onTouchEvent(event) || retVal;

        if (event.getAction() == MotionEvent.ACTION_UP) {
            if (scrolling) {
                scrolling = false;
                onScrollEnd(event);
            }
        }
        return retVal;
    }

    private void hideHandle() {
        ScrollHandle scrollHandle = pdfView.getScrollHandle();
        if (scrollHandle != null && scrollHandle.shown()) {
            scrollHandle.hideDelayed();
        }
    }

    /**
     * 滚动停止时，检测当前滚动位置是否未页面起始位置
     */
    private void checkLatestScrollPosition() {
        int xOffset = (int) pdfView.getCurrentXOffset();
        int yOffset = (int) pdfView.getCurrentYOffset();

        float zoom = pdfView.getZoom();
        boolean isSwipeVertical = pdfView.isSwipeVertical();
        PdfFile pdfFile = pdfView.pdfFile;

        float pageOffsetStart;
        int pageNumber;
        float rellativePosition = (director == Director.Right  || director == Director.Down ? 2.5f : 1.5f);
        if (isSwipeVertical) {
            float absYoffset = Math.abs(yOffset);
            pageNumber = pdfFile.getPageAtOffset(absYoffset, zoom);
            pageOffsetStart = pdfFile.getPageOffset(pageNumber, zoom);
            SizeF size = pdfFile.getScaledPageSize(pageNumber, zoom);
            if (pageNumber < pdfFile.getPagesCount() && scrollNext) {
                if (director == Director.Down) {
                    animationManager.startYAnimation(yOffset, (int)(absYoffset - pdfFile.getPageOffset(pageNumber + 1, zoom)));
                } else if (director == Director.Up) {
                    animationManager.startYAnimation(yOffset, (int)(absYoffset - pageOffsetStart));
                }
            } else if ((absYoffset - pageOffsetStart > ((size.getHeight() / rellativePosition)) || scrollNext)) {
                animationManager.startYAnimation(yOffset, (int)(absYoffset - pdfFile.getPageOffset(pageNumber + 1, zoom)));
            } else {
                animationManager.startYAnimation(yOffset, (int)(absYoffset - pageOffsetStart));
            }
        }  else {
            float absXoffset = Math.abs(xOffset);
            pageNumber = pdfFile.getPageAtOffset(Math.abs(xOffset), zoom);
            pageOffsetStart = pdfFile.getPageOffset(pageNumber, zoom);
            SizeF size = pdfFile.getScaledPageSize(pageNumber, zoom);
            if (pageNumber < pdfFile.getPagesCount() && scrollNext) {
                if (director == Director.Right) {
                    animationManager.startScrollTo(xOffset, yOffset, (int)(absXoffset - pdfFile.getPageOffset(pageNumber + 1, zoom)), yOffset);
                } else if (director == Director.Left) {
                    animationManager.startScrollTo(xOffset, yOffset, (int)(absXoffset - pageOffsetStart), yOffset);
                }
            } else if ((absXoffset - pageOffsetStart > (size.getWidth() / rellativePosition)) && pageNumber < pdfFile.getPagesCount()) {
                animationManager.startScrollTo(xOffset, yOffset, (int)(absXoffset - pdfFile.getPageOffset(pageNumber + 1, zoom)), yOffset);
            } else {
                animationManager.startScrollTo(xOffset, yOffset, (int)(absXoffset - pageOffsetStart), yOffset);
            }
        }
    }
}
