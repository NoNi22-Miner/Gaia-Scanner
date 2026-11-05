package com.gaiascanner.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class OrientationTest {

    @Test
    fun exifValuesMapToRotationAndFlip() {
        assertEquals(ExifTransform(0, false), ExifTransform.fromExif(1))
        assertEquals(ExifTransform(0, false), ExifTransform.fromExif(0)) // undefined
        assertEquals(ExifTransform(90, false), ExifTransform.fromExif(6))
        assertEquals(ExifTransform(180, false), ExifTransform.fromExif(3))
        assertEquals(ExifTransform(270, false), ExifTransform.fromExif(8))
        assertEquals(ExifTransform(0, true), ExifTransform.fromExif(2))
        assertEquals(ExifTransform(90, true), ExifTransform.fromExif(5))
    }

    @Test
    fun rotationNormalizes() {
        assertEquals(0, Rotation.normalize(360))
        assertEquals(270, Rotation.normalize(-90))
        assertEquals(90, Rotation.normalize(450))
        assertEquals(0, Rotation.rotateClockwise(270))
        assertEquals(90, Rotation.rotateClockwise(0))
    }

    @Test
    fun rotatedSizeSwapsForQuarterTurns() {
        assertEquals(300 to 400, Rotation.rotatedSize(400, 300, 90))
        assertEquals(400 to 300, Rotation.rotatedSize(400, 300, 180))
        assertEquals(300 to 400, Rotation.rotatedSize(400, 300, 270))
    }
}
