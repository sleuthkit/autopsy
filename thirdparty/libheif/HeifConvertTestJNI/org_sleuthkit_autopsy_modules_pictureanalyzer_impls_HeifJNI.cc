/**
  This code is based almost entirely on https://github.com/strukturag/libheif/blob/master/examples/heif_convert.cc with small changes for JNI.

  libheif example application "convert".
  MIT License
  Copyright (c) 2017 struktur AG, Joachim Bauch <bauch@struktur.de>

  Permission is hereby granted, free of charge, to any person obtaining a copy
  of this software and associated documentation files (the "Software"), to deal
  in the Software without restriction, including without limitation the rights
  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
  copies of the Software, and to permit persons to whom the Software is
  furnished to do so, subject to the following conditions:
  The above copyright notice and this permission notice shall be included in all
  copies or substantial portions of the Software.
  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
  SOFTWARE.
**/

#include <jni.h>

#ifndef _Included_org_sleuthkit_autopsy_modules_pictureanalyzer_impls_HeifJNI
#define _Included_org_sleuthkit_autopsy_modules_pictureanalyzer_impls_HeifJNI

//#if defined(HAVE_CONFIG_H)
//#include "config.h"
//#endif
//
//#include <cstring>
//
//#if defined(HAVE_UNISTD_H)
//
//#include <unistd.h>
//
//#endif

#include <fstream>
#include <iostream>
#include <sstream>
#include <cassert>
#include <algorithm>
#include <vector>
#include <cctype>

#include <libheif/heif.h>

#include "encoder.h"
#include "encoder_jpeg.h"

#define UNUSED(x) (void)x

#endif

class ContextReleaser
{
public:
    ContextReleaser(struct heif_context* ctx) : ctx_(ctx)
    {}

    ~ContextReleaser()
    {
        heif_context_free(ctx_);
    }

private:
    struct heif_context* ctx_;
};

jint throwException(JNIEnv* env, const char* exceptionType, const char* message)
{
    jclass exClass;
    exClass = env->FindClass(exceptionType);
    return env->ThrowNew(exClass, message);
}


jint throwIllegalArgument(JNIEnv* env, const char* message)
{
    std::string className = "java/lang/IllegalArgumentException";
    return throwException(env, className.c_str(), message);
}

jint throwIllegalState(JNIEnv* env, const char* message)
{
    std::string className = "java/lang/IllegalStateException";
    return throwException(env, className.c_str(), message);
}

//void println(JNIEnv* env, std::string message)
//{
//    // Get system class
//    jclass syscls = env->FindClass("java/lang/System");
//    // Lookup the "out" field
//    jfieldID fid = env->GetStaticFieldID(syscls, "out", "Ljava/io/PrintStream;");
//    jobject out = env->GetStaticObjectField(syscls, fid);
//    // Get PrintStream class
//    jclass pscls = env->FindClass("java/io/PrintStream");
//    // Lookup printLn(String)
//    jmethodID mid = env->GetMethodID(pscls, "println", "(Ljava/lang/String;)V");
//    // Invoke the method
//    jchar* cppstr = (jchar*)message.c_str(); // make an array of jchar (UTF-16 unsigned short encoding)
//    jstring str = env->NewString(cppstr, message.length());
//    env->CallVoidMethod(out, mid, str);
//}


