package com.example.camerauvctest

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import com.example.camerauvctest.R
import com.jiangdg.ausbc.render.effect.AbstractEffect
import com.jiangdg.ausbc.render.effect.bean.CameraEffect

class CameraPreviewModel: ViewModel() {
    //settings
    private val successRecognitionColor = Color.Green
    private val failedRecognitionColor = Color.Red
    val warningSound = R.raw.warning_sound

    var imageData by mutableStateOf(ByteArray(0))
    var warningStatus by mutableStateOf(false)
    var recognitionStatus = false
    val warningColor: Color
        get() =
            if (warningStatus) {
                successRecognitionColor
            } else {
                failedRecognitionColor
            }
}