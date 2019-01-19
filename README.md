# RGBit
This app will show you the top 5 most common colors in a frame while seeing a preview


## Table of content
- [How It Works](#How-It-Works)
- [Installation](#Installation)
- [Performance](#license)
- [Maintainers](#Maintainers)
- [Contributing](#Contributing)
- [License](#license)
- [Links](#links)


## How It Works
Configurs the camera to output JPEG, a background thread will convert it to Bitmap, and iterate over the pixels to count colors, using HashMap to count.


## Installation
Clone this repository and import into **Android Studio**
```bash
git clone https://github.com/GipsyBeggar/RGBit.git
```


## Performance
There are many improvements that can be done to improve performance, one option would probably be using YUV as output becuase it saves the time for compressing to JPEG.

In order to improve performance, a number of `consts` can be tweaked:
- DEVIDOR - devidor for scaling down the surface (picture) size for better performance
- MAX_IMAGES - more memory allocation for better performance
- MIN_FPS = 5 - min FPS
- MAX_FPS = 8 - max FPS of the camera, less FPS will improve performance


## Maintainers
This project is mantained by:
* [Omer Reznik](http://github.com/GipsyBeggar)


## Contributing
1. Fork it
2. Create your feature branch (git checkout -b my-new-feature)
3. Commit your changes (git commit -m 'Add some feature')
4. Push your branch (git push origin my-new-feature)
5. Create a new Pull Request


## License
This project is licensed under the GNU Affero General Public License v3.0 - see the [LICENSE.md](LICENSE.md) file for details


## Links
The following articles and documentations have been used:
### Tutorials
- https://www.youtube.com/watch?v=oPu42I0HSi4&t=141s
- https://www.youtube.com/watch?v=KhqGphh6KPE&list=PL1zUzvaWABxkMIeJCpQQzH9MafC-oxtL0&index=5
- https://android.jlelse.eu/the-least-you-can-do-with-camera2-api-2971c8c81b8b
- https://inducesmile.com/android/android-camera2-api-example-tutorial/
- https://proandroiddev.com/understanding-camera2-api-from-callbacks-part-1-5d348de65950
### Examples
- https://github.com/googlesamples/android-Camera2Basic
- https://github.com/googlesamples/android-Camera2Raw
- https://github.com/mitchtabian/TabianCustomCamera
### Documentation
- https://source.android.com/devices/camera/index.html
- https://developer.android.com/reference/android/hardware/camera2/package-summary
- https://pierrchen.blogspot.com/2015/01/android-camera2-api-explained.html
- https://developer.android.com/reference/android/hardware/camera2/TotalCaptureResult
- https://developer.android.com/reference/android/hardware/camera2/CaptureResult
- https://developer.android.com/reference/android/graphics/Bitmap.Config
- https://developer.android.com/reference/android/graphics/ImageFormat.html
- https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics#SCALER_STREAM_CONFIGURATION_MAP
- https://developer.android.com/reference/kotlin/android/renderscript/ScriptIntrinsicYuvToRGB
- https://www.impulseadventure.com/photo/jpeg-color-space.html
### RGB values and frame data Q&A
- https://stackoverflow.com/questions/34972415/finding-rgb-values-of-bitmap-from-camera2-api-in-android?rq=1
- https://stackoverflow.com/questions/35962068/how-to-get-rgb-values-from-byte-array-in-android-camera2-when-imageformat-is-j
- https://stackoverflow.com/questions/28430024/convert-android-media-image-yuv-420-888-to-bitmap
- https://stackoverflow.com/questions/25462277/camera-preview-image-data-processing-with-android-l-and-camera2-api
- https://stackoverflow.com/questions/32063524/how-to-get-each-frame-data-using-camera2-api-in-android5-0-in-realtime?noredirect=1&lq=1
- http://werner-dittmann.blogspot.com/
- https://stackoverflow.com/questions/43087321/get-rgb-of-jpg-in-android
- https://stackoverflow.com/questions/40885602/yuv-420-888-to-rgb-conversion
- https://gist.github.com/kimkidong/3346007
- http://blog.tomgibara.com/post/132956174/yuv420-to-rgb565-conversion-in-android
- https://stackoverflow.com/questions/34972415/finding-rgb-values-of-bitmap-from-camera2-api-in-android?rq=1
- https://stackoverflow.com/questions/9325861/converting-yuv-rgbimage-processing-yuv-during-onpreviewframe-in-android
