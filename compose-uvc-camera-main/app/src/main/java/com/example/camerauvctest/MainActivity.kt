package com.example.camerauvctest

import android.content.ContentValues
import android.content.ContentValues.TAG
import com.jiangdg.ausbc.CameraClient
import com.jiangdg.ausbc.camera.CameraUvcStrategy
import com.jiangdg.ausbc.camera.bean.CameraRequest
import com.jiangdg.ausbc.widget.AspectRatioSurfaceView
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.SurfaceHolder
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.camerauvctest.ui.theme.CameraUVCTestTheme
import com.google.accompanist.permissions.*
import com.jiangdg.ausbc.callback.ICaptureCallBack
import com.jiangdg.ausbc.utils.ToastUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.common.internal.ImageUtils
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.jiangdg.ausbc.callback.IPreviewDataCallBack
import com.jiangdg.ausbc.render.RenderManager
import com.jiangdg.ausbc.utils.GLBitmapUtils
import com.jiangdg.ausbc.utils.Logger
import java.io.OutputStream
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val PREVIEW_RESOLUTION_WIDTH = 640
const val PREVIEW_RESOLUTION_HEIGHT = 480

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CameraUVCTestTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    RequestMultiplePermissions(
                        permissions = listOf(
                            android.Manifest.permission.CAMERA,
                            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            android.Manifest.permission.READ_EXTERNAL_STORAGE,
                            android.Manifest.permission.ACCESS_NETWORK_STATE,
                            android.Manifest.permission.ACCESS_WIFI_STATE,
                            android.Manifest.permission.INTERNET,
                            android.Manifest.permission.RECORD_AUDIO,
                        )
                    )
               }
            }
        }
    }
}

@ExperimentalPermissionsApi
@Composable
fun RequestMultiplePermissions(
    permissions: List<String>,
    deniedMessage: String = "This app requires the camera and access to storage. If it doesn't work, then you'll have to do it manually from the settings.",
    rationaleMessage: String = "To use this app's functionalities, you need to give us the permission.",
) {
    val multiplePermissionsState = rememberMultiplePermissionsState(permissions)

    HandleRequests(
        multiplePermissionsState = multiplePermissionsState,
        deniedContent = { shouldShowRationale ->
            PermissionDeniedContent(
                deniedMessage = deniedMessage,
                rationaleMessage = rationaleMessage,
                shouldShowRationale = shouldShowRationale,
                onRequestPermission = { multiplePermissionsState.launchMultiplePermissionRequest() }
            )
        },
        content = {
            Content(
                text = "PERMISSION GRANTED!",
                showButton = false
            ) {}
        }
    )
}

@ExperimentalPermissionsApi
@Composable
private fun HandleRequests(
    multiplePermissionsState: MultiplePermissionsState,
    deniedContent: @Composable (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    var shouldShowRationale by remember { mutableStateOf(false) }
    val result = multiplePermissionsState.permissions.all {
        shouldShowRationale = it.status.shouldShowRationale
        it.status == PermissionStatus.Granted
    }
    if (result) {
        Toast.makeText(LocalContext.current, "Permission granted successfully", Toast.LENGTH_SHORT).show()
        MyApp()
    } else {
        deniedContent(shouldShowRationale)
    }
}

@ExperimentalPermissionsApi
@Composable
fun PermissionDeniedContent(
    deniedMessage: String,
    rationaleMessage: String,
    shouldShowRationale: Boolean,
    onRequestPermission: () -> Unit
) {
    if (shouldShowRationale) AlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                text = "Permission Request",
                style = TextStyle(
                    fontSize = MaterialTheme.typography.h6.fontSize,
                    fontWeight = FontWeight.Bold
                )
            )
        },
        text = {
            Text(rationaleMessage)
        },
        confirmButton = {
            Button(onClick = onRequestPermission) {
                Text("Give Permission")
            }
        }
    ) else Content(text = deniedMessage, onClick = onRequestPermission)

}

@Composable
fun Content(text: String, showButton: Boolean = true, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(50.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = text, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(12.dp))
        if (showButton) {
            Button(onClick = onClick) {
                Text(text = "Request")
            }
        }
    }
}

@Composable
fun rememberCameraClient(context: Context): CameraClient = remember {
    CameraClient.newBuilder(context).apply {
        setEnableGLES(true)
        setRawImage(false)
        setCameraStrategy(CameraUvcStrategy(context))
        setCameraRequest(
            CameraRequest.Builder()
                .setFrontCamera(false)
                .setPreviewWidth(PREVIEW_RESOLUTION_WIDTH)
                .setPreviewHeight(PREVIEW_RESOLUTION_HEIGHT)
                .create()
        )
        openDebug(false)
    }.build()
}

@Composable
fun MyApp() {
    val context = LocalContext.current
    OpenCVLoader.initDebug()
    val model = remember {
        CameraPreviewModel()
    }
    val mediaPlayer = MediaPlayer.create(context, model.warningSound)
    Box(modifier = Modifier.fillMaxWidth()) {
        UVCCameraPreview(rememberCameraClient(context), model)
    }
    WarningStatus(model, mediaPlayer, context)
}

