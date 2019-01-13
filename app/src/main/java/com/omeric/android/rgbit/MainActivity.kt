package com.omeric.android.rgbit

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.*
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.hardware.camera2.*
import android.view.TextureView
import android.util.Size
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.renderscript.*
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.util.Log
import android.util.Range
import android.util.SparseIntArray
import android.view.Surface
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.lang.Long.signum
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit


class MainActivity : AppCompatActivity(), ActivityCompat.OnRequestPermissionsResultCallback
{
    /**
     * [TextureView.SurfaceTextureListener] handles several lifecycle events on a
     * [TextureView].
     * This listener can be used to be notified when the surface texture associated with this texture view is available
     */
    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener
    {
        /** Invoked when a [TextureView]'s SurfaceTexture is ready for use */
        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int)
        {
            Log.d(TAG, ":surfaceTextureListener::onSurfaceTextureAvailable")
            openCamera(width, height)
        }

        /** Invoked when the [SurfaceTexture]'s buffers size changed */
        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int)
        {
            Log.d(TAG, ":surfaceTextureListener::onSurfaceTextureSizeChanged")
            configureTransform(width, height)
        }

        /** Invoked when the specified [SurfaceTexture] is about to be destroyed */
        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture) = true

        /** Invoked when the specified [SurfaceTexture] is updated through [SurfaceTexture.updateTexImage] */
        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
    }

    /**
     * ID of the current [CameraDevice]
     */
    private lateinit var cameraId: String

    /**
     * An [AutoFitTextureView] for camera preview
     */
    private lateinit var autoFitTextureView: AutoFitTextureView

    private lateinit var topColor1TextView: TextView
    private lateinit var topColor2TextView: TextView
    private lateinit var topColor3TextView: TextView
    private lateinit var topColor4TextView: TextView
    private lateinit var topColor5TextView: TextView

    /**
     * A [CameraCaptureSession] for camera preview
     */
    private var captureSession: CameraCaptureSession? = null

    /**
     * A reference to the opened [CameraDevice]
     */
    private var cameraDevice: CameraDevice? = null

    /**
     * The [android.util.Size] of camera preview
     */
    private lateinit var previewSize: Size

    /**
     * [CameraDevice.StateCallback] is called when [CameraDevice] changes its state.
     *
     * A callback instance must be provided to the [CameraManager.openCamera] method to open a camera device.
     * These state updates include notifications about the device completing startup
     * (allowing for [CameraDevice.createCaptureSession] to be called), about device disconnection or closure,
     * and about unexpected device errors.
     * Events about the progress of specific CaptureRequests are provided through a [CameraCaptureSession.CaptureCallback]
     */
    private val stateCallback = object : CameraDevice.StateCallback()
    {
        // The method called when a camera device has finished opening
        override fun onOpened(cameraDevice: CameraDevice)
        {
            Log.d(TAG, ":stateCallback::onOpened")
            cameraLock.release()
            this@MainActivity.cameraDevice = cameraDevice
            createCameraPreviewSession()
        }

        // The method called when a camera device is no longer available for use
        override fun onDisconnected(cameraDevice: CameraDevice)
        {
            Log.d(TAG, ":stateCallback::onDisconnected")
            cameraLock.release()
            cameraDevice.close()
            this@MainActivity.cameraDevice = null
        }

        override fun onError(cameraDevice: CameraDevice, error: Int)
        {
            Log.d(TAG, ":stateCallback::onError = $error")
            onDisconnected(cameraDevice)
            finish()
        }
    }

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private var backgroundThread: HandlerThread? = null

    /**
     * A [Handler] for running tasks in the background.
     */
    private var backgroundHandler: Handler? = null

    /**
     * An [ImageReader] that handles still image capture.
     */
    private var imageReader: ImageReader? = null

    /**
     * This a callback object for the [ImageReader]. "onImageAvailable" will be called when a
     * still image is ready to be saved
     */
    private val onImageAvailableListener = ImageReader.OnImageAvailableListener{
//        Log.d(TAG, ":onImageAvailableListener")
        /**
         * [ImageReader.acquireLatestImage] will acquire the latest Image from the [ImageReader]'s queue,
         * dropping older one - This function is recommended to use over [ImageReader.acquireNextImage] for most use-cases,
         * as it's more suited for real-time processing
         */
        // add the Runnable to the message queue
        val image = it.acquireLatestImage()
        if (image != null)
        {
            backgroundHandler?.post {
//                Log.d(TAG, ":onImageAvailableListener:Runnable:")
                jpegToBitmap(image)
                image.close()
            }
        }
/*
        else
        {
            backgroundHandler?.post(ImageSaver(it.acquireNextImage(), file, this))
        }
*/
    }

    /**
     * [CaptureRequest.Builder] for the camera preview
     */
    private lateinit var previewRequestBuilder: CaptureRequest.Builder

    /**
     * [CaptureRequest] generated by [.previewRequestBuilder]
     */
    private lateinit var previewCaptureRequest: CaptureRequest

    /**
     * A [Semaphore] to prevent the app from exiting before closing the camera.
     */
    private val cameraLock = Semaphore(1)

    /**
     * Whether the current camera device supports Flash or not.
     */
    private var flashSupported = false

    /**
     * Orientation of the camera sensor
     */
    private var sensorOrientation = 0

    /**
     * A [CameraCaptureSession.CaptureCallback] that handles events related to JPEG capture
     *
     * A callback object for tracking the progress of a [CaptureRequest] submitted to the
     * camera device
     *
     * This callback is invoked when a request triggers a capture to start, and when the capture is complete.
     * In case on an error capturing an image, the error method is triggered instead of the completion method
     */

    private val captureCallback = object : CameraCaptureSession.CaptureCallback()
    {
        /**
         * [CaptureResult] objects contain only metadata about a frame capture, no actual pixel information
         * The associated pixel data are sent to the designated target Surface in [CaptureRequest.Builder]
         */
        override fun onCaptureProgressed(session: CameraCaptureSession, request: CaptureRequest, partialResult: CaptureResult)
        {
//            Log.d(TAG, ":captureCallback::onCaptureProgressed")
        }

        /**
         * [TotalCaptureResult] is the total assembled results of a single image capture from the image sensor
         *
         * Contains the final configuration for the capture hardware (sensor, lens, flash), the processing pipeline,
         * the control algorithms, and the output buffers
         *
         * All properties returned by [CaptureResult.get] using the keys in [CaptureResult]
         * i.e. [CaptureResult.CONTROL_AF_MODE]
         */
        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult)
        {
//            Log.d(TAG, ":captureCallback::onCaptureCompleted")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.d(TAG, ":onCreate")

        // Example of a call to a native method
//        sample_text.text = stringFromJNI()
        autoFitTextureView = findViewById(R.id.texture)
        topColor1TextView = findViewById(R.id.txtVw_top1)
        topColor2TextView = findViewById(R.id.txtVw_top2)
        topColor3TextView = findViewById(R.id.txtVw_top3)
        topColor4TextView = findViewById(R.id.txtVw_top4)
        topColor5TextView = findViewById(R.id.txtVw_top5)
    }

    override fun onResume()
    {
        super.onResume()
        Log.d(TAG, ":onResume")
        startBackgroundThread()

        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).
        if (autoFitTextureView.isAvailable)
        {
            openCamera(autoFitTextureView.width, autoFitTextureView.height)
        }
        else
        {
            autoFitTextureView.surfaceTextureListener = surfaceTextureListener
        }
    }

    override fun onPause()
    {
        Log.d(TAG, ":onPause")
        closeCamera()
        stopBackgroundThread()
        super.onPause()
    }

    private fun requestCameraPermission()
    {
        Log.d(TAG, ":requestCameraPermission")
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA))
        {
            AlertDialog.Builder(this)
                .setMessage(R.string.request_permission)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    requestPermissions(
                        arrayOf(Manifest.permission.CAMERA),
                        REQUEST_CAMERA_PERMISSION
                    )
                }
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    finish()
                }
                .create()
        }
        else
        {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray)
    {
        Log.d(TAG, ":onRequestPermissionsResult")
        if (requestCode == REQUEST_CAMERA_PERMISSION)
        {
            for (grantResult in grantResults)
            {
                if (grantResult != PackageManager.PERMISSION_GRANTED)
                {
                    Toast.makeText(this, resources.getString(R.string.error), Toast.LENGTH_SHORT).show()
                }
            }
        }
        else
        {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }


    /**
     * Sets up member variables related to camera.
     *
     * @param width  The width of available size for camera preview
     * @param height The height of available size for camera preview
     *
     * NOTE:
     * It might be more efficient to use the [ImageFormat.FLEX_RGB_888], whereas [ImageFormat.JPEG] &
     * [ImageFormat.YUV_420_888] are ALWAYS supported by the [android.hardware.camera2] API
     */
    private fun setUpCameraOutputs(width: Int, height: Int) : Boolean
    {
        Log.d(TAG, ":setUpCameraOutputs")
        // A system service manager for detecting, characterizing, and connecting to CameraDevices
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        try
        {
            Log.d(TAG, ":setUpCameraOutputs: number of cameraId's = ${cameraManager.cameraIdList.size}")

            if (cameraManager.cameraIdList.isEmpty()) throw NullPointerException("No camera is available")

            // looping through the cameras the device has until we found a one that satisfies the requirements
            for (cameraId in cameraManager.cameraIdList)
            {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)

                val cameraDirection = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (cameraDirection != null && cameraDirection == CameraCharacteristics.LENS_FACING_FRONT)
                {
                    // skip if it's the front facing camera
                    continue
                }
                Log.d(TAG, ":setUpCameraOutputs:02")

                /**
                 * StreamConfigurationMap the authoritative list for all output formats (and sizes respectively for that format)
                 * that are supported by a camera device
                 * This also contains the minimum frame durations and stall durations for each format/size combination
                 * that can be used to calculate effective frame rate when submitting multiple captures
                 */
                val streamConfiguration = characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
                ) ?: continue // skip if StreamConfigurationMap is null
                Log.d(TAG, ":setUpCameraOutputs:03")



/*
                val outpusFormats = streamConfiguration.outputFormats
//                Log.d(TAG, ":setUpCameraOutputs:03.1: outpusFormats = ${outpusFormats} ")
                for (element in outpusFormats) {
                    Log.d(TAG, ":setUpCameraOutputs:03.1: outpusFormats = 0x${Integer.toHexString(element)} ")
                }
*/

                /**
                 * For still image captures, we use the largest available size:
                 * Get an array of supported sizes for a given format, and then get the largest size by using
                 * the [CompareSizesByArea] comperator, the result will be save in a [Size] class - an Immutable class
                 * for describing width and height dimensions in pixels
                 */
                val largestOutputSize = Collections.max(
//                    Arrays.asList(*streamConfiguration.getOutputSizes(ImageFormat.YUV_420_888)),
                    Arrays.asList(*streamConfiguration.getOutputSizes(ImageFormat.JPEG)),
                    CompareSizesByArea())
                Log.d(TAG, ":setUpCameraOutputs:largestOutputSize: width = ${largestOutputSize.width}, " +
                        "height = ${largestOutputSize.height}")

                /** The ImageReader class allows direct application access to image data rendered
                 * into a [android.view.Surface] which is a raw buffer that the
                 * [android.hardware.camera2.CameraDevice.createCaptureSession] draw into
                 *
                 * The image data is encapsulated in [android.media.Image] objects, and multiple such objects can be accessed
                 * at the same time, up to the number specified by the {maxImages} constructor parameter.
                 * New images sent to an ImageReader through its [android.view.Surface] are queued until accessed
                 * through the [android.media.ImageReader.acquireLatestImage] or
                 * [android.media.ImageReader.acquireNextImage] call. Due to memory limits, an image source will
                 * eventually stall or drop Images in trying to render to the Surface if the ImageReader does not
                 * obtain and release Images at a rate equal to the production rate
                 */
                imageReader = ImageReader.newInstance( // Create a new reader for images of the desired size and format
                    largestOutputSize.width/DEVIDOR, largestOutputSize.height/DEVIDOR,
//                    ImageFormat.YUV_420_888, 50
                    ImageFormat.JPEG, MAX_IMAGES
                ).apply {
                    // Register a listener to be invoked when a new image becomes available from the ImageReader
                    setOnImageAvailableListener(onImageAvailableListener, backgroundHandler)
                }
                Log.d(TAG, ":setUpCameraOutputs:05")

                // Find out if we need to swap dimension to get the preview size relative to sensor coordinate
                // displayRotation will be the rotation of the screen from its "natural" orientation {0,90,180,270}
                val displayRotation = windowManager.defaultDisplay.rotation
                // sensor orientation is the physical rotation of the device’s camera sensor
                @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
                sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)
                val swappedDimensions = areDimensionsSwapped(displayRotation)

                val displaySize = Point() // Point holds two integer coordinates
                windowManager.defaultDisplay.getSize(displaySize) // Gets the size of the display, in pixels
                val rotatedPreviewWidth = if (swappedDimensions) height else width
                val rotatedPreviewHeight = if (swappedDimensions) width else height
                var maxPreviewWidth = if (swappedDimensions) displaySize.y else displaySize.x
                var maxPreviewHeight = if (swappedDimensions) displaySize.x else displaySize.y

                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) maxPreviewWidth = MAX_PREVIEW_WIDTH
                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) maxPreviewHeight = MAX_PREVIEW_HEIGHT

                /** Get a list of sizes compatible with the [SurfaceTexture] class to use as an output. */
                val compatibleSizesList = streamConfiguration.getOutputSizes(SurfaceTexture::class.java)
                Log.d(TAG, ":setUpCameraOutputs:06")

                // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
                // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
                // garbage capture data.
                previewSize = chooseOptimalSize(compatibleSizesList, rotatedPreviewWidth, rotatedPreviewHeight,
                    maxPreviewWidth, maxPreviewHeight, largestOutputSize)
                Log.d(TAG, ":setUpCameraOutputs:07")

                // We fit the aspect ratio of TextureView to the size of preview we picked.
                if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
                {
                    autoFitTextureView.setAspectRatio(previewSize.width, previewSize.height)
                }
                else
                {
                    autoFitTextureView.setAspectRatio(previewSize.height, previewSize.width)
                }

                // Check if the flash is supported.
                flashSupported = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true

                this.cameraId = cameraId

                // We've found a viable camera and finished setting up member variables,
                // so we don't need to iterate through other available cameras
                return true
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
            Toast.makeText(this, resources.getString(R.string.error), Toast.LENGTH_SHORT).show()
            Log.e(TAG, e.toString())
        }

        return false
    }

    /**
     * Determines if the dimensions are swapped given the phone's current rotation.
     *
     * @param displayRotation The current rotation of the display
     *
     * @return true if the dimensions are swapped, false otherwise.
     */
    private fun areDimensionsSwapped(displayRotation: Int): Boolean
    {
        Log.d(TAG, ":areDimensionsSwapped")
        var swappedDimensions = false
        when (displayRotation)
        {
            Surface.ROTATION_0, Surface.ROTATION_180 ->
            {
                if (sensorOrientation == 90 || sensorOrientation == 270) {
                    swappedDimensions = true
                }
            }
            Surface.ROTATION_90, Surface.ROTATION_270 ->
            {
                if (sensorOrientation == 0 || sensorOrientation == 180) {
                    swappedDimensions = true
                }
            }
            else ->
            {
                Log.e(TAG, "Display rotation is invalid: $displayRotation")
            }
        }
        return swappedDimensions
    }

    /**
     * Opens the camera specified by [cameraId].
     */
    private fun openCamera(width: Int, height: Int)
    {
        Log.d(TAG, ":openCamera")
        // ask permission for the camera
        val permission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        if (permission != PackageManager.PERMISSION_GRANTED)
        {
            requestCameraPermission()
            return
        }

        if (!setUpCameraOutputs(width, height)) return
        configureTransform(width, height)

        /** A system service manager for detecting, characterizing, and connecting to [CameraDevice] */
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try
        {
            // Wait for camera to open - 2.5 seconds is sufficient
            if (!cameraLock.tryAcquire(2500, TimeUnit.MILLISECONDS))
            {
                throw RuntimeException("Time out waiting to lock camera opening.")
            }
            // Open a connection to a camera with the given cameraId, a stateCallback which is invoked once the
            // camera is opened and a handler on which the callback should be invoked
            cameraManager.openCamera(cameraId, stateCallback, backgroundHandler)
        }
        catch (e: CameraAccessException)
        {
            Log.e(TAG, e.toString())
        }
        catch (e: InterruptedException)
        {
            throw RuntimeException("Interrupted while trying to lock camera opening.", e)
        }
    }

    /**
     * Closes the current [CameraDevice]
     */
    private fun closeCamera()
    {
        Log.d(TAG, ":closeCamera")
        try
        {
            cameraLock.acquire()
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
            imageReader?.close()
            imageReader = null
        }
        catch (e: InterruptedException)
        {
            throw RuntimeException("Interrupted while trying to lock camera closing.", e)
        }
        finally
        {
            cameraLock.release()
        }
    }

    /**
     * Starts a background thread and its [Handler]
     * Using the Looper we create a Pipeline Thread
     */
    private fun startBackgroundThread()
    {
        Log.d(TAG, ":startBackgroundThread")
        // starting a new thread that has a looper
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        // associate the looper with the handler
        backgroundHandler = Handler(backgroundThread?.looper)
    }

    /**
     * Stops the background thread and its [Handler].
     */
    private fun stopBackgroundThread()
    {
        Log.d(TAG, ":stopBackgroundThread")
        // Quits the handler thread's looper safely
        backgroundThread?.quitSafely()
        try
        {
            // Waits for this thread to die
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        }
        catch (e: InterruptedException)
        {
            Log.e(TAG, e.toString())
        }
    }

    /**
     * Creates a new [CameraCaptureSession] for camera preview.
     */
    private fun createCameraPreviewSession()
    {
        Log.d(TAG, ":createCameraPreviewSession")
        try
        {
            val surfaceTexture = autoFitTextureView.surfaceTexture

            /** We configure the default size of the image buffer to be the size of camera preview values
             * we got from [setUpCameraOutputs] */
            surfaceTexture.setDefaultBufferSize(previewSize.width, previewSize.height)

            /** Create Surface from a [SurfaceTexture], this is the output Surface we need to start preview */
            val surface = Surface(surfaceTexture)

            /** We set up a [CaptureRequest.Builder] with the output Surface for new capture requests */
            previewRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            // The surfaces are used as an output target for this request:
            previewRequestBuilder.addTarget(surface) // surface for preview
            previewRequestBuilder.addTarget(imageReader!!.surface) // surface for proccessing

            /**
             * Create a CameraCaptureSession for camera preview:
             * Create a new camera capture session by providing the target output set of Surfaces to the
             * camera device
             *
             * It can take several hundred milliseconds for the session's configuration to complete,
             * since camera hardware may need to be powered on or reconfigured. Once the configuration is
             * complete and the session is ready to actually capture data, the provided
             * [CameraCaptureSession.StateCallback.onConfigured] callback will be called
             */
            cameraDevice?.createCaptureSession(
                Arrays.asList(surface, imageReader?.surface),
                object : CameraCaptureSession.StateCallback()
                {
                    override fun onConfigured(cameraCaptureSession: CameraCaptureSession)
                    {
                        // The camera is already closed
                        if (cameraDevice == null) return

                        // When the session is ready, we start displaying the preview
                        captureSession = cameraCaptureSession
                        try
                        {
                            // Set the autofocus field to continuous
//                            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
//                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                            // Overall mode of 3A (auto-exposure, auto-white-balance, auto-focus) control routines
                            previewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                            previewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(MIN_FPS, MAX_FPS))
                            // Start displaying the camera preview:
                            // create the CaptureRequests by using a Builder instance
                            previewCaptureRequest = previewRequestBuilder.build()

                            /**
                             * Request endlessly repeating capture of images by this capture session.
                             *
                             * The camera device will continually capture images using the settings in the provided
                             * [CaptureRequest], at the maximum rate possible
                             *
                             * Repeating requests are a simple way for an application to maintain a preview or other
                             * continuous stream of frames, without having to continually submit identical requests
                             * through [android.hardware.camera2.CameraCaptureSession.capture]
                             */
                            captureSession?.setRepeatingRequest(previewCaptureRequest, captureCallback, backgroundHandler)
                        }
                        catch (e: CameraAccessException)
                        {
                            Log.e(TAG, e.toString())
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession)
                    {
                        Toast.makeText(applicationContext, resources.getString(R.string.error), Toast.LENGTH_SHORT).show()
                    }
                }, null
            )
        }
        catch (e: CameraAccessException)
        {
            Log.e(TAG, e.toString())
        }
    }

    /**
     * Configures the necessary [android.graphics.Matrix] transformation to [autoFitTextureView].
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of [autoFitTextureView] is fixed.
     *
     * @param viewWidth  The width of `textureView`
     * @param viewHeight The height of `textureView`
     */
    private fun configureTransform(viewWidth: Int, viewHeight: Int)
    {
        Log.d(TAG, ":configureTransform")
        val rotation = windowManager.defaultDisplay.rotation
        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(0f, 0f, previewSize.height.toFloat(), previewSize.width.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()

        if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270)
        {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            val scale = Math.max(viewHeight.toFloat() / previewSize.height,
                viewWidth.toFloat() / previewSize.width)
            with(matrix)
            {
                setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
                postScale(scale, scale, centerX, centerY)
                postRotate((90 * (rotation - 2)).toFloat(), centerX, centerY)
            }
        }
        else if (Surface.ROTATION_180 == rotation)
        {
            matrix.postRotate(180f, centerX, centerY)
        }
        autoFitTextureView.setTransform(matrix)
    }

    // Flash is automatically enabled when necessary
    private fun setAutoFlash(requestBuilder: CaptureRequest.Builder)
    {
        Log.d(TAG, ":setAutoFlash")
        if (flashSupported)
        {
            // set auto exposure mode: camera device controls the camera's flash unit, firing it in low-light conditions
            requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
        }
    }

    companion object
    {
        /** Tag for the [Log] */
        private val TAG = "gipsy:" + this::class.java.name
        private const val REQUEST_CAMERA_PERMISSION = 1
        private const val DEVIDOR = 50 //devidor for scaling down the surface size for better performance
        private const val NUMBER_OF_TOP = 5 // number of top colors to present the user
        private const val MAX_IMAGES = 50 // more allocation for better performance
        private const val MIN_FPS = 5
        private const val MAX_FPS = 8

        /** Conversion from screen rotation to JPEG orientation */
        private val ORIENTATIONS = SparseIntArray()

        init
        {
            ORIENTATIONS.append(Surface.ROTATION_0, 90)
            ORIENTATIONS.append(Surface.ROTATION_90, 0)
            ORIENTATIONS.append(Surface.ROTATION_180, 270)
            ORIENTATIONS.append(Surface.ROTATION_270, 180)
        }

        /**
         * Max preview width that is guaranteed by Camera2 API
         */
        private const val MAX_PREVIEW_WIDTH = 1920

        /**
         * Max preview height that is guaranteed by Camera2 API
         */
        private const val MAX_PREVIEW_HEIGHT = 1080

        /**
         * Given `choices` of `Size`s supported by a camera, choose the smallest one that
         * is at least as large as the respective texture view size, and that is at most as large as
         * the respective max size, and whose aspect ratio matches with the specified value. If such
         * size doesn't exist, choose the largest one that is at most as large as the respective max
         * size, and whose aspect ratio matches with the specified value.
         *
         * @JvmStatic Specifies that an additional static method needs to be generated from this element if it's
         * a function. If this element is a property, additional static getter/setter methods should be generated.
         *
         * @param choices           The list of sizes that the camera supports for the intended
         *                          output class
         * @param textureViewWidth  The width of the texture view relative to sensor coordinate
         * @param textureViewHeight The height of the texture view relative to sensor coordinate
         * @param maxWidth          The maximum width that can be chosen
         * @param maxHeight         The maximum height that can be chosen
         * @param aspectRatio       The aspect ratio
         * @return The optimal `Size`, or an arbitrary one if none were big enough
         */
        @JvmStatic
        private fun chooseOptimalSize(choices: Array<Size>, textureViewWidth: Int, textureViewHeight: Int,
            maxWidth: Int, maxHeight: Int, aspectRatio: Size): Size
        {
            Log.d(TAG, ":chooseOptimalSize")
            // Collect the supported resolutions that are at least as big as the preview Surface
            val bigEnough = ArrayList<Size>()
            // Collect the supported resolutions that are smaller than the preview Surface
            val notBigEnough = ArrayList<Size>()
            val w = aspectRatio.width
            val h = aspectRatio.height
            for (option in choices)
            {
                if (option.width <= maxWidth && option.height <= maxHeight && option.height == option.width * h / w)
                {
                    if (option.width >= textureViewWidth && option.height >= textureViewHeight) {
                        bigEnough.add(option)
                    } else {
                        notBigEnough.add(option)
                    }
                }
            }

            // Pick the smallest of those big enough. If big enough found, pick the largest of those not big enough
            return when
            {
                bigEnough.size > 0 -> Collections.min(bigEnough, CompareSizesByArea())
                notBigEnough.size > 0 -> Collections.max(notBigEnough, CompareSizesByArea())
                else ->
                {
                    Log.e(TAG, "Couldn't find any suitable preview size")
                    choices[0] // an arbitrary choice
                }
            }
        }
    }

    internal class CompareSizesByArea : Comparator<Size>
    {
        // The return value is -1 if the specified value is negative; 0 if the specified value is zero;
        // and 1 if the specified value is positive
        override fun compare(lhs: Size, rhs: Size ) : Int
        {
            Log.d(TAG, ":compare")
            // We cast here to ensure the multiplications won't overflow
            return signum(lhs.width.toLong() * lhs.height - rhs.width.toLong() * rhs.height)
        }
    }

    private fun jpegToBitmap(image: Image)
    {
        Log.d(TAG, ":jpegToBitmap: jpeg values: width = ${image.width},  height = ${image.height} " +
               "number of pixels = ${image.width*image.height}")
        val buffer = image.planes[0].buffer
        val byteArray = ByteArray(buffer.remaining()) //create byte array
        buffer.get(byteArray) // transfers bytes from the plane buffer into the ByteArray

        val bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)

        val rgbMap = hashMapOf<Int, Int>()
        for (ii in 0 until bitmap.width)
        {
            for (jj in 0 until bitmap.height)
            {
                val pixel : Int = bitmap.getPixel(ii, jj)
                //using elvis operator: if index return null, use 0
                val count : Int = rgbMap[pixel] ?: 0
                rgbMap[pixel] = count + 1
            }
        }

        /*
        for ((key, value) in rgbMap)
        {
           Log.d(TAG, ":jpegToBitmap: rgbMap.key = ${key}, rgbMap.value = ${value}")
        }
        */
        // sort map by value
        val rgbMapSorted = rgbMap.toList().sortedByDescending { (_, value) -> value}.toMap()

        val iterator = rgbMapSorted.entries.iterator()
        val topColorsPairList : MutableList<Triple<Int, Int, Float>> = ArrayList()

        /** get [NUMBER_OF_TOP] most abundant colors */
        for (i in 1..NUMBER_OF_TOP)
        {
            if (iterator.hasNext())
            {
                val next = iterator.next()
                val percentage : Float = (100*next.value.toFloat()/(image.width.toFloat()*image.height.toFloat()))
//                val percentage : Int = (100*next.value/(bitmap.width*bitmap.height))
                topColorsPairList.add(Triple(next.key, next.value, percentage))
            }
            else // in case there's only one dominant color
            {
                topColorsPairList.add(Triple(0,0,0.toFloat()))
            }
        }

        this@MainActivity.runOnUiThread{
                Log.i(TAG,"runOnUiThread: topColorsPairList.size = ${topColorsPairList.size}")
                topColor1TextView.text = (topColorsPairList[0].third.toInt().toString()+"%")
                topColor1TextView.setBackgroundColor(topColorsPairList[0].first)
                val color = topColorsPairList[0].first
                topColor1TextView.setTextColor(
                        (color.inv() and 0x0000ff) + (color.inv() and 0x00ff00) + (color.inv() and 0xff0000))

                topColor2TextView.text = (topColorsPairList[1].third.toInt().toString()+"%")
                topColor2TextView.setBackgroundColor(topColorsPairList[1].first)
                topColor3TextView.text = (topColorsPairList[2].third.toInt().toString()+"%")
                topColor3TextView.setBackgroundColor(topColorsPairList[2].first)
                topColor4TextView.text = (topColorsPairList[3].third.toInt().toString()+"%")
                topColor4TextView.setBackgroundColor(topColorsPairList[3].first)
                topColor5TextView.text = (topColorsPairList[4].third.toInt().toString()+"%")
                topColor5TextView.setBackgroundColor(topColorsPairList[4].first)
        }

