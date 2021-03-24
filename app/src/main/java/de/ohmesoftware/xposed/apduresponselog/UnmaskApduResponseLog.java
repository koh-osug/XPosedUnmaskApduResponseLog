package de.ohmesoftware.xposed.apduresponselog;

import android.os.Message;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Unmask the APDU response payloads masked in the radio log in Android Pie.
 * <p>
 * Does not apply to a special app or package, but is used system wide.
 * </p>
 *
 * @author <a href="mailto:k_o_@sourceforge.net">Karsten Ohme
 * (karsten@simless.com)</a>
 */
public class UnmaskApduResponseLog implements IXposedHookLoadPackage {

    private static final String TAG = "UnmaskApduResponseLog";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        Log.d(TAG, String.format("Loaded: %s", lpparam.packageName));
// TODO:        this is just for the eUICC, remove if the next more general one is doing the job
        // https://cs.android.com/android/platform/superproject/+/master:frameworks/opt/telephony/src/java/com/android/internal/telephony/uicc/euicc/apdu/TransmitApduLogicalChannelInvocation.java;l=67?q=TransmitApduLogicalChannelInvocation&ss=android
        XposedHelpers.findAndHookMethod(
                "com.android.internal.telephony.uicc.euicc.apdu.TransmitApduLogicalChannelInvocation",
                lpparam.classLoader, "parseResult",
                Class.forName("android.os.AsyncResult"),
                new XC_MethodHook() {

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        //  first param is android.os.AsyncResult which result field is an
                        // IccIoResult
                        Field iccIoResultField = XposedHelpers.findField(param.args[0].getClass(), "result");
                        iccIoResultField.setAccessible(true);
                        Object iccIoResult = iccIoResultField.get(param.args[0]);
                        Field payloadField = XposedHelpers.findField(iccIoResult.getClass(), "payload");
                        payloadField.setAccessible(true);
                        byte[] data = (byte[]) payloadField.get(iccIoResult);
                        if (data != null && data.length > 0) {
                            Method verboseRlog = Class.forName("android.telephony.Rlog").getDeclaredMethod("v", String.class, String.class);
                            verboseRlog.invoke(null, TAG, String.format(
                                    "IccIoResult eUICC Payload: %s", byteArrayToHexString(data)));
                        }
                    }
                });
//        https://cs.android.com/android/platform/superproject/+/master:frameworks/opt/telephony/src/java/com/android/internal/telephony/uicc/IccIoResult.java;drc=master;l=187
        XposedHelpers.findAndHookMethod(
                "com.android.internal.telephony.uicc.IccIoResult",
                lpparam.classLoader, "toString",
                new XC_MethodHook() {

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Object iccIoResult = param.thisObject;
                        Field payloadField = XposedHelpers.findField(iccIoResult.getClass(), "payload");
                        payloadField.setAccessible(true);
                        byte[] data = (byte[]) payloadField.get(iccIoResult);
                        if (data != null && data.length > 0) {
                            Method verboseRlog = Class.forName("android.telephony.Rlog").getDeclaredMethod("v", String.class, String.class);
                            verboseRlog.invoke(null, TAG, String.format(
                                    "IccIoResult Payload: %s", byteArrayToHexString(data)));
                        }
                    }
                });
