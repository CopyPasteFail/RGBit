# RGBit
This app will show you the top 5 most common colors in a frame while seeing a preview

## Installation
Clone this repository and import into **Android Studio**
```bash
git clone https://github.com/GipsyBeggar/RGBit.git
```

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

## How It Works
Configurs the camera to output JPEG, a background thread will convert it to Bitmap, and iterate over the pixels to count colors, using HashMap to count.

## Performance
There are many improvements that can be done to improve performance, one option would probably be using YUV as output becuase it saves the time for compressing to JPEG.
In order to improve performance
