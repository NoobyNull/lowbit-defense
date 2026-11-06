# Lowbit Defense

Low-bit tower defense for Android. Your tower sits in the centre and enemies home in on it from every direction.

## Combat

- The tower auto-fires at the nearest enemy.
- Tap anywhere during a wave to fire a straight shot in that direction. There is no aim assist, so it can miss.
- With BOOST bought, press and hold to charge. Letting go fires at the power reached, along the line to your finger.
- A loss sends you back to the last cleared boss. Upgrades, cash and gems are kept.

## Economy

- **Cash** buys the roots: Weapons, Boost, Ship, Protect.
- **Gems** buy every other level in the Weapons, Power and Skills trees.
- **Exchange:** cash to gems depends on your player level. Level 1 is $100 per gem, level 50 is 1:1, level 100 is 1 cash for 100 gems. Log-linear between those anchors.
- **Auto-trade** converts cash to gems at each wave end, keeping enough cash for roots not bought yet.
- **Growth:** gem prices, XP needs and enemy HP step by `v + v/3 + random(below v/3)`, which is 1/3 of the way to double plus a random amount below that.
- **Waves:** the ship count is rolled at the start of a run, then grows. Every 5th wave adds a boss with 60% of the whole wave's HP.

## Build

```bash
./build.sh
```

Runs the rule tests, then produces `build/LowbitDefense.apk`. No Gradle is needed. It uses the Android SDK build tools at `$ANDROID_SDK_ROOT` (default `/opt/android-sdk`). The signing key is created once at `~/.android/lowbit-defense.jks`. Back it up, because updates must be signed with the same key.

## Layout

- `src/.../Game.java`: rules, skill tree, economy, waves, combat. No Android types.
- `src/.../MainActivity.java`: low-res pixel rendering, touch input, save/load.
- `test/.../GameTest.java`: headless checks run by `build.sh`.