//        https://cs.android.com/android/platform/superproject/+/master:packages/services/Telephony/src/com/android/phone/PhoneInterfaceManager.java;drc=master;l=1536?q=SIM_IO&ss=android%2Fplatform%2Fsuperproject
        try {
            XposedHelpers.findAndHookMethod(
                    "com.android.phone.PhoneInterfaceManager",
                    lpparam.classLoader, "sendRequest",
                    int.class, Object.class, Integer.class,
                    Class.forName("com.android.internal.telephony.Phone"),
                    android.os.WorkSource.class,
                    new XC_MethodHook() {

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            if (param.args[1] != null && param.args[1].getClass().equals(Class.forName("com.android.phone.PhoneInterfaceManager$IccAPDUArgument"))) {
//                            public int channel, cla, command, p1, p2, p3;
//                            public String data;
                                String apduString = "ApduCommand(channel=" + XposedHelpers.getIntField(param.args[1], "channel")
                                        + ", cla=" + Integer.toHexString(XposedHelpers.getIntField(param.args[1], "cla"))
                                        + ", ins=" + Integer.toHexString(XposedHelpers.getIntField(param.args[1], "command"))
                                        + ", p1=" + Integer.toHexString(XposedHelpers.getIntField(param.args[1], "p1"))
                                        + ", p2=" + Integer.toHexString(XposedHelpers.getIntField(param.args[1], "p2"))
                                        + ", p3=" + Integer.toHexString(XposedHelpers.getIntField(param.args[1], "p3"))
                                        + ", cmd=" + XposedHelpers.getObjectField(param.args[1], "data") + ")";
                                Method verboseRlog = Class.forName("android.telephony.Rlog").getDeclaredMethod("v", String.class, String.class);
                                verboseRlog.invoke(null, TAG, String.format(
                                        "IccAPDUArgument unhidden APDU: %s", apduString));
                            }
                        }
                    });
        } catch (XposedHelpers.ClassNotFoundError e) {
            // ignore
        }

//        https://cs.android.com/android/platform/superproject/+/master:frameworks/opt/telephony/src/java/com/android/internal/telephony/uicc/UiccProfile.java;drc=master;l=1525
        XposedHelpers.findAndHookMethod(
                "com.android.internal.telephony.uicc.UiccProfile",
                lpparam.classLoader, "iccTransmitApduLogicalChannel",
                int.class,
                int.class,
                int.class,
                int.class,
                int.class,
                int.class,
                String.class,
                Message.class,
                new XC_MethodHook() {

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        String apduString = "ApduCommand(channel=" + param.args[0]
                                + ", cla=" + Integer.toHexString((Integer) param.args[1])
                                + ", ins=" + Integer.toHexString((Integer) param.args[2])
                                + ", p1=" + Integer.toHexString((Integer) param.args[3])
                                + ", p2=" + Integer.toHexString((Integer) param.args[4])
                                + ", p3=" + Integer.toHexString((Integer) param.args[5])
                                + ", cmd=" + param.args[6] + ")";
                        Method verboseRlog = Class.forName("android.telephony.Rlog").getDeclaredMethod("v", String.class, String.class);
                        verboseRlog.invoke(null, TAG, String.format(
                                "UiccProfile APDU: %s", apduString));
                    }
                });
        XposedHelpers.findAndHookMethod(
                "com.android.internal.telephony.uicc.UiccProfile",
                lpparam.classLoader, "iccExchangeSimIO",
                int.class,
                int.class,
                int.class,
                int.class,
                int.class,
                String.class,
                Message.class,
                new XC_MethodHook() {

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        String apduString = "ApduCommand(fileId=" + param.args[0]
                                + ", ins=" + Integer.toHexString((Integer) param.args[1])
                                + ", p1=" + Integer.toHexString((Integer) param.args[2])
                                + ", p2=" + Integer.toHexString((Integer) param.args[3])
                                + ", p3=" + Integer.toHexString((Integer) param.args[4])
                                + ", pathId=" + param.args[5] + ")";
                        Method verboseRlog = Class.forName("android.telephony.Rlog").getDeclaredMethod("v", String.class, String.class);
                        verboseRlog.invoke(null, TAG, String.format(
                                "UiccProfile SimIO: %s", apduString));
                    }
                });

