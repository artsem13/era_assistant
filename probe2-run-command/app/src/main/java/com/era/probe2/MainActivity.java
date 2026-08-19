package com.era.probe2;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

public final class MainActivity extends Activity {
    static final String TERMUX_PACKAGE = "com.termux";
    static final String TERMUX_RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService";
    static final String RUN_COMMAND = "com.termux.RUN_COMMAND";
    static final String EXTRA_PATH = "com.termux.RUN_COMMAND_PATH";
    static final String EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS";
    static final String EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR";
    static final String EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND";
    static final String EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT";

    static final String WORKER_PATH = "/data/data/com.termux/files/home/.era_probe2/worker.sh";
    static final String WORKDIR = "/data/data/com.termux/files/home";
    static final String ACTION_START = "START";
    static final String ACTION_STATUS = "STATUS";
    static final String ACTION_RESULT = "RESULT";
    static final String ACTION_CANCEL = "CANCEL";

    private static final int FLAG_MUTABLE_COMPAT = 0x02000000;
    private final AtomicInteger requestCounter = new AtomicInteger(1);
    private TextView output;
    private String currentTaskId;
    private String currentAttemptId;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);

        TextView title = new TextView(this);
        title.setText("Probe 2 — fixed RUN_COMMAND sender\nNo external input; worker path and actions are constants.");
        root.addView(title);

        addButton(root, "A: START normal (5s)", new View.OnClickListener() {
            @Override public void onClick(View view) { startTask(5); }
        });
        addButton(root, "B: START long (45s)", new View.OnClickListener() {
            @Override public void onClick(View view) { startTask(45); }
        });
        addButton(root, "B: STATUS current task", new View.OnClickListener() {
            @Override public void onClick(View view) { sendControl(ACTION_STATUS); }
        });
        addButton(root, "C: CANCEL current task", new View.OnClickListener() {
            @Override public void onClick(View view) { sendControl(ACTION_CANCEL); }
        });
        addButton(root, "RESULT current task", new View.OnClickListener() {
            @Override public void onClick(View view) { sendControl(ACTION_RESULT); }
        });

        output = new TextView(this);
        output.setText(CommandResultReceiver.readLog(this));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(output);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private void addButton(LinearLayout root, String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(listener);
        root.addView(button);
    }

    private void startTask(int durationSeconds) {
        long now = System.currentTimeMillis();
        currentTaskId = String.format(Locale.US, "p2-%x-%x", now, requestCounter.getAndIncrement());
        currentAttemptId = "a1";
        send(ACTION_START, currentTaskId, currentAttemptId, Integer.toString(durationSeconds));
    }

    private void sendControl(String action) {
        if (currentTaskId == null || currentAttemptId == null) {
            append("No current task. Start a fixed probe task first.");
            return;
        }
        send(action, currentTaskId, currentAttemptId, "");
    }

    private void send(String action, String taskId, String attemptId, String duration) {
        final int requestCode = requestCounter.getAndIncrement();
        Intent intent = new Intent(RUN_COMMAND);
        intent.setClassName(TERMUX_PACKAGE, TERMUX_RUN_COMMAND_SERVICE);
        intent.putExtra(EXTRA_PATH, WORKER_PATH);
        intent.putExtra(EXTRA_ARGUMENTS, new String[]{action, taskId, attemptId, duration});
        intent.putExtra(EXTRA_WORKDIR, WORKDIR);
        intent.putExtra(EXTRA_BACKGROUND, true);

        Intent resultIntent = new Intent(this, CommandResultReceiver.class);
        resultIntent.putExtra(CommandResultReceiver.EXTRA_REQUEST_CODE, requestCode);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                requestCode,
                resultIntent,
                PendingIntent.FLAG_ONE_SHOT | (Build.VERSION.SDK_INT >= 31 ? FLAG_MUTABLE_COMPAT : 0));
        intent.putExtra(EXTRA_PENDING_INTENT, pendingIntent);

        append("SEND action=" + action + " taskId=" + taskId + " attemptId=" + attemptId
                + " requestCode=" + requestCode);
        try {
            startService(intent);
            append("INTENT_ACCEPTED requestCode=" + requestCode);
        } catch (SecurityException e) {
            append("INTENT_REJECTED permission_or_policy=" + e.getClass().getSimpleName());
        } catch (RuntimeException e) {
            append("INTENT_REJECTED runtime=" + e.getClass().getSimpleName());
        }
    }

    private void append(String line) {
        CommandResultReceiver.appendLog(this, line);
        if (output != null) {
            output.append(line + "\n");
        }
    }
}
