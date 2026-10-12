# Fix Player Animation Glitch

The user reported buggy animations when showing/hiding video player controls, especially when the system bars (status and navigation bars) are toggled simultaneously. This is likely caused by layout jumps as the root `Scaffold` and internal components react to changing window insets.

## User Review Required

> [!IMPORTANT]
> The video player will be moved outside of the main `Scaffold` to ensure it is truly full-screen and unaffected by the navigation bar or other UI elements' padding. This is standard practice for immersive video players.

## Proposed Changes

### Core UI Navigation

#### [MODIFY] [MainActivity.kt](file:///D:/AndroidStudioProjects/MyApplication/app/src/main/java/com/example/videodownloader/MainActivity.kt)
- Move `Screen.Player` logic outside of the `Scaffold` in `AppNavigation`. This prevents the player from being resized or shifted when the `Scaffold` reacts to system bar visibility changes (via `paddingValues`).

### Video Player Screen

#### [MODIFY] [PlayerScreen.kt](file:///D:/AndroidStudioProjects/MyApplication/app/src/main/java/com/example/videodownloader/ui/screens/PlayerScreen.kt)
- Improve the sync between system bar visibility and Compose animations.
- Replace instant inset-based spacers with more stable alternatives or animate the inset changes if possible.
- Ensure the `AndroidView` (ExoPlayer) is not affected by any top/bottom padding changes during animation.
- Synchronize `ControlsAlpha`, `TopBarOffset`, and `BottomBarOffset` animation specs to be identical for a smoother feel.

## Verification Plan

### Manual Verification
1. Open the video player.
2. Toggle the controls by tapping.
3. Observe the animation in both Portrait and Landscape modes.
4. Verify that the system bars appear/disappear smoothly without causing the video or the controls to "jump" or flicker.
5. Check if the auto-hide functionality still works correctly.
