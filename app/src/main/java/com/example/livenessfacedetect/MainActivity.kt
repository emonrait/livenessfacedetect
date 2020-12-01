package com.example.livenessfacedetect

import android.os.Bundle
import android.util.Size
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions
import kotlinx.android.synthetic.main.activity_main.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.android.synthetic.main.activity_main.cameraView as cameraView1

class MainActivity : AppCompatActivity() {
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        startCamera()
    }

    var highAccuracyOpts = FirebaseVisionFaceDetectorOptions.Builder()
            .setPerformanceMode(FirebaseVisionFaceDetectorOptions.ACCURATE)
            .setLandmarkMode(FirebaseVisionFaceDetectorOptions.ALL_LANDMARKS)
            .setClassificationMode(FirebaseVisionFaceDetectorOptions.ALL_CLASSIFICATIONS)
            .build()

    val faceDetector =
            FirebaseVision.getInstance()
                    .getVisionFaceDetector(highAccuracyOpts)

    //Function that creates and displays the camera preview
    private fun startCamera() {

        val previewConfig = PreviewConfig.Builder()
                .apply {
                    setTargetResolution(Size(1920, 1080))
                }
                .build()

        val preview = Preview(previewConfig)

        preview.setOnPreviewOutputUpdateListener {
            val parent = cameraView1.parent as ViewGroup
            parent.removeView(cameraView1)
            parent.addView(cameraView1, 0)
            cameraView1.surfaceTexture = it.surfaceTexture
        }

        val analyzerConfig = ImageAnalysisConfig.Builder().apply {

            setImageReaderMode(
                    ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE
            )
        }.build()

        val imageAnalysis = ImageAnalysis(analyzerConfig).apply {
            analyzer = ImageProcessor()
        }

        CameraX.bindToLifecycle(this, preview, imageAnalysis)
    }

    inner class ImageProcessor : ImageAnalysis.Analyzer {
        private val TAG = javaClass.simpleName

        private var lastAnalyzedTimestamp = 0L

        private fun degreesToFirebaseRotation(degrees: Int): Int = when (degrees) {
            0 -> FirebaseVisionImageMetadata.ROTATION_0
            90 -> FirebaseVisionImageMetadata.ROTATION_90
            180 -> FirebaseVisionImageMetadata.ROTATION_180
            270 -> FirebaseVisionImageMetadata.ROTATION_270
            else -> throw Exception("Rotation must be 0, 90, 180, or 270.")
        }

        override fun analyze(image: ImageProxy?, rotationDegrees: Int) {
            val currentTimestamp = System.currentTimeMillis()
            if (currentTimestamp - lastAnalyzedTimestamp >=
                    TimeUnit.SECONDS.toMillis(1)
            ) {
                val imageRotation = degreesToFirebaseRotation(rotationDegrees)
                image?.image?.let {
                    val visionImage = FirebaseVisionImage.fromMediaImage(it, imageRotation)
                    faceDetector.detectInImage(visionImage)
                            .addOnSuccessListener { faces ->
                                faces.forEach { face ->
                                    if (face.leftEyeOpenProbability < 0.4 || face.rightEyeOpenProbability < 0.4 || face.smilingProbability < 0.4) {
                                        label.text = "Real Face"
                                    } else {
                                        label.text = "Not Real Face"
                                    }
                                }
                            }
                            .addOnFailureListener {
                                it.printStackTrace()
                            }
                }
            }

        }
    }
}
