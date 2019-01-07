package com.omeric.android.rgbit

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_main.*
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.view.TextureView
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.util.Size
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.params.StreamConfigurationMap
import android.support.v7.app.AlertDialog
import android.util.Log
import android.widget.Toast


class MainActivity : AppCompatActivity()
{
    /**
     * [TextureView.SurfaceTextureListener] handles several lifecycle events on a
     * [TextureView].
     */
    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener
    {
        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int)
        {
            setUpCamera()
            openCamera()
        }

        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int)
        {

        }

        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture) = true

        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
    }

    /**
     * ID of the current [CameraDevice].
     */
    private lateinit var cameraId: String

    /**
     * A [CameraCaptureSession] for camera preview.
     */
    private var captureSession: CameraCaptureSession? = null

    /**
     * A reference to the opened [CameraDevice].
     */
    private var cameraDevice: CameraDevice? = null

    /**
     * The [android.util.Size] of camera preview.
     */
    private lateinit var previewSize: Size

    private val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
//    private val cameraFacing = CameraCharacteristics.LENS_FACING_BACK


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Example of a call to a native method
//        sample_text.text = stringFromJNI()



    }

    /**
     * Sets up member variables related to camera.
     */
    private fun setUpCamera()
    {
        try
        {
            for (cameraId in cameraManager.cameraIdList)
            {
                val cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)
                if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK)
                {
                    val streamConfigurationMap = cameraCharacteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    previewSize = streamConfigurationMap!!.getOutputSizes(SurfaceTexture::class.java)[0]
                    this.cameraId = cameraId

                    // We've found a viable camera and finished setting up member variables,
                    // so we don't need to iterate through other available cameras.
                    return
                }
            }
        }
        catch (e: CameraAccessException)
        {
            Log.e(TAG, e.toString())
        }
        catch (e: NullPointerException)
        {
        // Currently an NPE is thrown when the Camera2API is used but not supported on the
        // device this code runs.
            Toast.makeText(this, resources.getString(R.string.error), Toast.LENGTH_SHORT).show();
        }
    }

    private fun requestCameraPermission() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA))
        {
            AlertDialog.Builder(this)
                .setMessage(R.string.request_permission)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    requestPermissions(arrayOf(Manifest.permission.CAMERA),
                        REQUEST_CAMERA_PERMISSION)
                }
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    finish()
                }
                .create()
        } else
        {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>,
                                            grantResults: IntArray)
    {
        if (requestCode == REQUEST_CAMERA_PERMISSION)
        {
            for (grantResult in grantResults)
            {
                if (grantResult != PackageManager.PERMISSION_GRANTED)
                {
                    Toast.makeText(this, resources.getString(R.string.error), Toast.LENGTH_SHORT).show();
                }
            }
        }
        else
        {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
//    external fun stringFromJNI(): String

    companion object {

        // Used to load the 'native-lib' library on application startup.
/*
        init
        {
            System.loadLibrary("native-lib")
        }
*/
        private val TAG = "gipsy:" + this::class.java.name
        private const val REQUEST_CAMERA_PERMISSION = 1

    }
}


