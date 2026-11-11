package com.kasper.mobsenses.client;

import net.minecraft.network.chat.Component;

/**
 * Translating + vertical-bounds clipping {@link Canvas} decorator for scrollable panel bodies,
 * plus scrollbar rendering.
 */
public final class ScrollCanvas implements Canvas {

    private final Canvas delegate;
    private final int clipTop;
    private final int clipBottom;
    private final int scrollOffset;

    public ScrollCanvas(Canvas delegate, int clipTop, int clipBottom, int scrollOffset) {
        this.delegate = delegate;
        this.clipTop = clipTop;
        this.clipBottom = clipBottom;
        this.scrollOffset = scrollOffset;
    }

    @Override
    public int width() {
        return delegate.width();
    }

    @Override
    public int height() {
        return delegate.height();
    }

    @Override
    public int textWidth(Component text) {
        return delegate.textWidth(text);
    }

    @Override
    public void fill(int x1, int y1, int x2, int y2, int argb) {
        int dy1 = Math.max(clipTop, y1 - scrollOffset);
        int dy2 = Math.min(clipBottom, y2 - scrollOffset);
        if (x2 > x1 && dy2 > dy1) {
            delegate.fill(x1, dy1, x2, dy2, argb);
        }
    }

    @Override
    public void text(Component text, int x, int y, int argb) {
        int dy = y - scrollOffset;
        if (dy >= clipTop && dy + 8 <= clipBottom) {
            delegate.text(text, x, dy, argb);
        }
    }

    @Override
    public boolean pushScale(float factor) {
        return delegate.pushScale(factor);
    }

    @Override
    public void popScale() {
        delegate.popScale();
    }

    public static void drawScrollbar(
            Canvas canvas,
            int trackX,
            int top,
            int bottom,
            int scrollOffset,
            int maxScroll,
            int contentHeight) {
        int viewportH = bottom - top;
        if (maxScroll <= 0 || viewportH <= 8 || contentHeight <= viewportH) {
            return;
        }
        canvas.fill(trackX, top, trackX + 3, bottom, Palette.SCROLL_TRACK);
        int thumbH = Math.max(16, (int) Math.round(((double) viewportH / contentHeight) * viewportH));
        int travel = Math.max(0, viewportH - thumbH);
        int thumbY = top + (int) Math.round(((double) scrollOffset / maxScroll) * travel);
        canvas.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, Palette.SCROLL_THUMB);
    }
}
