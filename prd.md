# PRD — Android Action Narrator Accessibility Service

## 1. Product concept

**Working name:** PassiveVoice
**Category:** Android Accessibility Service
**Primary users:** Blind and low-vision Android users
**Core idea:**

> **Speak what the user does, without changing how the user touches the phone.**

ActionVoice observes normal Android interaction and converts actions and resulting UI changes into concise speech.

For example:

* User taps **Like** → “Liked.”
* User taps **Follow** → “Following.”
* User swipes vertically through Instagram → “Next post.”
* User presses **Back** → “Back.”
* User opens a menu → “Menu opened.”
* User changes a switch → “Notifications on.”
* User selects a tab → “Home tab.”
* User scrolls a list → “More content.”
* User types → optionally “A”, “B”, etc., depending on configuration.

The user's finger remains completely under Android's normal touch handling.

---

# 2. The problem

TalkBack is extremely powerful, but it changes the interaction model of Android.

A blind user may sometimes want:

> **Normal touchscreen interaction + just enough auditory confirmation to understand what their physical action caused.**

Examples:

### Scenario A — Instagram

The user performs a normal swipe.

Instead of requiring TalkBack's touch-exploration interaction, ActionVoice observes the resulting accessibility/UI changes and says:

> “Next reel.”

### Scenario B — unfamiliar application

The user taps something.

The application exposes an accessibility event containing:

> Button — Share

ActionVoice can say:

> “Share.”

### Scenario C — custom interface

An application may expose poor or unusual accessibility semantics.

ActionVoice should be able to fall back to contextual interpretation:

> “Selected.”

rather than attempting to completely replace the application's interaction model.

---

# 3. Product philosophy

The product should have five fundamental principles.

### 1. Passive

ActionVoice observes.

It does not control the user's finger.

### 2. Non-interfering

No:

* touch interception
* gesture injection
* touch exploration
* fake touch layer
* full-screen transparent overlay
* replacement gesture system

Android explicitly provides gesture-dispatch APIs that can cancel gestures already in progress, so those APIs should be outside the MVP architecture. ([Android Developers][2])

### 3. Contextual

Don't simply announce raw Android events.

Convert:

> `TYPE_VIEW_CLICKED`

into:

> “Liked.”

when sufficient context exists.

### 4. Configurable

Different users want radically different amounts of speech.

One user might want:

> “Button. Like.”

Another:

> “Liked.”

Another:

> No speech unless something meaningful changes.

### 5. Cooperative with TalkBack

ActionVoice is **not TalkBack**.

If TalkBack is active:

> **ActionVoice becomes silent.**

It can remain technically enabled in the background if useful, but its speech engine should not produce narration.

This avoids two accessibility services speaking simultaneously.

---

# 4. Target experience

The ideal experience is almost invisible.

The user:

1. touches the screen normally;
2. Android handles the touch normally;
3. the application changes state;
4. Android emits accessibility/UI information;
5. ActionVoice interprets it;
6. ActionVoice speaks a short confirmation.

Conceptually:

```text
User touch
    ↓
Android normal touch pipeline
    ↓
Application
    ↓
UI/state change
    ↓
AccessibilityEvent
    ↓
ActionVoice
    ↓
Event interpretation
    ↓
Speech policy
    ↓
TTS
```

**Nothing should sit between the finger and the application.**

---

# 5. Technical foundation

The application should be implemented as an Android `AccessibilityService`.

Android accessibility services receive system-generated `AccessibilityEvent`s representing things such as focus changes and button clicks, and they can optionally inspect the accessibility node hierarchy of the active window. ([Android Developers][1])

Core components:

```text
AccessibilityService
        │
        ├── Event Collector
        │
        ├── Event Normalizer
        │
        ├── Context Manager
        │
        ├── Action Interpreter
        │
        ├── Speech Policy
        │
        └── TTS Engine
```

---

# 6. Event collection

The service should initially listen to relevant events such as:

* `TYPE_VIEW_CLICKED`
* `TYPE_VIEW_SELECTED`
* `TYPE_VIEW_FOCUSED`
* `TYPE_VIEW_SCROLLED`
* `TYPE_VIEW_TEXT_CHANGED`
* `TYPE_WINDOW_STATE_CHANGED`
* `TYPE_WINDOW_CONTENT_CHANGED`
* `TYPE_NOTIFICATION_STATE_CHANGED` where useful
* accessibility focus changes where relevant

Android's accessibility event system is explicitly designed around these kinds of UI state changes. ([Android Developers][3])

However:

**Do not speak every event.**

Raw Android events are merely input to the interpretation engine.

---

# 7. Event normalization

Different applications may produce wildly different events for essentially the same action.

Therefore introduce a normalized internal event model.

For example:

```kotlin
data class UserAction(
    val type: ActionType,
    val packageName: String?,
    val appName: String?,
    val label: String?,
    val role: String?,
    val value: String?,
    val timestamp: Long,
    val confidence: Float
)
```

Possible `ActionType`s:

```text
CLICK
LONG_CLICK
SCROLL
SELECTION
TOGGLE
TEXT_INPUT
WINDOW_OPENED
WINDOW_CLOSED
TAB_CHANGED
MENU_OPENED
MENU_CLOSED
NAVIGATION
FOCUS_CHANGED
CONTENT_CHANGED
UNKNOWN
```

This abstraction becomes extremely important later.

---

# 8. Action interpretation engine

This is the brain of the product.

Instead of:

> Event → Speech

use:

> Events + UI context + previous state → Action → Speech

Example:

```text
TYPE_VIEW_CLICKED
label = "Like"
class = Button
previous state = unliked

↓

ACTION:
LIKE

↓

speech:
"Liked"
```

Another example:

```text
TYPE_VIEW_CLICKED
label = "Follow"
previous state = Follow

↓

ACTION:
FOLLOW

↓

"Following"
```

And:

```text
TYPE_VIEW_SCROLLED
scroll delta = positive

↓

ACTION:
CONTENT_ADVANCED

↓

"Next item"
```

---

# 9. Context manager

The service needs a small amount of short-term memory.

For example:

```text
Current app
Current window
Previous focused node
Previous clicked node
Previous text
Previous toggle state
Previous scroll position
Last spoken event
Last speech timestamp
```

This allows deduplication.

Without this, the service could say:

> “Like button.”

> “Like.”

> “Liked.”

> “Like button.”

within 500 milliseconds.

The desired result is:

> **“Liked.”**

---

# 10. Speech policy engine

This is where the product becomes genuinely useful.

The speech policy decides:

### Should I speak?

### What should I say?

### How much should I say?

### Should I interrupt current speech?

### Should I merge this with the previous event?

Example configuration:

```text
Speech verbosity

○ Minimal
● Standard
○ Detailed
○ Custom
```

### Minimal

> “Liked.”

### Standard

> “Liked.”

### Detailed

> “Like button. Liked.”

---

# 11. Speech queue

The system needs an intelligent speech queue.

Possible priorities:

```text
CRITICAL
HIGH
NORMAL
LOW
BACKGROUND
```

For example:

> “Battery 5 percent”

should potentially interrupt:

> “Scrolled.”

But:

> “Scrolled.”

should never interrupt:

> “Message received from Rahul.”

Possible policies:

```text
Interrupt
Queue
Replace
Merge
Discard
```

---

# 12. TTS configuration

This should be a major feature.

The user should be able to choose:

### TTS engine

For example:

* Google Speech Services
* Samsung TTS
* eSpeak
* other installed Android TTS engines

The Android platform exposes TTS functionality to applications, so the product should use Android's standard `TextToSpeech` interface rather than embedding a proprietary speech engine into the MVP.

Settings:

```text
TTS Engine
Voice
Language
Speech rate
Pitch
Volume
Audio stream
```

Advanced:

```text
Speech delay
Queue mode
Interrupt policy
Audio ducking
```

---

# 13. TalkBack coexistence

This should be a **first-class architectural component**, not an afterthought.

Startup:

```text
ActionVoice enabled
        ↓
Is TalkBack active?
        │
   ┌────┴────┐
   YES       NO
   ↓          ↓
Silent      Narrate
```

If TalkBack becomes active while ActionVoice is running:

```text
TalkBack detected
        ↓
ActionVoice speech disabled
        ↓
Continue observing if necessary
```

If TalkBack turns off:

```text
TalkBack inactive
        ↓
ActionVoice resumes
```

The user should also have:

```text
[✓] Automatically become silent when TalkBack is active
```

with this enabled by default.

---

# 14. Important distinction: service enabled vs speech enabled

I would **not automatically disable the entire accessibility service** when TalkBack is active.

Instead:

```text
Service:
    ACTIVE

Observation:
    ACTIVE

Interpretation:
    ACTIVE

Speech:
    MUTED
```

Why?

Because the service can maintain state and immediately resume when TalkBack disappears.

It also avoids unnecessary service lifecycle churn.

---

# 15. Touch handling philosophy

This needs to be an explicit **non-functional requirement**.

## ActionVoice MUST NOT:

* intercept touch events
* consume touch events
* dispatch replacement gestures
* simulate user taps
* perform `dispatchGesture()`
* request touch exploration mode
* create a touchable full-screen overlay
* manipulate the user's gesture path

`dispatchGesture()` can cancel gestures already in progress, which is precisely the kind of interaction interference this project is designed to avoid. ([Android Developers][2])

---

# 16. What about detecting actual finger gestures?

This is one of the technically interesting parts.

Modern Android exposes `onMotionEvent()` to accessibility services, but the current Android documentation specifically warns about its interaction with touch exploration and notes that motion-event observation requires configuring motion-event sources. It also says that motion events from configured sources are not sent to the rest of the system. ([Android Developers][2])

Therefore:

**Do not make raw `MotionEvent` interception the foundation of the MVP.**

Instead, MVP should derive actions primarily from:

```text
AccessibilityEvent
+
AccessibilityNodeInfo
+
window state
+
application state transitions
```

This gives us the desired non-invasive behavior.

Later, we can investigate newer Android APIs for passive motion observation on supported versions, but only if they can satisfy the critical requirement:

> **Observing the user's gesture must not steal that gesture from Android or the application.**

---

# 17. Accessibility node intelligence

When an event occurs, ActionVoice can inspect the associated `AccessibilityNodeInfo`.

Useful attributes include:

```text
text
contentDescription
className
role
isClickable
isScrollable
isChecked
isSelected
isEnabled
isPassword
hintText
stateDescription
paneTitle
```

Then construct a semantic representation:

```text
Role: BUTTON
Label: Like
State: Checked
Action: CLICK
```

rather than relying purely on raw event types.

---

# 18. Example interaction pipeline

### User taps a button

```text
Touch
 ↓
Android
 ↓
App
 ↓
TYPE_VIEW_CLICKED
 ↓
Node:
    class = Button
    text = "Follow"
 ↓
Interpreter
 ↓
FOLLOW
 ↓
Speech Policy
 ↓
"Following"
```

---

# 19. Scroll interpretation

Scrolling is harder.

A scroll event doesn't necessarily mean:

> “Next item.”

It might mean:

* next Instagram Reel
* next YouTube video
* more messages
* movement through a web page
* movement inside a settings page
* horizontal carousel movement

Therefore MVP should initially support generic narration:

```text
"Scrolled down"
"Scrolled up"
"More content"
```

Then application-specific semantic adapters can be added later.

---

# 20. Application adapters

This is where the project could eventually become extremely powerful.

Architecture:

```text
Generic Accessibility Engine
             ↓
       App Recognition
             ↓
     ┌───────┼────────┐
     ↓       ↓        ↓
 Instagram YouTube  Browser
 Adapter    Adapter   Adapter
```

An adapter could understand:

```text
Instagram:
swipe → next reel

YouTube:
swipe → next video

Gallery:
swipe → next photo
```

But **MVP should remain app-agnostic**.

---

# 21. Configuration interface

The companion application should have a clean settings interface.

## Main screen

```text
ActionVoice

Status
● Running

TalkBack
○ Automatically mute when TalkBack is active

Speech
Standard

TTS
Google Speech Services

[Configure Speech]

[Configure Apps]

[Advanced]
```

---

# 22. Speech settings