@Composable
fun UVCCameraPreview(cameraClient: CameraClient, model: CameraPreviewModel) {
    AndroidView(
        factory = { ctx ->
            AspectRatioSurfaceView(ctx).apply {
                this.holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        cameraClient.openCamera(this@apply)
                        cameraClient.addPreviewDataCallBack(object : IPreviewDataCallBack {
                            override fun onPreviewData(data: ByteArray?, format: IPreviewDataCallBack.DataFormat) {
                                if (data == null) {
                                    return;
                                }
                                model.imageData = data
                                imgTextDetect(model)
                            }
                        })
                    }
                    override fun surfaceChanged(
                        holder: SurfaceHolder,
                        format: Int,
                        width: Int,
                        height: Int
                    ) {
                        cameraClient.setRenderSize(width, height)
                    }
                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        cameraClient.closeCamera()
                    }
                })
            }
        }
    )
    val currentContext = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally,
    ){
        Button(
            onClick = {
                captureImage(cameraClient, currentContext, model)
            }
        ) {
            Text("Take Picture")
        }
    }
}

// if recognize 5 symbols -> green color
@Composable
fun WarningStatus(model: CameraPreviewModel, mediaPlayer: MediaPlayer, context: Context) {
    IndicatorView(color = model.warningColor)
    if (model.warningStatus) {
        mediaPlayer.start()
    }
}

@Composable
fun IndicatorView(color: Color) {
    Row(
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            modifier = Modifier
                .padding(10.dp)
                .size(20.dp)
                .clip(shape = RoundedCornerShape(50))
                .background(color)
        )
    }
}

private fun saveImageInternal(bitmap: Bitmap, context: Context) {
    val date = mDateFormat.format(System.currentTimeMillis())
    val title = "IMG_JJCamera_$date"
    val displayName = "$title.jpg"
    val path = "$mCameraDir/$displayName"
    val fos: FileOutputStream?
    try {
        fos = FileOutputStream(path)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
        val file = File(path)
        if (file.length() == 0L) {
            file.delete()
            return
        }
    } catch (e: FileNotFoundException) {
        Toast.makeText(context, "Image is not saved", Toast.LENGTH_SHORT).show()
    } catch (e: IOException) {
        Toast.makeText(context, "Image is not saved", Toast.LENGTH_SHORT).show()
    }
    val values = ContentValues()
    values.put(MediaStore.Images.ImageColumns.TITLE, title)
    values.put(MediaStore.Images.ImageColumns.DISPLAY_NAME, displayName)
    values.put(MediaStore.Images.ImageColumns.DATA, path)
    values.put(MediaStore.Images.ImageColumns.DATE_TAKEN, date)
    context.contentResolver?.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
}

fun Context.createImageFile(): File {
    // Create an image file name
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
    val imageFileName = "JPEG_" + timeStamp + "_"
    val image = File.createTempFile(
        imageFileName, /* prefix */
        ".jpg", /* suffix */
        externalCacheDir      /* directory */
    )
    return image
}

fun captureImage(cameraClient: CameraClient, context:Context, model: CameraPreviewModel) {
//    cameraClient.captureImage(object : ICaptureCallBack {
//        override fun onBegin() {
//            Toast.makeText(context, "onBegin", Toast.LENGTH_SHORT).show()
//            Log.i("CameraClient", "onBegin")
//        }
//
//        override fun onError(error: String?) {
//            Toast.makeText(context, "onError", Toast.LENGTH_SHORT).show()
//            ToastUtils.show(error ?: "未知异常")
//            Log.i("CameraClient", "onError")
//        }
//
//        override fun onComplete(path: String?) {
//            Toast.makeText(context, "onComplete", Toast.LENGTH_SHORT).show()
//            ToastUtils.show("OnComplete")
//            Log.i("CameraClient", "onComplete")
//        }
//    })
    val image = opencvHandler(model.imageData)
    saveImageInternal(image, context)
}

fun imgTextDetect(model: CameraPreviewModel) {
    val image = model.imageData.let {
        InputImage.fromByteArray(
            it,
            PREVIEW_RESOLUTION_WIDTH,
            PREVIEW_RESOLUTION_HEIGHT,
            0,
            InputImage.IMAGE_FORMAT_NV21
        )
    }
    // waiting the end of recognition
    if (!model.recognitionStatus) {
        model.recognitionStatus = true
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val clearText = visionText.text.filterNot { it.isWhitespace() }
                model.warningStatus = clearText.length >= 5
                model.recognitionStatus = false
            }
            .addOnFailureListener { e ->
                model.warningStatus = false
                model.recognitionStatus = false
            }
    }
}

fun opencvHandler(byteArray: ByteArray):Bitmap {
    val bitmap = byteBufferToBitmap(byteArray, Size(PREVIEW_RESOLUTION_WIDTH, PREVIEW_RESOLUTION_HEIGHT))
    val mat = Mat()
    Utils.bitmapToMat(bitmap, mat)

    Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGB2GRAY)

    val grayBitmap = bitmap.copy(bitmap.config, true)
    Utils.matToBitmap(mat, grayBitmap)
    return grayBitmap
}
