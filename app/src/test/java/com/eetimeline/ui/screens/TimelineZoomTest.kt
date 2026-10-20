package com.eetimeline.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineZoomTest {
    @Test
    fun zoomPreservesTheFocalYearAndClampsToBounds() {
        val contentWidth = 1_200f
        val viewportWidth = 320f
        val padding = 20f
        val scroll = 260f
        val focal = 130f
        val transformed = timelineZoomTransform(
            currentZoom = 1f,
            requestedZoom = 2f,
            minimumZoom = 1f,
            maximumZoom = 4f,
            contentWidthPx = contentWidth,
            viewportWidthPx = viewportWidth,
            scrollPx = scroll,
            focalPointPx = focal,
            horizontalPaddingPx = padding,
        )
        val oldProgress = (scroll + focal - padding) / (contentWidth - 2f * padding)
        val newContentWidth = contentWidth * transformed.zoom
        val newProgress =
            (transformed.scrollPx + focal - padding) / (newContentWidth - 2f * padding)

        assertEquals(2f, transformed.zoom, 0.0001f)
        assertEquals(oldProgress, newProgress, 0.0001f)

        val clamped = timelineZoomTransform(
            currentZoom = transformed.zoom,
            requestedZoom = 8f,
            minimumZoom = 1f,
            maximumZoom = 4f,
            contentWidthPx = newContentWidth,
            viewportWidthPx = viewportWidth,
            scrollPx = transformed.scrollPx,
            focalPointPx = focal,
            horizontalPaddingPx = padding,
        )
        assertEquals(4f, clamped.zoom, 0.0001f)
        assertTrue(clamped.scrollPx in 0f..(contentWidth * 4f - viewportWidth))

        val minimum = timelineZoomTransform(
            currentZoom = transformed.zoom,
            requestedZoom = 0.5f,
            minimumZoom = 1f,
            maximumZoom = 4f,
            contentWidthPx = newContentWidth,
            viewportWidthPx = viewportWidth,
            scrollPx = transformed.scrollPx,
            focalPointPx = focal,
            horizontalPaddingPx = padding,
        )
        assertEquals(1f, minimum.zoom, 0.0001f)
        assertTrue(minimum.scrollPx in 0f..(contentWidth - viewportWidth))
    }
}
