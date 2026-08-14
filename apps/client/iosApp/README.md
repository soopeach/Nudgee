# Nudgee iOS host

This is the Xcode host application for the shared Compose Multiplatform UI in `composeApp`.

## Run in Xcode

1. Open `iosApp.xcodeproj` in Xcode.
2. Select an iPhone Simulator.
3. Set a Development Team under Signing & Capabilities if Xcode asks for one.
4. Run the `Nudgee` scheme.

The Xcode build phase calls `../gradlew :composeApp:embedAndSignAppleFrameworkForXcode` to build and embed the shared `ComposeApp` framework.
