# Kate — Capability Roadmap

Scope agreed: no LLM budget right now, so intelligence comes from a much
richer rule/intent engine + local memory + real APIs (weather, wiki,
jokes) + full accessibility-driven device control. Every screen gets a
visual pass. Anywhere a feature isn't live yet, the UI says so plainly
("Offline mode — coming soon, stay tuned") instead of pretending.

Legend: 🟢 no new permission needed · 🟡 new permission/manifest change ·
🔴 Play Store data-safety disclosure required

---

## Batch 1 — Brain foundation (memory, identity, core intents)
No new dangerous permissions. This is the base everything else sits on.

- [ ] 🟢 Conversation memory: Room entity storing rolling turn history
      (last N exchanges) + durable facts (user's name, last topic),
      injected into classification so follow-ups resolve correctly
- [ ] 🟢 Onboarding: ask "What should I call you?" on fresh install,
      store name, use it in greetings/jokes/responses
- [ ] 🟢 Instant spoken acknowledgment the moment the overlay is
      triggered ("Yes?" / "I'm listening"), before STT starts —
      currently she goes silent-straight-to-listening
- [ ] 🟢 Identity intent: "who made you / who created you" → fixed
      factual answer naming Ede Johnwesley, founder of Purple Labs,
      plus creation year; also self-name questions ("what's your name",
      "who are you")
- [ ] 🟢 Time intent: "what time is it" → immediate local answer,
      no permission needed
- [ ] 🟢 Alarm intent: "set an alarm for 7am" → `AlarmClock.ACTION_SET_ALARM`
      implicit intent (opens system clock pre-filled, no special permission)
- [ ] 🟢 Math/calculator intent: local expression parser for arithmetic
      ("what's 45 times 12", "12% of 300") — no network dependency
- [ ] 🟢 Small talk / conversational intents: hi, hello, what's up,
      how are you, thank you, goodbye — so Kate can be chatted with
      before a task, not just command-parsed
- [ ] 🟢 Jokes: free joke API (e.g. JokeAPI), category selected by
      tone slider (professional → clean/dad jokes, sassy → edgier)

## Batch 2 — Knowledge fallback + Chat screen fix
- [ ] 🟢 Wikipedia REST summary API as fallback when DuckDuckGo
      instant-answer returns nothing, chained: DDG → Wikipedia → honest
      "I couldn't find a good answer" (no invented answers)
- [ ] 🟢 **Fix ChatScreen**: currently calls a remote `repository.chat()`
      endpoint that isn't live, so every message fails over silently.
      Reroute chat input through the same local `KateCommandProcessor`
      used by voice/overlay, so typed chat gets identical intent
      handling, small talk, jokes, weather, math, search fallback, etc.

## Batch 3 — Device control via accessibility (global actions) ✅ DONE
Extends `KateAccessibilityService`. No manifest change for most of these.
- [x] 🟢 Go home (`GLOBAL_ACTION_HOME`)
- [x] 🟢 Go back (`GLOBAL_ACTION_BACK`)
- [x] 🟢 Recent apps (`GLOBAL_ACTION_RECENTS`)
- [x] 🟢 Lock phone (`GLOBAL_ACTION_LOCK_SCREEN`, Android 9+)
- [x] 🟡 Screenshot (`GLOBAL_ACTION_TAKE_SCREENSHOT`, Android 9+) —
      requires `android:canTakeScreenshot="true"` in the accessibility
      service's config XML (added)

## Batch 4 — Targeted messaging (WhatsApp / Messenger / etc.) ✅ DONE
- [x] 🟡 "Message [name] on WhatsApp saying [text]" — via accessibility:
      open WhatsApp, find the contact, find compose field, type, send.
      Same pattern for Facebook Messenger. No public send-intent API for
      either, so this is accessibility automation, which means it's
      slower and more fragile than native SMS — needs per-app UI
      selectors, and those break when WhatsApp/Messenger update their UI.
      Implemented in MessagingAppAutomator - view IDs captured against
      current builds, WILL need updating if WhatsApp/Messenger change UI.
- [x] 🟢 No app specified → falls back to existing SMS behavior
      (already works today)

## Batch 5 — Call handling ✅ DONE
- [x] 🟡 Announce incoming caller by name via TTS: implemented via a plain
      registered BroadcastReceiver on ACTION_PHONE_STATE_CHANGED in
      KateForegroundService (matches the existing powerReceiver pattern),
      not TelephonyCallback/PhoneStateListener. Caller name resolved via
      ContactsHelper's existing number matching - READ_CALL_LOG was NOT
      needed after all, only the already-declared READ_PHONE_STATE +
      READ_CONTACTS.
- [x] 🟡🔴 Voice-driven call actions ("answer", "decline") — ANSWER_PHONE_CALLS
      added to manifest. Answer uses the real TelecomManager.acceptRingingCall()
      API (solid). Decline has NO public API on Android unless the app IS
      the default dialer - implemented via accessibility UI-button-tapping
      instead (fragile, same caveat as Batch 4's messaging automation).
      "Call back" was not separately implemented - the existing MakeCall
      intent already covers it ("call [name]" after a missed call).
      STILL NEEDS: Play Console Data Safety form disclosure for
      ANSWER_PHONE_CALLS before release - this is the single most
      sensitive permission in the whole roadmap, expect extra Play review
      scrutiny.

## Batch 6 — UI pass (all screens)
- [ ] 🟢 Sharpen visual design across every screen (Home, Chat, Settings,
      History, Onboarding, Login/Register, Premium, Admin, Legal) using
      the existing purple/lime dark palette more deliberately — better
      spacing, elevation, motion, typography hierarchy rather than a
      palette rebuild
- [ ] 🟢 Add "Offline mode — coming soon, stay tuned" banners/labels
      anywhere a feature depends on future work, so users aren't misled
      about current capability (transparency requirement)

## Batch 7 — Docs & compliance
- [ ] 🟢 Update Privacy Policy: disclose network calls now made
      (weather already did; add Wikipedia, joke API, and any future
      chat backend), accessibility scope (messaging automation, global
      actions), call-state listening
- [ ] 🟢 Update User Agreement to match new capabilities
- [ ] 🔴 Update Play Console Data Safety form once Batch 5 (call
      permissions) ships — this is a store-listing requirement, not
      just an in-app doc

---
 
## Explicitly out of scope for now
- No LLM integration (no budget) — intelligence stays rule/intent-based
- No passive/background journaling or stress/lie-detection features
  (removed from file_tree.txt per earlier decision)

## Suggested build order
Batch 1 → Batch 2 → Batch 6 (can run in parallel with 3–5, it's just UI) →
Batch 3 → Batch 4 → Batch 5 → Batch 7 (Batch 7 partially updates as each
batch lands, finalized after Batch 5)