//        http://aosp.opersys.com/xref/android-11.0.0_r33/xref/frameworks/opt/telephony/src/java/com/android/internal/telephony/RIL.java#4159
        XposedHelpers.findAndHookMethod(
                "com.android.internal.telephony.RIL",
                lpparam.classLoader, "iccOpenLogicalChannel",
                String.class,
                int.class,
                Message.class,
                new XC_MethodHook() {

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Method verboseRlog = Class.forName("android.telephony.Rlog").getDeclaredMethod("v", String.class, String.class);
                        verboseRlog.invoke(null, TAG, String.format(
                                "RIL iccOpenLogicalChannel: %s", param.args[0]));
                    }
                });
        XposedHelpers.findAndHookMethod(
                "com.android.internal.telephony.RIL",
                lpparam.classLoader, "iccCloseLogicalChannel",
                int.class,
                Message.class,
                new XC_MethodHook() {

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Method verboseRlog = Class.forName("android.telephony.Rlog").getDeclaredMethod("v", String.class, String.class);
                        verboseRlog.invoke(null, TAG, String.format(
                                "RIL iccCloseLogicalChannel: %d", (int)param.args[0]));
                    }
                });
        XposedHelpers.findAndHookMethod(
                "com.android.internal.telephony.RIL",
                lpparam.classLoader, "iccTransmitApduLogicalChannel",
                int.class,
                int.class,
                int.class,
                int.class,
                int.class,
                int.class,
                String.class,
                Message.class,
                new XC_MethodHook() {

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        String apduString = "ApduCommand(channel=" + param.args[0]
                                + ", cla=" + Integer.toHexString((Integer) param.args[1])
                                + ", ins=" + Integer.toHexString((Integer) param.args[2])
                                + ", p1=" + Integer.toHexString((Integer) param.args[3])
                                + ", p2=" + Integer.toHexString((Integer) param.args[4])
                                + ", p3=" + Integer.toHexString((Integer) param.args[5])
                                + ", cmd=" + param.args[6] + ")";
                        Method verboseRlog = Class.forName("android.telephony.Rlog").getDeclaredMethod("v", String.class, String.class);
                        verboseRlog.invoke(null, TAG, String.format(
                                "RIL iccTransmitApduLogicalChannel: %s", apduString));
                    }
                });
        // http://aosp.opersys.com/xref/android-11.0.0_r33/xref/packages/apps/SecureElement/src/com/android/se/SecureElementService.java#235
//        XposedHelpers.findAndHookMethod(
//                "com.android.se.SecureElementService$SecureElementSession",
//                lpparam.classLoader, "openLogicalChannel",
//                String.class,
//                byte.class,
//                Class.forName("android.se.omapi.ISecureElementListener"),
//                new XC_MethodHook() {
//
//                    @Override
//                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
//                        Method verboseRlog = Class.forName("android.telephony.Rlog").getDeclaredMethod("v", String.class, String.class);
//                        verboseRlog.invoke(null, TAG, String.format(
//                                "SecureElementService$SecureElementSession openLogicalChannel: %s", param.args[0]));
//                    }
//                });

        // http://aosp.opersys.com/xref/android-11.0.0_r33/xref/packages/apps/SecureElement/src/com/android/se/Terminal.java#393

//        XposedHelpers.findAndHookMethod(
//                "com.android.se.Terminal",
//                lpparam.classLoader, "select",
//                byte[].class,
//                new XC_MethodHook() {
//
//                    @Override
//                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
//                        Method verboseRlog = Class.forName("android.telephony.Rlog").getDeclaredMethod("v", String.class, String.class);
//                        verboseRlog.invoke(null, TAG, String.format(
//                                "Terminal select: %s", byteArrayToHexString((byte[]) param.args[0])));
//                    }
//                });
//        XposedHelpers.findAndHookMethod(
//                "com.android.se.Terminal",
//                lpparam.classLoader, "transmit",
//                byte[].class,
//                new XC_MethodHook() {
//
//                    @Override
//                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
//                        Method verboseRlog = Class.forName("android.telephony.Rlog").getDeclaredMethod("v", String.class, String.class);
//                        verboseRlog.invoke(null, TAG, String.format(
//                                "Terminal transmit: %s", byteArrayToHexString((byte[]) param.args[0])));
//                    }
//                });
    }

    /**
     * Converts byte array to hex string
     *
     * @param bytes The data
     * @return String represents the data in HEX string
     */
    private static String byteArrayToHexString(final byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

}
