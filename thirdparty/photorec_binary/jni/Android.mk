LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

TARGET_PLATFORM := android-8

testdisk_TOP := $(abspath $(LOCAL_PATH))/..
NDK_PROJECT_PATH = $(testdisk_TOP)

CONFIGURE_CC := $(TARGET_CC)
CONFIGURE_INCLUDES := -I$(TOPMOST)/include
CONFIGURE_LDFLAGS := -lc -ldl

CONFIGURE_CFLAGS := \
    -nostdlib -Bdynamic \
    -Wl,-dynamic-linker,/system/bin/linker \
    -Wl,--gc-sections \
    -Wl,-z,nocopyreloc \
    $(call host-path,\
        $(TARGET_CRTBEGIN_DYNAMIC_O) \
        $(PRIVATE_OBJECTS)) \
    $(call link-whole-archives,$(PRIVATE_WHOLE_STATIC_LIBRARIES))\
    $(call host-path,\
        $(PRIVATE_STATIC_LIBRARIES) \
        $(TARGET_LIBGCC) \
        $(PRIVATE_SHARED_LIBRARIES)) \
    $(PRIVATE_LDFLAGS) \
    $(PRIVATE_LDLIBS) \
    $(call host-path,\
        $(TARGET_CRTEND_O)) \
	$(CONFIGURE_INCLUDES)
CONFIGURE_LDFLAGS += -L$(SYSROOT)/usr/lib -L$(TARGET_OUT)
CONFIGURE_INCLUDES += -I$(SYSROOT)/usr/include
CONFIGURE_CPP := $(TOOLCHAIN_PREFIX)cpp
LIB := $(SYSROOT)/usr/lib

CONFIGURE_CPPFLAGS := \
	$(CONFIGURE_INCLUDES)

CONFIGURE := configure

#.SECONDARYEXPANSION:
#CONFIGURE_TARGETS :=

TD_BUILT_MAKEFILES := $(testdisk_TOP)/src/Android.mk

.PHONY: testdisk-configure testdisk-configure-real
testdisk-configure-real:
	echo $(TD_BUILT_MAKEFILES)
	cd $(testdisk_TOP) ; \
	CC="$(CONFIGURE_CC)" \
	CFLAGS="$(CONFIGURE_CFLAGS)" \
	LD=$(TARGET_LD) \
	LDFLAGS="$(CONFIGURE_LDFLAGS)" \
	CPP=$(CONFIGURE_CPP) \
	CPPFLAGS="$(CONFIGURE_CPPFLAGS)" \
	PKG_CONFIG_LIBDIR=$(CONFIGURE_PKG_CONFIG_LIBDIR) \
	PKG_CONFIG_TOP_BUILD_DIR=/ \
	$(abspath $(testdisk_TOP))/configure --host=arm-linux-androideabi \
	--prefix=/system \
	--libexec /system/bin \
	--datarootdir /system/usr/share \
	--without-ncurses --without-ext2fs --without-jpeg \
	--without-ntfs --without-ntfs3g --without-ewf \
	--enable-missing-uuid-ok
	rm -f $(TD_BUILT_MAKEFILES)
	for file in $(TD_BUILT_MAKEFILES); do \
		echo "make -C $$(dirname $$file) $$(basename $$file)" ; \
		make -C $$(dirname $$file) $$(basename $$file) ; \
	done

testdisk-configure: testdisk-configure-real

CONFIGURE_TARGETS += testdisk-configure

-include $(TD_BUILT_MAKEFILES)

run:
	adb push libs/armeabi/photorec /data/local/bin/photorec
	adb push libs/armeabi/testdisk /data/local/bin/testdisk
	adb shell chmod 755 /data/local/bin/photorec /data/local/bin/testdisk
	adb shell /data/local/bin/testdisk -lu
	adb shell /data/local/bin/photorec

