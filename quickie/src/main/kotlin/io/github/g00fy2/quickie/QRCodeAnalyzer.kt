package io.github.g00fy2.quickie

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.graphics.drawable.BitmapDrawable
import android.media.Image
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.io.ByteArrayOutputStream
import kotlin.math.min


internal class QRCodeAnalyzer(
  private val barcodeFormats: IntArray,
  private val onSuccess: ((String) -> Unit),
  private val onFailure: ((Exception) -> Unit),
  private val onPassCompleted: ((Boolean) -> Unit),
) : ImageAnalysis.Analyzer {

  private var collectedTexts: ArrayList<String> = arrayListOf("","","")

  private val barcodeScanner by lazy {
    val optionsBuilder = if (barcodeFormats.size > 1) {
      BarcodeScannerOptions.Builder().setBarcodeFormats(barcodeFormats.first(), *barcodeFormats.drop(1).toIntArray())
    } else {
      BarcodeScannerOptions.Builder().setBarcodeFormats(barcodeFormats.firstOrNull() ?: Barcode.FORMAT_UNKNOWN)
    }
    BarcodeScanning.getClient(optionsBuilder.build())
  }

  @Volatile
  private var failureOccurred = false
  private var failureTimestamp = 0L

  @RequiresApi(Build.VERSION_CODES.Q)
  @ExperimentalGetImage
  override fun analyze(imageProxy: ImageProxy) {
    if (imageProxy.image == null) return

    // throttle analysis if error occurred in previous pass
    if (failureOccurred && System.currentTimeMillis() - failureTimestamp < 1000L) {
      imageProxy.close()
      return
    }

    failureOccurred = false
    val inputImages: ArrayList<InputImage> = triSeparator(imageProxy)
    for(i in 0..2) {
      println("index:$i  1: ${collectedTexts[0]} 2: ${collectedTexts[1]} 3: ${collectedTexts[2]}")
      barcodeScanner.process(inputImages[i])
        .addOnSuccessListener {
            codes -> codes.mapNotNull { it }.firstOrNull()?.let {
          collectedTexts[i] = it.rawValue?:""
          if (collectedTexts[0] != "" && collectedTexts[1] != "" && collectedTexts[2] != "") {
            val res: String = getCompletedBarcode(collectedTexts)
            collectedTexts = arrayListOf("", "", "")
            onSuccess(res)
          }
        }

        }
        .addOnFailureListener {
          failureOccurred = true
          failureTimestamp = System.currentTimeMillis()
          onFailure(it)
        }
        .addOnCompleteListener {
          println("COMPLETED $i")
          if(failureOccurred) {
            onPassCompleted(failureOccurred)
            imageProxy.close()
          }
          if(i == 2) {
            onPassCompleted(failureOccurred)
            imageProxy.close()
          }
        }
    }
  }

  private fun Image.toBitmap(): Bitmap {
    val yBuffer = planes[0].buffer // Y
    val vuBuffer = planes[2].buffer // VU
    val ySize = yBuffer.remaining()
    val vuSize = vuBuffer.remaining()
    val nv21 = ByteArray(ySize + vuSize)
    yBuffer.get(nv21, 0, ySize)
    vuBuffer.get(nv21, ySize, vuSize)

    val yuvImage = YuvImage(nv21, ImageFormat.NV21, this.width, this.height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 50, out)
    val imageBytes = out.toByteArray()
    return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
  }

  @SuppressLint("UnsafeOptInUsageError")
  @RequiresApi(Build.VERSION_CODES.Q)
  private fun triSeparator(imgPrxy: ImageProxy): ArrayList<InputImage> {

    val img = imgPrxy.image!!
    val imageSource: Bitmap = img.toBitmap()
    val temp: Bitmap.Config = Bitmap.Config.ARGB_8888
    val imageR: Bitmap = imageSource.copy(temp, true)
    val imageG: Bitmap = imageSource.copy(temp, true)
    val imageB: Bitmap = imageSource.copy(temp, true)

    val sourceWidth: Int = imageR.width
    val sourceHeight: Int = imageR.height

    val minLength: Int = min(sourceWidth, sourceHeight)
    val edgeLength: Int = minLength - (minLength / 4)
    val widthOffset = (sourceWidth - edgeLength)/2
    val heightOffset = (sourceHeight - edgeLength)/2

    for(row in heightOffset until sourceHeight-heightOffset) {
      for(col in widthOffset until sourceWidth-widthOffset) {
//        imageR.getPixels()
        val touchedRGB: Int = imageR.getPixel(col, row)
        val r: Int = Color.red(touchedRGB);
        val g: Int = Color.green(touchedRGB);
        val b: Int = Color.blue(touchedRGB);

        if (collectedTexts[0] == "") {
          if (r > 160)
            imageR.setPixel(col, row, Color.rgb(255, 255, 255))
          else
            imageR.setPixel(col, row, Color.rgb(0, 0, 0))
        }
        if (collectedTexts[1] == "") {
          if (g > 175)
            imageG.setPixel(col, row, Color.rgb(255, 255, 255))
          else
            imageG.setPixel(col, row, Color.rgb(0, 0, 0))
        }
        if (collectedTexts[2] == "") {
          if (b > 160)
            imageB.setPixel(col, row, Color.rgb(255, 255, 255))
          else
            imageB.setPixel(col, row, Color.rgb(0, 0, 0))
        }
      }
    }

    return arrayListOf(InputImage.fromBitmap(imageR, imgPrxy.imageInfo.rotationDegrees),
                       InputImage.fromBitmap(imageG, imgPrxy.imageInfo.rotationDegrees),
                       InputImage.fromBitmap(imageB, imgPrxy.imageInfo.rotationDegrees))
  }

  private fun getCompletedBarcode(texts: ArrayList<String>): String {
    val mergedText: StringBuilder = java.lang.StringBuilder()
    for(index in 0 until (texts[0].length)) {
      mergedText.insert(0, texts[0][index])
      mergedText.insert(0, texts[1][index])
      mergedText.insert(0, texts[2][index])
    }
//    val newBarcode: Barcode = Barcode()
//    val result: Barcode = Barcode()
    return mergedText.toString()
  }

  @ExperimentalGetImage
  @Suppress("UnsafeCallOnNullableType")
  private fun ImageProxy.toInputImage() = InputImage.fromMediaImage(image!!, imageInfo.rotationDegrees)


}