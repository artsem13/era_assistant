package com.era.probe2;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

public final class CommandResultReceiver extends BroadcastReceiver {
    static final String EXTRA_REQUEST_CODE = "probe2_request_code";
    private static final String RESULT_BUNDLE = "com.termux.service.EXTRA_PLUGIN_RESULT_BUNDLE";
    private static final String STDOUT = "com.termux.service.extra.PLUGIN_RESULT_BUNDLE_STDOUT";
    private static final String STDERR = "com.termux.service.extra.PLUGIN_RESULT_BUNDLE_STDERR";
    private static final String EXIT_CODE = "com.termux.service.extra.PLUGIN_RESULT_BUNDLE_EXIT_CODE";
    private static final String ERR = "com.termux.service.extra.PLUGIN_RESULT_BUNDLE_ERR";
    private static final String ERRMSG = "com.termux.service.extra.PLUGIN_RESULT_BUNDLE_ERRMSG";
    private static final String LOG = "probe2_log";

    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle result = intent.getBundleExtra(RESULT_BUNDLE);
        if (result == null) {
            appendLog(context, "CALLBACK requestCode=" + intent.getIntExtra(EXTRA_REQUEST_CODE, 0)
                    + " missing_result_bundle");
            return;
        }
        String stdout = result.getString(STDOUT, "");
        String stderr = result.getString(STDERR, "");
        appendLog(context, "CALLBACK requestCode=" + intent.getIntExtra(EXTRA_REQUEST_CODE, 0)
                + " exitCode=" + result.getInt(EXIT_CODE, Integer.MIN_VALUE)
                + " err=" + result.getInt(ERR, Integer.MIN_VALUE)
                + " stdout=" + bounded(stdout)
                + " stderr=" + bounded(stderr)
                + " errmsg=" + bounded(result.getString(ERRMSG, "")));
    }

    private static String bounded(String value) {
        if (value == null) return "<null>";
        return value.length() <= 512 ? value : value.substring(0, 512) + "…";
    }

    static void appendLog(Context context, String line) {
        String old = context.getSharedPreferences(LOG, Context.MODE_PRIVATE).getString("lines", "");
        String next = old + line + "\n";
        if (next.length() > 8192) next = next.substring(next.length() - 8192);
        context.getSharedPreferences(LOG, Context.MODE_PRIVATE).edit().putString("lines", next).apply();
    }

    static String readLog(Context context) {
        return context.getSharedPreferences(LOG, Context.MODE_PRIVATE).getString("lines", "");
    }
}