/*
            for (pair in topColorsPairList)
            {
               Log.d(TAG, ":jpegToBitmap: Key = ${pair.first}, Value = ${pair.second}, Percentage = ${pair.third}")
            }
*/
    }

    fun YuvToBitmap(image: Image)
    {
        Log.d(TAG, ":YuvToBitmap")
        val buffer = image.planes[0].buffer
        val numberOfPixels = buffer.remaining()
        val byteArray = ByteArray(numberOfPixels) //create byte array
        buffer.get(byteArray) // transfers bytes from the plane buffer into the ByteArray

        val rs = RenderScript.create(this)
        val yuvToRgbIntrinsic = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs))

        val yuvType = Type.Builder(rs, Element.U8(rs)).setX(byteArray.size)
        val inData = Allocation.createTyped(rs, yuvType.create(), Allocation.USAGE_SCRIPT)

        val rgbaType = Type.Builder(rs, Element.RGBA_8888(rs)).setX(image.width).setY(image.height)
        val outData = Allocation.createTyped(rs, rgbaType.create(), Allocation.USAGE_SCRIPT)

        inData.copyFrom(byteArray)

        yuvToRgbIntrinsic.setInput(inData)
        yuvToRgbIntrinsic.forEach(outData)

        val bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        outData.copyTo(bitmap)
    }

}