```text
Speech verbosity
    Minimal
    Standard
    Detailed

Speech rate
    ─────────●────

Pitch
    ────────●─────

Volume
    ─────────●────

Queue mode
    Replace
    Queue
    Interrupt

Repeat suppression
    [✓]

Speech delay
    100 ms
```

---

# 23. Per-app settings

This is potentially one of the best features.

```text
Application behavior

Instagram
    Narration: Standard
    Scroll narration: Enabled

YouTube
    Narration: Minimal
    Scroll narration: Enabled

Chrome
    Narration: Detailed

WhatsApp
    Narration: Standard
```

The architecture should support package-specific policies.

---

# 24. User-defined speech rules

Eventually:

```text
WHEN:
    action = TOGGLE
    label = Wi-Fi

SAY:
    "Wi-Fi is now {state}"
```

Or:

```text
WHEN:
    app = Instagram
    action = SCROLL

SAY:
    "Next reel"
```

This could become a lightweight accessibility automation system.

---

# 25. Privacy model

This should be **local-first**.

### MVP should require:

* no account
* no cloud
* no analytics
* no external server
* no internet permission if technically possible
* no recording of screen
* no recording of touch coordinates
* no storage of message contents

Accessibility services can access sensitive UI information, so privacy should be treated as a core product requirement rather than a marketing feature.

---

# 26. Security boundaries

Never speak or log sensitive information unnecessarily.

Examples:

```text
Password field
Credit-card field
OTP field
Banking application
Private notification
```

Potential rule:

```text
if node.isPassword:
    suppress()
```

For sensitive applications, provide:

```text
[✓] Disable narration in sensitive apps
```

---

# 27. Debug mode

For development, add a diagnostic mode.

Example:

```text
EVENT

Package:
com.instagram.android

Type:
TYPE_VIEW_SCROLLED

Source:
RecyclerView

Class:
androidx.recyclerview.widget.RecyclerView

Text:
—

Scrollable:
true

Scroll delta:
+823

Interpretation:
CONTENT_ADVANCED

Speech:
"Next item"
```

This will be incredibly useful while developing adapters.

---

# 28. Accessibility event inspector

I would actually make this a dedicated developer feature.

```text
Live Event Monitor

[Start]

02:31:04
WINDOW_CHANGED
Instagram

02:31:05
VIEW_SCROLLED
RecyclerView

02:31:05
CONTENT_CHANGED

02:31:06
VIEW_CLICKED
Like
```

This allows us to discover how different apps expose their interfaces.

---

# 29. MVP

The first version should be **much smaller than the eventual vision**.

### MVP v0.1

Implement:

* Android AccessibilityService
* service enable/disable
* accessibility event collection
* current package detection
* node inspection
* basic click narration
* basic selection narration
* basic toggle narration
* basic window-change narration
* basic scroll narration
* Android TTS
* TTS engine selection
* speech rate
* pitch
* verbosity
* deduplication
* TalkBack detection/muting
* local-only operation
* debug event log

Do **not** implement:

* gesture injection
* touch exploration
* overlays
* AI/cloud interpretation
* app-specific hacks
* OCR
* screen recording

---

# 30. V0.2

Add:

* context-aware action interpretation
* better scroll detection
* state-change detection
* speech priority
* speech queue
* per-app settings
* customizable verbosity
* configurable suppression rules

---

# 31. V0.3

Add:

* application adapters
* Instagram adapter
* YouTube adapter
* browser adapter
* media-player adapter
* richer action semantics

---

# 32. V1.0

Potentially:

```text
ActionVoice
│
├── Passive Action Narration
├── Context Engine
├── App Adapters
├── Custom Rules
├── TTS Engine Manager
├── Per-App Profiles
├── Developer Inspector
└── Privacy/Safety Layer
```

---

# 33. Example user journeys

### First launch

```text
Welcome to ActionVoice.

ActionVoice provides spoken feedback for
your normal touchscreen interactions.

It does not replace your touch gestures.

[Enable Accessibility Service]
```

Android accessibility settings open.

User enables it.

Then:

```text
ActionVoice is ready.

Try opening an application and interacting
normally with the touchscreen.
```

---

# 34. Example: Settings

User taps Wi-Fi.

ActionVoice:

> “Wi-Fi.”

