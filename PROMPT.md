I want you to install "Personal TV Home", a custom home screen (launcher) app for Android TV / Google TV, on my TV.
Repo: https://github.com/alex99ka/personal-tv-home
Prebuilt APK: https://github.com/alex99ka/personal-tv-home/releases/latest/download/personal-tv-home.apk

I'm not technical. Guide me one step at a time in plain language, wait for me to confirm each step on the TV, and run the computer-side commands yourself. Read the repo's README.md first: it has the full reference (brand-specific menus, commands, undo).

## 1. Check my TV is compatible (do this first)
Ask me for my TV brand and model, and to look in Settings → System → About (or Settings → Device Preferences → About).
- Works: Google TV / Android TV. That covers TCL, Xiaomi / Mi TV (Android TV or Google TV models), Sony Bravia, Hisense (Android/Google TV models only), Philips (Android TV models), Nvidia Shield, Chromecast with Google TV, Onn and Android TV boxes. Android 8 or newer.
- Does NOT work: LG (webOS), Samsung (Tizen), Roku, Hisense VIDAA, Philips Titan OS, Panasonic. They don't run Android apps. If I have one of these, tell me kindly and stop. Don't try workarounds.
- Amazon Fire TV: the app installs, but Fire OS won't let you replace the Home screen. Tell me that and ask whether I still want it as a normal app.
- If you're unsure, ask me to read out what the About screen says ("Android TV OS", the Android version, the build).

## 2. Set up my computer
- Find out my OS. Check whether `adb` is installed (`adb version`). If it isn't, install Android Platform Tools: Windows `winget install Google.PlatformTools` (then open a new terminal or use the full path), Mac `brew install android-platform-tools`, Linux `sudo apt install adb`, or download it from https://developer.android.com/tools/releases/platform-tools.
- Download the APK from the link above. You don't need to build anything.

## 3. Walk me through turning on ADB on the TV
The menu names differ by brand and Android version, so adapt to what I tell you I see:
- Unlock Developer options: press OK 7 times on the build entry. On Google TV (TCL, Sony, Hisense, Chromecast, newer Xiaomi) it's Settings → System → About → "Android TV OS build". On older Android TV (Xiaomi PatchWall, Philips, Shield, boxes) it's Settings → Device Preferences → About → "Build". Some brands put About under "Device Preferences" even on Google TV. If it isn't there, ask me what I see and help me find it.
- Then go to Developer options (under System or Device Preferences): turn on "USB debugging". On TCL that alone opens network ADB. Also turn on "Network debugging" / "ADB debugging" if it exists. If there's only "Wireless debugging" (some newer Google TVs), turn that on and use pairing (below).
- Turn OFF "Verify apps over USB" if it exists. Otherwise the install fails with INSTALL_FAILED_VERIFICATION_FAILURE.
- Get the TV's IP: Settings → Network & Internet → my network → IP address. You may also find it on my network yourself, but ask me before scanning.
- Warn me: some TVs cut power to their USB ports while debugging is on, so a USB speaker or drive may stop until I turn it off again.

## 4. Connect
- `adb connect <IP>:5555`. Tell me BEFORE you run it that a "Allow USB debugging?" box will pop up on the TV, and that I should tick "Always allow" and press Allow. Then confirm with `adb devices` that it says `device` (not `unauthorized` or `offline`).
- If port 5555 is refused and the TV has "Wireless debugging": ask me to open Wireless debugging → "Pair device with pairing code" and read you the IP:port and the 6-digit code. Then run `adb pair <IP>:<pair_port> <code>`, `adb connect <IP>:<port shown on the main Wireless debugging screen>`, and optionally `adb tcpip 5555`.
- Use `adb -s <IP>:5555` (or the connected serial) on every command so you never touch another device.

## 5. Install
```
adb -s <IP>:5555 install -r personal-tv-home.apk
adb -s <IP>:5555 shell cmd notification allow_listener local.personalhome/local.personalhome.MusicListener
```
- INSTALL_FAILED_VERIFICATION_FAILURE: have me turn off "Verify apps over USB" in Developer options, then retry. Don't try to change verifier settings yourself.
- If `adb install` hangs, the TV is probably waiting for an on-screen confirmation, so ask me to look. Fallback: `adb push` the APK to /data/local/tmp/ and run `adb shell pm install -r /data/local/tmp/personal-tv-home.apk`.
- TCL only: if the app's background features misbehave after a reboot, run `adb shell appops set local.personalhome APP_AUTO_START allow`.

## 6. Make it the Home screen
```
adb -s <IP>:5555 shell cmd package set-home-activity local.personalhome/.MainActivity
adb -s <IP>:5555 shell input keyevent HOME
```
Ask me to press Home on the remote and say whether I see the new glass home screen. If Home still opens the old launcher, explain that the stock launcher has to be disabled. It's reversible, but on Google TV it hides the "For you" tab and a few Google settings screens. Only do it after I say yes:
- Google TV: `pm disable-user --user 0 com.google.android.apps.tv.launcherx`
- Older Android TV: `com.google.android.tvlauncher`
- Xiaomi PatchWall: `com.mitv.tvhome`
- Other brands: find the current launcher with `adb shell cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME` and tell me what it is before disabling it.
Never uninstall or disable anything else on the TV.

## 7. Show me how to use it
Briefly explain: the dock holds my apps (All Apps is at the end). Holding OK on an app gives Open / App info / Add to bar / Remove from bar. The round button at the top right (or the remote's Settings button) opens Settings. In there, "Wallpapers" → Google Photos lets me use my own photo album (scan the QR code, paste a shared album link), and "Stremio account" adds my Stremio library as a row on Home if I use Stremio. When music plays, a glass player appears.

## 8. Finish safely
- Remind me to go back to Developer options and turn USB / Network / Wireless debugging OFF. The launcher keeps working, and nobody else on my Wi-Fi can control the TV.
- Tell me how to undo it all later: reconnect ADB, `adb shell pm enable <launcher you disabled>` and `adb uninstall local.personalhome`.

Rules: only use commands on my TV that this prompt or the repo README mention, explain each step before you do it, and if something fails twice, stop and tell me what you see instead of guessing.
