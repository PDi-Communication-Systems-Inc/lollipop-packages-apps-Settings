#include <jni.h>
#include<stdio.h>
#include<stdlib.h>
#include<fcntl.h>
#include<sys/types.h> 
#include <libusb.h>
#include "InternalSpeakersJNI.h"

/*
 * Make sure to add package name prefixed after java. i.e com.android.setting
   java_<package name with _ instead of . >_<JavaFileName>_<functionname>

 */
char mfcName[] = "PDi Communication Systems.";

JNIEXPORT  void JNICALL Java_com_android_settings_notification_InternalSpeakersJNI_toggleInternalSpeakers
  (JNIEnv * env, jclass SoundSettings, jboolean val) {

    FILE *fp;
    char gpio_path[30];
    size_t ret = 0;
    strcpy(gpio_path, "/sys/class/gpio/gpio39/value");
    if ((fp = fopen(gpio_path, "rb+")) == NULL) /*file in binary for reading and writing*/
	return ;
		
    rewind(fp);	/*Set pointer to begining of the file*/
    if(val) {
	ret = fwrite("1", sizeof(char), 1, fp); /*Write "1" to the file*/
    } else {
	ret = fwrite("0", sizeof(char), 1, fp); /*Write "0" to the file*/
    }
    
    fclose(fp);
}


static char* getFirmwareVersion(libusb_device **devs)
{
    libusb_device *dev;
    int i = 0;
    char msg[256];

    while ((dev = devs[i++]) != NULL) {
        int ret;
        char string[256];
        struct libusb_device_descriptor desc;
        libusb_device_handle *udev = NULL;

        int r = libusb_get_device_descriptor(dev, &desc);
        if (r < 0) {
            LOGV("failed to get device descriptor");
            continue;
        } else {
            LOGV(" %04x:%04x (bus %d, device %d)\n",
                desc.idVendor, desc.idProduct,
                libusb_get_bus_number(dev), libusb_get_device_address(dev));
        }

        r = libusb_open(dev, &udev);
        if (r < 0) {
            LOGV("failed to open usb device");
            continue;
        }

        if (desc.iManufacturer) {
            ret = libusb_get_string_descriptor_ascii(udev,
                                        desc.iManufacturer,
                                        string,
                                        sizeof(string));
            if (ret > 0) {
                if ((strcmp(mfcName, string) == 0) && desc.iProduct) {
                    ret = libusb_get_string_descriptor_ascii(udev,
                                              desc.iProduct,
                                              string,
                                              sizeof(string));
                    if (ret > 0) {
                        memset(msg, '\0', sizeof(msg));
                        strcpy(msg, string);
                    } else {
                        memset(msg, '\0', sizeof(msg));
                        strcpy(msg, "Unavailable");
                    }
                 }
            } else {
                LOGV("­Unable to fetch manufacturer string\n");
                memset(msg, '\0', sizeof(msg));
                strcpy(msg, "Unavailable");
            }
        }

    }
    return msg;
}
/*
 * Gets the Firmware version of the pilow speakers from the usb connected devices.
   -Uses libusb to retireve the verion
**/
JNIEXPORT jstring JNICALL Java_com_android_settings_notification_InternalSpeakersJNI_returnFirmwareVersion 
  (JNIEnv *env, jclass SoundSettings) {
        libusb_device **devs;
        char *msg;
	int r;
	ssize_t cnt;
	r = libusb_init(NULL);
	if (r < 0)
		return "Unavailable";
	cnt = libusb_get_device_list(NULL, &devs);
	if (cnt < 0)
		return "Unavailable";
	msg = getFirmwareVersion(devs);
	libusb_free_device_list(devs, 1);
	libusb_exit(NULL);
return  (*env)->NewStringUTF(env, msg);

}
