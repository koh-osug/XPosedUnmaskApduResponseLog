package de.ohmesoftware.xposed.apduresponselog;

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
 *     Does not apply to a special app or package, but is used system wide.
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
        XposedHelpers.findAndHookMethod(
                "com.android.internal.telephony.uicc.euicc.apdu.TransmitApduLogicalChannelInvocation",
                lpparam.classLoader,"parseResult",
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
                                    "IccIoResult unmasked Payload: %s", byteArrayToHexString(data)));
                        }
                    }
                });
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
