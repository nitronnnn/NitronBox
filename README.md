# NitronBox Liquid Glass

Native Jetpack Compose demonstration of a reusable `LiquidGlassCard` built with:

- `com.github.skydoves:cloudy:0.2.0`
- `dev.chrisbanes.haze:haze:0.7.0`
- Coil Compose

The background list uses `Modifier.haze(state)`. The floating card uses `Modifier.hazeChild(state)` and a dedicated decorative layer with `Modifier.cloudy(radius = 25)`, keeping card content sharp.

> Cloudy 0.2.0 is published as `com.github.skydoves:cloudy:0.2.0`. The requested `io.github.skydoves` coordinate does not exist in Maven Central.
