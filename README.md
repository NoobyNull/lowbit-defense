# Lowbit Defense

Low-bit tower defense for Android. Your ship sits at the end of the lane and fights off waves automatically.

## Economy

- **Cash** buys each module's first level (Ship, Protection, Weapons, Special).
- **Gems** buy every upgrade after that. Each level costs twice the last.
- **Exchange:** cash to gems depends on your player level. Level 1 is $100 per gem, level 50 is 1:1, level 100 is 1 cash for 100 gems. Log-linear between those anchors.
- **Auto-trade** converts cash to gems at each wave end, keeping enough cash for the cheapest unbought base module.
- Waves: the ship count is random each wave. Every 5th wave ends with a boss whose HP is 60% of the whole wave's HP.

## Build

```bash
./build.sh
```

Runs the rule tests, then produces `build/LowbitDefense.apk`. No Gradle is needed. It uses the Android SDK build tools at `$ANDROID_SDK_ROOT` (default `/opt/android-sdk`). The signing key is created once at `~/.android/lowbit-defense.jks`. Back it up, because updates must be signed with the same key.

## Layout

- `src/.../Game.java`: rules, economy, waves, combat. No Android types.
- `src/.../MainActivity.java`: canvas drawing, touch input, save/load.
- `test/.../GameTest.java`: headless checks run by `build.sh`.

## Not yet

Radius damage, missed shots, and player aim.