User taps the switch.

ActionVoice:

> “Wi-Fi on.”

User taps again:

> “Wi-Fi off.”

No gesture modification occurs.

---

# 35. Example: Instagram

User performs a normal swipe.

ActionVoice:

> “Next reel.”

User taps Like.

> “Liked.”

User taps Follow.

> “Following.”

TalkBack remains off.

---

# 36. Example: TalkBack

User turns TalkBack on.

ActionVoice detects:

```text
TalkBack = ACTIVE
```

ActionVoice:

```text
speechEnabled = false
```

TalkBack now owns the spoken accessibility experience.

When TalkBack turns off:

```text
speechEnabled = true
```

ActionVoice resumes.

---

# 37. Architecture

I would structure the codebase roughly like this:

```text
app/
│
├── accessibility/
│   ├── ActionVoiceService
│   ├── EventCollector
│   ├── NodeInspector
│   └── TalkBackDetector
│
├── core/
│   ├── Action
│   ├── ActionType
│   ├── ContextState
│   └── EventNormalizer
│
├── interpreter/
│   ├── ActionInterpreter
│   ├── GenericInterpreter
│   └── AppAdapterManager
│
├── speech/
│   ├── SpeechManager
│   ├── SpeechQueue
│   ├── SpeechPolicy
│   └── TtsEngineManager
│
├── settings/
│   ├── SettingsRepository
│   └── AppProfiles
│
├── ui/
│   ├── MainActivity
│   ├── SettingsActivity
│   └── DebugActivity
│
└── adapters/
    ├── InstagramAdapter
    ├── YouTubeAdapter
    └── BrowserAdapter
```

---

# 38. Key product metric

The most important metric shouldn't be "how many events did we speak?"

It should be:

> **How often did the narration correctly explain the user's action without disrupting their interaction?**

Potential metrics:

### Accuracy

```text
Correct narration / meaningful interactions
```

### Intrusiveness

```text
Unwanted speech / total speech
```

### Latency

```text
Action → speech start
```

### Gesture preservation

```text
User actions disrupted by service
```

The last one should ideally be:

> **0**

---

# 39. Fundamental success criterion

A blind user should be able to forget that ActionVoice is even running.

They should simply feel:

> **“My phone now tells me what my touch did.”**

Not:

> “I'm using another screen reader.”

That's the conceptual distinction that makes this product interesting.

---

# 40. Longer-term possibility

If the passive narration layer works reliably, this could evolve into something much larger:

```text
                 ActionVoice
                      │
       ┌──────────────┼──────────────┐
       │              │              │
   Narration      Context        Automation
       │              │              │
   "Liked"       "Next reel"    Custom actions
       │              │              │
       └──────────────┼──────────────┘
                      │
             Accessibility OS layer
```

The eventual vision could be a **semantic interaction layer for Android**: not replacing TalkBack, but allowing a blind user to retain the directness of ordinary touchscreen interaction while receiving an auditory representation of the consequences of their actions.

And importantly, the Android platform's accessibility architecture already provides the two pieces we need for the initial version: event callbacks and optional access to the active window's accessibility-node tree. ([Android Developers][1])

### One architectural decision I'd lock in immediately

**Do not build the first prototype around detecting raw finger movement.**

Build:

> **Accessibility events → node inspection → state comparison → action interpretation → TTS**

first.

That gets us a genuinely passive prototype. Then we can experimentally investigate Android's newer motion-event APIs on supported Android versions without making touch interception a dependency of the product. Android's current documentation specifically makes the distinction between observing motion events and controlling touchscreen interaction, which is exactly the boundary this product needs to preserve. ([Android Developers][2])

[1]: https://developer.android.com/reference/android/accessibilityservice/AccessibilityService?utm_source=chatgpt.com "AccessibilityService  |  API reference  |  Android Developers"
[2]: https://developer.android.com/reference/android/accessibilityservice/AccessibilityService?authuser=3&utm_source=chatgpt.com "AccessibilityService  |  API reference  |  Android Developers"
[3]: https://developer.android.com/reference/android/view/accessibility/package-summary?utm_source=chatgpt.com "android.view.accessibility  |  API reference  |  Android Developers"
