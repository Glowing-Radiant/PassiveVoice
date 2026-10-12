# PassiveVoice

An Android accessibility service that speaks what your touch just did, without changing how you touch the phone.

Screen readers take over touch: you explore first, then double-tap. PassiveVoice leaves touch alone. It watches what changed after an ordinary tap, scroll or keystroke and confirms it out loud: "Liked", "Scrolled down", "Wi-Fi on", "Next reel, by …". It is built for people who can use the touchscreen directly but want spoken confirmation.

## Features

- Taps, switches, tabs, selections, screens and menus, read with the state they ended in ("Follow" is heard as "Following")
- Scroll gestures with direction, list edges and page changes
- Typing echo: each key as you type it, and the whole word when you type a space
- Richer narration in Instagram and YouTube
- Per-app profiles: narration on or off, verbosity, scroll narration
- Full TTS control: any installed engine, rate, pitch, volume boost, and the audio channel (accessibility volume by default)
- Mutes itself while a screen reader is active (TalkBack, Talk Forward, Jieshuo, BackTalk)
- Local only: no account, no network, no analytics. Password fields are never spoken, and typed text is never logged.

## Install

1. Download the latest APK from [Releases](../../releases) and open it on your phone.
2. Turn on PassiveVoice in Settings → Accessibility.

Requires Android 8.0 or later.

## Building

Open `app/` in Android Studio, or from the command line:

```bash
cd app
./gradlew testDebugUnitTest assembleDebug
```

Release builds are signed when a keystore is configured, either through `app/keystore.properties` (gitignored, with `storeFile`, `storePassword`, `keyAlias` and `keyPassword`) or the `PV_KEYSTORE_FILE`, `PV_KEYSTORE_PASSWORD`, `PV_KEY_ALIAS` and `PV_KEY_PASSWORD` environment variables. Without one, `assembleRelease` produces an unsigned APK.

## Releasing

Push a version tag. The [release workflow](.github/workflows/release.yml) runs the unit tests, builds a signed APK and publishes a GitHub release. Its notes list every commit since the previous tag, led by the tag's message when the tag is annotated:

```bash
git tag -a v0.5.0 -m "Headline for the release" -m "Optional longer description."
git push origin v0.5.0
```

Tags with a suffix, such as `v0.5.0-beta.1`, are published as pre-releases. The workflow reads the signing key from the `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` repository secrets.

See [prd.md](prd.md) for the full product design.