int convertToDisk
	(JNIEnv* env, jclass cls, jbyteArray byteArr, jstring outputPath) {

    size_t arrLen = env->GetArrayLength(byteArr);
    std::vector<jbyte> nativeByteArr(arrLen);

    boolean isCopy;
    env->GetByteArrayRegion(byteArr, 0, arrLen, &nativeByteArr[0]);
    std::string output_filename = env->GetStringUTFChars(outputPath, 0);
    const int quality = 100;

    printf("Checking heif file type...\n");
    enum heif_filetype_result filetype_check = heif_check_filetype((const uint8_t*)&nativeByteArr[0], 12);
    if (filetype_check == heif_filetype_no) {
        // env->ReleaseByteArrayElements(byteArr, nativeByteArr, 0);
        throwIllegalArgument(env, "Input file is not an HEIF/AVIF file");
        return 1;
    }

    printf("Checking heif file type supported...\n");
    if (filetype_check == heif_filetype_yes_unsupported) {
        // env->ReleaseByteArrayElements(byteArr, nativeByteArr, 0);
        throwIllegalArgument(env, "Input file is an unsupported HEIF/AVIF file type");
        return 1;
    }

    printf("Creating heif context...\n");
    struct heif_context* ctx = heif_context_alloc();
    if (!ctx) {
        // env->ReleaseByteArrayElements(byteArr, nativeByteArr, 0);
        throwIllegalState(env, "Could not create context object");
        return 1;
    }

    printf("Reading in heif bytes...\n");
    ContextReleaser cr(ctx);
    struct heif_error err;
    err = heif_context_read_from_memory_without_copy(ctx, (void*)&nativeByteArr[0], arrLen, nullptr);
    if (err.code != 0) {
        // env->ReleaseByteArrayElements(byteArr, nativeByteArr, 0);
        std::string err_message = "Could not read HEIF/AVIF file:";
        err_message += err.message;
        throwIllegalState(env, err_message.c_str());
        return 1;
    }

    printf("Checking heif file type...\n");
    int num_images = heif_context_get_number_of_top_level_images(ctx);
    if (num_images == 0) {
        // env->ReleaseByteArrayElements(byteArr, nativeByteArr, 0);
        throwIllegalState(env, "File doesn't contain any images");
        return 1;
    }

    printf("File contains %d images.  Reading in image ids...\n", num_images);

    std::vector<heif_item_id> image_IDs(num_images);
    num_images = heif_context_get_list_of_top_level_image_IDs(ctx, image_IDs.data(), num_images);

    printf("Resetting encoder...\n");
    std::string filename;
    std::unique_ptr<Encoder> encoder(new JpegEncoder(quality));

    size_t image_index = 1;  // Image filenames are "1" based.

    for (int idx = 0; idx < num_images; ++idx) {
        printf("Looping through for image %d\n", idx);

        if (num_images > 1) {
            std::ostringstream s;
            s << output_filename.substr(0, output_filename.find_last_of('.'));
            s << "-" << image_index;
            s << output_filename.substr(output_filename.find_last_of('.'));
            filename.assign(s.str());
            printf("Assigning filename of %s\n", s.str().c_str());
        }
        else {
            filename.assign(output_filename);
            printf("Assigning filename of %s\n", output_filename.c_str());
        }

        printf("acquiring heif image handle...\n");
        struct heif_image_handle* handle;
        err = heif_context_get_image_handle(ctx, image_IDs[idx], &handle);
        if (err.code) {
            // env->ReleaseByteArrayElements(byteArr, nativeByteArr, 0);
            std::string err_message = "Could not read HEIF/AVIF image ";
            err_message += idx;
            err_message += ": ";
            err_message += err.message;
            throwIllegalState(env, err_message.c_str());
            return 1;
        }

        printf("handling alpha...\n");
        int has_alpha = heif_image_handle_has_alpha_channel(handle);
        struct heif_decoding_options* decode_options = heif_decoding_options_alloc();
        encoder->UpdateDecodingOptions(handle, decode_options);

        int bit_depth = heif_image_handle_get_luma_bits_per_pixel(handle);
        if (bit_depth < 0) {
            heif_decoding_options_free(decode_options);
            heif_image_handle_release(handle);
            // env->ReleaseByteArrayElements(byteArr, nativeByteArr, 0);
            throwIllegalState(env, "Input image has undefined bit-depth");
            return 1;
        }

        printf("decoding heif image...\n");
        struct heif_image* image;
        err = heif_decode_image(handle,
            &image,
            encoder->colorspace(has_alpha),
            encoder->chroma(has_alpha, bit_depth),
            decode_options);
        heif_decoding_options_free(decode_options);
        if (err.code) {
            heif_image_handle_release(handle);
            std::string err_message = "Could not decode image: ";
            err_message += idx;
            err_message += ": ";
            err_message += err.message;
            // env->ReleaseByteArrayElements(byteArr, nativeByteArr, 0);
            throwIllegalState(env, err_message.c_str());
            return 1;
        }


        if (image) {
            printf("valid image found.\n");
            bool written = encoder->Encode(handle, image, filename);
            if (!written) {
                printf("could not write image\n");
            }            
            else {
                printf("Written to %s\n", filename.c_str());
            }
            heif_image_release(image);


            int has_depth = heif_image_handle_has_depth_image(handle);
            if (has_depth) {
                printf("has depth...\n");
                heif_item_id depth_id;
                int nDepthImages = heif_image_handle_get_list_of_depth_image_IDs(handle, &depth_id, 1);
                assert(nDepthImages == 1);
                (void)nDepthImages;

                struct heif_image_handle* depth_handle;
                err = heif_image_handle_get_depth_image_handle(handle, depth_id, &depth_handle);
                if (err.code) {
                    heif_image_handle_release(handle);
                    // env->ReleaseByteArrayElements(byteArr, nativeByteArr, 0);
                    throwIllegalState(env, "Could not read depth channel");
                    return 1;
                }

                int depth_bit_depth = heif_image_handle_get_luma_bits_per_pixel(depth_handle);

                printf("decoding depth image...\n");
                struct heif_image* depth_image;
                err = heif_decode_image(depth_handle,
                    &depth_image,
                    encoder->colorspace(false),
                    encoder->chroma(false, depth_bit_depth),
                    nullptr);
                if (err.code) {
                    heif_image_handle_release(depth_handle);
                    heif_image_handle_release(handle);
                    // env->ReleaseByteArrayElements(byteArr, nativeByteArr, 0);
                    std::string err_message = "Could not decode depth image: ";
                    err_message += err.message;
                    throwIllegalState(env, err_message.c_str());
                    return 1;
                }

                std::ostringstream s;
                s << output_filename.substr(0, output_filename.find('.'));
                s << "-depth";
                s << output_filename.substr(output_filename.find('.'));

                printf("Encoding to %s.\n", s.str().c_str());
                written = encoder->Encode(depth_handle, depth_image, s.str());
                if (!written) {
                    printf("could not write depth image\n");
                }
                else {
                    printf("Depth image written to %s\n", s.str().c_str());
                }

                heif_image_release(depth_image);
                heif_image_handle_release(depth_handle);
            }


            printf("checking for aux images...\n");
            
            // --- aux images

            int nAuxImages = heif_image_handle_get_number_of_auxiliary_images(handle, LIBHEIF_AUX_IMAGE_FILTER_OMIT_ALPHA | LIBHEIF_AUX_IMAGE_FILTER_OMIT_DEPTH);

            if (nAuxImages > 0) {
                printf("found %d aux images.\n", nAuxImages);

                std::vector<heif_item_id> auxIDs(nAuxImages);
                heif_image_handle_get_list_of_auxiliary_image_IDs(handle,
                    LIBHEIF_AUX_IMAGE_FILTER_OMIT_ALPHA | LIBHEIF_AUX_IMAGE_FILTER_OMIT_DEPTH,
                    auxIDs.data(), nAuxImages);

                for (heif_item_id auxId : auxIDs) {
                    printf("getting aux handle...\n");

                    struct heif_image_handle* aux_handle;
                    err = heif_image_handle_get_auxiliary_image_handle(handle, auxId, &aux_handle);
                    if (err.code) {
                        heif_image_handle_release(handle);
                        // env->ReleaseByteArrayElements(byteArr, nativeByteArr, 0);
                        throwIllegalState(env, "Could not read auxiliary image");
                        return 1;
                    }

                    printf("decoding aux handle image...\n");
                    int aux_bit_depth = heif_image_handle_get_luma_bits_per_pixel(aux_handle);

                    struct heif_image* aux_image;
                    err = heif_decode_image(aux_handle,
                        &aux_image,
                        encoder->colorspace(false),
                        encoder->chroma(false, aux_bit_depth),
                        nullptr);
                    if (err.code) {
                        heif_image_handle_release(aux_handle);
                        heif_image_handle_release(handle);
                        // env->ReleaseByteArrayElements(byteArr, nativeByteArr, 0);
                        std::string err_message = "Could not decode auxiliary image: ";
                        err_message += err.message;
                        throwIllegalState(env, err_message.c_str());
                        return 1;
                    }

                    printf("decoding aux image handle auxiliary type...\n");
                    const char* auxTypeC = nullptr;
                    err = heif_image_handle_get_auxiliary_type(aux_handle, &auxTypeC);
                    if (err.code) {
                        heif_image_handle_release(aux_handle);
                        heif_image_handle_release(handle);
                        // env->ReleaseByteArrayElements(byteArr, nativeByteArr, 0);
                        std::string err_message = "Could not get type of auxiliary image: ";
                        err_message += err.message;
                        throwIllegalState(env, err_message.c_str());
                        return 1;
                    }

                    std::string auxType = std::string(auxTypeC);

                    printf("freeing auxiliary type.\n");
                    heif_image_handle_free_auxiliary_types(aux_handle, &auxTypeC);

                    std::ostringstream s;
                    s << output_filename.substr(0, output_filename.find('.'));
                    s << "-" + auxType;
                    s << output_filename.substr(output_filename.find('.'));
                    throwIllegalArgument(env, s.str().c_str());

                    printf("Writing aux to output: %s\n", s.str().c_str());

                    written = encoder->Encode(aux_handle, aux_image, s.str());
                    if (!written) {
                        printf("could not write auxiliary image\n");
                    }
                    else {
                        printf("Auxiliary image written to %s\n", s.str().c_str());
                    }

                    heif_image_release(aux_image);
                    heif_image_handle_release(aux_handle);
                }
            }


            heif_image_handle_release(handle);
        }

        image_index++;
    }

    // env->ReleaseByteArrayElements(byteArr, nativeByteArr, 0);
    return 0;
}


extern "C" {
    /*
     * Class:     org_sleuthkit_autopsy_modules_pictureanalyzer_impls_HeifJNI
     * Method:    convertToDisk
     * Signature: ([BLjava/lang/String;)V
     */
    JNIEXPORT int JNICALL Java_org_sleuthkit_autopsy_modules_pictureanalyzer_impls_HeifJNI_convertToDisk
    (JNIEnv* env, jclass cls, jbyteArray byteArr, jstring outputPath) {
        return convertToDisk(env, cls, byteArr, outputPath);
    }
}
