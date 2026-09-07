Kate Android - Fix Zip
=======================

Six files, all overwrite in place at the same repo-relative path shown
below. Structure matches apply-fix-zip.yml's expected layout (unzip at
repo root).

1. app/src/main/java/com/dti/kate/service/MessagingAppAutomator.kt
   - WhatsApp Business (com.whatsapp.w4b) was never targeted - the
     package name was hardcoded to regular WhatsApp (com.whatsapp), so
     messaging always opened Messenger-style WhatsApp regardless of
     which variant is actually installed/used.
   - Now resolves whichever is actually installed, preferring Business,
     and builds every accessibility view-ID lookup from that resolved
     package rather than a hardcoded "com.whatsapp:id/" prefix.
   - CAVEAT: assumes WhatsApp Business shares the same internal view-ID
     *names* as regular WhatsApp (just under its own package prefix) -
     reasonable since Business is built from largely the same codebase,
     but not verified against a real Business install. Worth confirming
     this actually completes a send end-to-end, not just that it opens
     the right app.

2. app/src/main/java/com/dti/kate/ui/overlay/KateOverLayService.kt
   - Root cause of "overlay crashes on search/calculate" and "response
     gets cut off": showResultText() (which displays search/calculation
     results as text in the overlay's expanded panel) touched views and
     called toggleExpanded() directly from the IO-dispatcher coroutine
     that runs the whole listen cycle - never posted to the main thread.
     Confirmed via bugreport: CalledFromWrongThreadException thrown from
     a DefaultDispatcher-worker thread, stack trace running straight
     through toggleExpanded <- showResultText <- startListenCycle.
   - Same class of bug as the earlier setState()/scheduleAutoCollapse()
     fixes (same session, different functions) - this one was missed
     since it's only reached when there's actual text to show, which is
     only true for search/calculate, never for voice-only replies like
     "turn on the flashlight."
   - Also very likely explains the microphone foreground-service
     SecurityException seen ~22s after the crash in the same bugreport -
     losing the overlay window (which the crash causes) removes one of
     Android's eligibility exemptions for restarting a microphone-type
     foreground service. Should stop happening as a side effect of this
     fix, not a separate thing that needed its own change.
   - Made toggleExpanded() itself self-safe (posts to main internally)
     for defense-in-depth, same approach as setState() - protects any
     future caller by default, not just the ones known about today.
   - Also updated: added hasCallPhone()/requestCallPhone() to this
     file's PermissionBridge implementation (see item 6 below).

3. app/src/main/java/com/dti/kate/ui/screen/HomeScreen.kt
4. app/src/main/java/com/dti/kate/ui/screen/ChatScreen.kt
   - Added a callPhonePermission state (rememberPermissionState) and
     wired hasCallPhone()/requestCallPhone() into each screen's
     PermissionBridge implementation, matching the existing pattern
     already used for contactsPermission/locationPermission.

5. app/src/main/java/com/dti/kate/core/KateResponseGenerator.kt
   - Added speechForCallNeedsConfirm() - a distinct, honest response for
     when the dialer was opened pre-filled (not yet dialed) rather than
     dialed directly. Previously speechForCall's "Calling X now" wording
     was used for both cases, which was actively misleading when nothing
     had actually been dialed yet.

6. app/src/main/java/com/dti/kate/core/KateCommandProcessor.kt
   - THE ACTUAL FIX for "call Fortune opens the dialer/contacts app but
     doesn't dial": CALL_PHONE was declared in the manifest and CHECKED
     in DeviceControlManager.makeCall() (which correctly falls back to
     ACTION_DIAL - opens the dialer pre-filled, requires a manual tap -
     when it's not granted), but the permission was never actually
     REQUESTED anywhere in the app. Since Android requires an explicit
     runtime grant for dangerous permissions like CALL_PHONE (manifest
     declaration alone isn't enough), it was permanently stuck at "not
     granted" with no way for the user to ever grant it, so every call
     silently used the ACTION_DIAL fallback forever.
   - MakeCall's handler now checks hasCallPhone() first. If granted,
     behavior is unchanged (dials directly, speaks the normal "Calling
     X" response). If not granted, it now (a) requests the permission
     via the bridge - won't take effect for the current call attempt
     (no await/retry, consistent with how the existing Contacts flow
     also just requests-and-moves-on rather than blocking), but sets up
     a real permission prompt for next time, and (b) speaks the new
     honest speechForCallNeedsConfirm() response instead of pretending
     the call is already in progress.
   - Practical effect: the very next time you grant CALL_PHONE when
     prompted, calls should start dialing directly. Until granted, at
     least the spoken response now matches what's actually happening.

STILL NOT FULLY RESOLVED:
   The original report was "call Fortune opens the contacts/call app
   without actually calling" - the fix above addresses the mechanism
   that would cause exactly that (missing CALL_PHONE runtime grant), and
   the earlier "bare call with no name" mixup from before is now
   understood separately. Please test with a fresh permission grant
   after this build - if it still doesn't dial directly even after
   granting CALL_PHONE when prompted, that would point to something else
   entirely and I'd want a fresh debug log for that specific attempt.
