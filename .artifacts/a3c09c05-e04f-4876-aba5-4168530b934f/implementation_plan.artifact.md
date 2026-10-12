# Improve Internal Player UI and UX

Enhance the internal video player with a modern Material 3 design, improved animations, and functional gestures (volume/brightness).

## User Review Required

> [!IMPORTANT]
> The implementation will add gesture-based controls (volume/brightness) which might conflict with system gestures if not handled carefully. I will ensure they only trigger on vertical swipes in specific screen areas.

## Proposed Changes

### [UI Components]

#### [MODIFY] [PlayerScreen.kt](file:///D:/AndroidStudioProjects/MyApplication/app/src/main/java/com/example/videodownloader/ui/screens/PlayerScreen.kt)
- **Redesign UI:** Replace basic black gradients with Material 3 `Surface` overlays using `surfaceColorAtElevation` and better contrast.
- **Animations:**
    - Use `AnimatedContent` for the Play/Pause button for smooth icon transitions.
    - Improve control bar enter/exit animations.
    - Add visual feedback for double-tap seeking.
- **Gestures:**
    - Implement vertical swipe on the left half for brightness control.
    - Implement vertical swipe on the right half for volume control.
    - Add visual indicators for volume/brightness changes.
- **Functionality:**
    - Add a "Resize Mode" toggle to switch between Fit, Fill, and Zoom.
    - Ensure the player respects the system's dynamic colors.

## Verification Plan

### Manual Verification
1.  Open a video in the internal player.
2.  Verify the new Material 3 control bars.
3.  Test Play/Pause animation.
4.  Test vertical swipes on both sides:
    - Left side: Check if brightness changes (and indicator shows).
    - Right side: Check if volume changes (and indicator shows).
5.  Test double-tap to seek and verify visual feedback.
6.  Test the Resize Mode toggle.
