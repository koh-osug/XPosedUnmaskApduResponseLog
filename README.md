# Summary

Unmask the eUICC APDU response payloads masked in the radio log in Android Pie and later.

Usually the payload should be masked (`logcat -b radio *:V`):

    09-14 16:17:51.294  2111  2111 V TransApdu: Send: ApduCommand(channel=1, cla=128, ins=226, p1=145, p2=0, p3=3, cmd=BF2000)
    09-14 16:17:51.315  2111  2111 V TransApdu: Response: IccIoResult sw1:0x61 sw2:0x64 Payload: ******* Error: unknown

After the module is installed an additional an additional line is printed:

    09-14 16:17:51.324  2111  2111 V UnmaskApduResponseLog: IccIoResult Payload: bf20....
    
# Install

* Compile it using Android Studio and deploy it.
* Enabled it in EdXposed Manager
* Reboot

__NOTE:__ After an update of the module the module must be disabled an re-enabled and the device rebooted.

# Inspect Log

~~~
adb shell
logcat -b radio UnmaskApduResponseLog:V *:S
~~~
