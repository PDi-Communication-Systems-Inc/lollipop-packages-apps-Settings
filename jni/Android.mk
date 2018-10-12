LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := libinternalspeakers_jni
LOCAL_SRC_FILES := internalspeakers.c

LOCAL_C_INCLUDES += \
    external/libusb-1.0.20/libusb


LOCAL_C_INCLUDES += \
    $(JNI_H_INCLUDE) \

#LOCAL_NDK_VERSION := 18
LOCAL_LDLIBS := -llog
LOCAL_SHARED_LIBRARIES += liblog \
                          libusb1.0    

include $(BUILD_SHARED_LIBRARY)

