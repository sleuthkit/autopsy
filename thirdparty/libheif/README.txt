This project relies heavily on libheif: https://github.com/strukturag/libheif/ and specifically, the example here: https://github.com/strukturag/libheif/blob/master/examples/heif_convert.cc. 

In order to build this, the project requires vcpkg (directions here: https://vcpkg.io/en/getting-started.html), cmake (https://cmake.org/download/), and visual studio build tools for cmake (I downloaded visual studio 17 2022).  This project will require the following vcpkg dependencies:

libde265:x64-windows
libheif:x64-windows (files in project were grabbed from the libheif examples folder as of v1.12.0)
libjpeg-turbo:x64-windows
x265:x64-windows

or something other than the suffix :x64-windows for different architectures. 

In order to build, 
1) from command line, set directory to HeifConvertJNI\dist
2) You can rebuild the vcxproj in this directory by running:  cmake -G "Visual Studio 17 2022" -A x64 -S .. "-DCMAKE_TOOLCHAIN_FILE=PATH_TO_VCPKG_INSTALL/scripts/buildsystems/vcpkg.cmake"
3) The binaries can be created by running: cmake --build . --config Release

* The "-A x64" flag can be substituted with relevant architecture.