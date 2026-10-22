#!/usr/bin/env bash
# End-to-end check on a running emulator/phone: installs the app, grants
# notification access, posts fake notifications and checks what got captured.
set -euo pipefail

PKG=com.paulesthor.arepondre
APK=${1:-app/build/outputs/apk/debug/app-debug.apk}

adb wait-for-device
adb install -r "$APK"
adb shell cmd notification allow_listener "$PKG/$PKG.MessageListenerService"
adb shell am start -n "$PKG/.MainActivity"
sleep 5
adb logcat -c

# A chat message (MessagingStyle, like WhatsApp/Messages) -> must be captured.
adb shell "cmd notification post -S messaging --conversation Lea --message 'Lea:Tu viens samedi' --message 'Lea:Reponds moi stp' chat1 x"
# A regular notification (not a message) -> must be ignored.
adb shell "cmd notification post -t 'Mise a jour' other1 'Batterie chargee'"
sleep 5

adb logcat -d -s ARepondre:I > capture.log
cat capture.log

fail=0
grep -q "text=Tu viens samedi" capture.log || { echo "FAIL: chat message not captured"; fail=1; }
grep -q "text=Reponds moi stp" capture.log || { echo "FAIL: second chat message not captured"; fail=1; }
if grep -q "Batterie" capture.log; then echo "FAIL: non-message notification was captured"; fail=1; fi

adb exec-out screencap -p > screenshot.png || true
[ "$fail" = 0 ] && echo "OK: capture works"
exit "$fail"
