| V3 model is used (faster, more accurate) | currently V1 model will be downloaded
```kotlin
// bundled:  
implementation("io.github.g00fy2.quickie:quickie-bundled:1.4.0")

// unbundled:
implementation("io.github.g00fy2.quickie:quickie-unbundled:1.4.0")
```

## Quick Start
To use the QR scanner simply register the `ScanQRCode()` ActivityResultContract together with a callback during `init` or `onCreate()` lifecycle of your Activity/Fragment and use the returned ActivityResultLauncher to launch the QR scanner Activity.
```kotlin
val scanQrCode = registerForActivityResult(ScanQRCode(), ::handleResult)

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    …
    binding.button.setOnClickListener { scanQrCode.launch(null) }
}

fun handleResult(result: QRResult) {
    …
```


## Screenshots / Sample App
You can find the sample app APKs inside the [release](https://github.com/G00fY2/quickie/releases) assets.

## Requirements
* AndroidX
* Min SDK 21+ (required by CameraX)
