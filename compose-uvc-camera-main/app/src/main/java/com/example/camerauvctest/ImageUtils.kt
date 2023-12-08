package com.example.camerauvctest

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.os.Environment
import android.util.Size
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Locale

internal fun imageToByteBuffer(image: Image): ByteBuffer {
    val y = image.planes[0].buffer
    val cr = image.planes[1].buffer
    val cb = image.planes[2].buffer

    return ByteBuffer.allocate(image.height * image.width * 2)
        .put(y).put(cb).put(cr)
}

internal fun byteBufferToBitmap(byteArray: ByteArray, size: Size): Bitmap {
    val yuvImage = YuvImage(byteArray, ImageFormat.NV21, size.width, size.height, null)
    val out = ByteArrayOutputStream()

    yuvImage.compressToJpeg(Rect(0, 0, size.width, size.height), 100, out)
    val imageBytes = out.toByteArray()
    return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
}

internal val mDateFormat by lazy {
    SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.getDefault())
}

internal val mCameraDir by lazy {
    "${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)}/Camera"
}
