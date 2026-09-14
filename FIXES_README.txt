Kate Android - Combined Fix Zip (everything from both recent rounds, one upload)
==================================================================================

Nine files, all overwrite in place at the repo-relative path shown.
Structure matches apply-fix-zip.yml's expected layout (unzip at repo root).

PLEASE CONFIRM A FULLY FRESH BUILD after merging this - several of these
fixes (the call permission prompt, WhatsApp Business resolution) should
produce visibly different behavior. If nothing changes at all after
testing, the most likely explanation is a stale/cached build rather than
a new bug - worth ruling that out before chasing anything further.

===========================================================================
1. app/src/main/java/com/dti/kate/service/MessagingAppAutomator.kt
===========================================================================
WhatsApp Business (com.whatsapp.w4b) resolution - prefers Business if
installed, builds every accessibility view-ID lookup from whichever
package was actually resolved rather than a hardcoded "com.whatsapp:id/"
prefix.

===========================================================================
2. app/src/main/java/com/dti/kate/ui/overlay/KateOverLayService.kt
===========================================================================
The showResultText()/toggleExpanded() main-thread fix - root cause of the
overlay crash on search/calculate and the "response cut off" symptom.
Confirmed working.

===========================================================================
3. app/src/main/java/com/dti/kate/ui/screen/ChatScreen.kt
===========================================================================
callPhonePermission wired into its PermissionBridge implementation.

===========================================================================
4. app/src/main/java/com/dti/kate/core/KateResponseGenerator.kt
===========================================================================
speechForCallNeedsConfirm() - honest response when the dialer opened
pre-filled rather than actually dialing.

===========================================================================
5. app/src/main/java/com/dti/kate/core/KateCommandProcessor.kt
===========================================================================
MakeCall now checks hasCallPhone() and requests it if missing, uses the
honest response above when it can't dial directly yet.

===========================================================================
6. app/src/main/java/com/dti/kate/ui/screen/HomeScreen.kt
===========================================================================
- callPhonePermission wired into its PermissionBridge implementation.
- phoneStatePermission (READ_PHONE_STATE) added, requested proactively on
  first HomeScreen load - this is the actual fix that should make
  incoming-call announcement start working. That feature was already
  fully built in KateForegroundService.kt (contact lookup, TTS
  announcement, dedup logic) but could never activate since the
  permission was declared in the manifest but never requested anywhere.

===========================================================================
7. app/src/main/java/com/dti/kate/ui/screen/SettingsScreen.kt
===========================================================================
- Wake gesture availability check: Raise-to-Wake and Shake both depend on
  TYPE_ACCELEROMETER: now checked once via SensorManager, and both
  toggles show "Not supported on this device" (taps ignored) if it's
  missing, instead of silently doing nothing.
- "Set Kate as your Digital Assistant" card (only shown where
  ROLE_ASSISTANT is available - API 29+): launches Android's own
  role-request system picker so the user can explicitly select Kate.

===========================================================================
8. app/src/main/AndroidManifest.xml
===========================================================================
New <activity> entry for KateAssistActivity with an ACTION_ASSIST
intent-filter - the actual mechanism that makes Kate ELIGIBLE to appear
in Android's "Digital assistant app" picker at all.

===========================================================================
9. app/src/main/java/com/dti/kate/ui/KateAssistActivity.kt (new file)
===========================================================================
Trampoline activity, zero UI of its own - catches the system assist
gesture and hands off to the existing overlay, then finishes immediately.

===========================================================================
NOTE ON THE ASSISTANT FEATURE
===========================================================================
Being ELIGIBLE (files 8-9) and being SELECTED (a manual step - either the
new Settings card in file 7, or manually via Android's own Settings >
Apps > Default apps > Digital assistant app) are two different things.
After this build, Kate should appear as an option in that list for the
first time - she still needs to be explicitly picked there for the
assist gesture to actually open her.
