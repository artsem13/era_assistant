# Probe 2 — disposable RUN_COMMAND harness

This directory is test-only and isolated from `app/`. It is not production
bridge code. The sender has no text fields and has only fixed action buttons:
`START` (5/45 seconds), `STATUS`, `RESULT`, and `CANCEL`.

The only executable path ever sent is:

`/data/data/com.termux/files/home/.era_probe2/worker.sh`

The only working directory is the Termux private home. Arguments are generated
inside the sender and are restricted again by the worker to the four action
names, bounded IDs, and durations 5/45/60. No stdin, shell string, model input,
workspace path, or arbitrary executable is supported.

## Device setup boundary

The already-approved Termux setting is required:

`allow-external-apps = true`

The worker must be copied into Termux private storage and made executable. A
user may do this from Termux after the APK is installed:

```sh
mkdir -p "$HOME/.era_probe2"
cp /sdcard/Era/Era_From_Zip/probe2-run-command/termux/worker.sh "$HOME/.era_probe2/worker.sh"
chmod 700 "$HOME/.era_probe2/worker.sh"
```

The disposable APK then requires the user-granted Android permission
`com.termux.permission.RUN_COMMAND` in Android Settings:

Settings → Apps → Era Probe 2 → Permissions → Additional permissions → Run commands in Termux environment.

Do not grant this permission to the production Era app for this probe.

## Probe sequence

1. Press `A: START normal (5s)`, wait for callback, then press `RESULT current task`.
2. Press `B: START long (45s)`, quickly press `STATUS current task` while it is running.
3. While the same task is running, press `C: CANCEL current task`, then press `RESULT current task`.
4. Preserve the sender log and the Termux-private `~/.era_probe2/tasks/<taskId>/`
   directory as evidence. Do not force-stop Termux. Any sender force-stop is a
   separate manual test boundary.

For every run, record the sender log plus the task `state`, `journal.log`,
`heartbeat`, and the final `RESULT`. The journal is append-only; `state` is
replaced atomically. The worker never writes project content or secrets.

Build from the harness directory with the existing project wrapper, without
invoking the production `:app` project:

```sh
export JAVA_HOME=/opt/java11
export ANDROID_HOME=/opt/android-sdk/android-sdk
export ANDROID_SDK_ROOT=/opt/android-sdk/android-sdk
bash ../gradlew assembleDebug
```

The APK is `app/build/outputs/apk/debug/app-debug.apk` inside this directory.
