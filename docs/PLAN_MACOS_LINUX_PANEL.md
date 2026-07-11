# Plan: transparent TrayApp popup on macOS and Linux

Status as of 2026-07-11. Windows is DONE; this document is the hand-off for
porting the same UX to macOS (and deciding what to do on Linux).

## Where things stand

`TrayApp` (Tao-only, no AWT) routes per platform in
`src/jvmMain/kotlin/dev/nucleusframework/composenativetray/tray/api/TrayApp.kt`:

- **Windows → `TrayAppImplPanel`**: renders into `TaoStandalonePopup`
  (public composable in Nucleus `decorated-window-tao`), a top-level,
  ownerless, topmost, non-activating native panel with per-pixel
  transparency. Nothing appears in taskbar/Alt-Tab/task view. Slide+fade
  animations composite over the desktop.
- **macOS/Linux → `TrayAppImplWindow`**: an opaque `DecoratedWindow`
  (undecorated, focus-based dismissal). Animations run over the opaque
  window background — ugly, but currently enabled by default anyway
  (user decision: same defaults on all platforms until the port lands).

Nucleus source lives at `C:\Users\Elie\IdeaProjects\ComposeDeskKit`,
published to mavenLocal as `2.0.0-tao-local` — publish commands, module
list and the GITHUB_REF trick are in the auto-memory
(`nucleus-local-publish`). Windows pitfalls (focus, AltGr, KEY_TYPED,
keyLocation, WH_MOUSE_LL, GraalVM metadata) are in the auto-memory
(`trayapp-transparent-popup`) — read it before touching input code.

## Windows architecture to mirror (reference implementation)

All in `ComposeDeskKit/decorated-window-tao`:

| Piece | File | Role |
|---|---|---|
| `TaoStandalonePopup` | `src/main/kotlin/.../tao/TaoStandalonePopup.kt` | Public composable: visible/position/size/focusable/onOutsideClick/key handlers. All host mutations in `LaunchedEffect` (never in `remember{}` — nested composition crash). Bridges outer CompositionLocals into the panel scene. |
| `TaoStandalonePopupHost` | `src/main/kotlin/.../tao/render/TaoStandalonePopupHost.kt` | Owns the native panel + its own `CanvasLayersComposeScene`. On-demand rendering: `BroadcastFrameClock` + `FlushingDispatcher`, invalidate → `scheduleRender()` → `TaoMainDispatcher`, paced at 60 fps. Input callbacks → scene; keys via `dispatchNativeKeyEvent` (TaoSyntheticKey.kt). |
| Native panel | `src/main/native/windows/nucleus_tao_windows_popup.c` | Ownerless `WS_POPUP` + NOACTIVATE/TOOLWINDOW/NOREDIRECTIONBITMAP/TOPMOST, DComp alpha, `WH_MOUSE_LL` outside-click, keyboard forwarding + `takeKeyboardFocus()`. |
| Headless GL bootstrap | `nucleus_tao_gl.c` → `nativeEnsureHeadlessContext()` | Creates the shared process EGL context without any window host — required because TrayApp may show a popup before any window exists. |
| Smoke test | `src/test/kotlin/.../StandalonePanelNativeSmokeTest.kt` | Validates load → headless ctx → panel → makeCurrent → Skia DirectContext without an event loop. Do the macOS equivalent first. |

## macOS port (the real work)

Goal: `TaoStandalonePopupHost` equivalent backed by an `NSPanel`, then flip
the router in `TrayApp.kt` so macOS uses `TrayAppImplPanel`.

Existing bricks in `decorated-window-tao` (owner-based popups already work
on macOS — reuse, don't reinvent):

- `nucleus_tao_macos_popup.m` (or similarly named) behind
  `PopupNativeBridge` (the non-Windows twin of `PopupNativeBridgeWindows`):
  NSPanel + transparent CAMetalLayer used by `TaoPopupSceneLayer`.
- `TaoPopupSceneLayer.kt` — owner-based macOS popup layer; its event
  callback already uses `dispatchNativeKeyEvent`.
- Rendering on macOS is Metal (Skia `DirectContext` per host, render
  thread affine — see `TaoComposeSceneHost.kt`), NOT the EGL path.

Steps:

1. **Native**: extend the macOS popup .m file to allow an ownerless panel
   (`parentNsView == 0`): `NSPanel` with `.nonactivatingPanel` style,
   `level = .statusBar` (topmost above normal apps), `isOpaque = false`,
   clear background, `hidesOnDeactivate = false`, NOT added to any window
   parent. Screen coordinates (beware: AppKit Y axis is bottom-up —
   convert from the top-left dp coords the Kotlin side uses).
2. **Outside click**: use a global `NSEvent.addGlobalMonitorForEvents`
   (mouseDown/rightMouseDown) + local monitor for clicks inside the app —
   equivalent of the Windows WH_MOUSE_LL split. The owner-based popups'
   existing local monitor is not enough for clicks on other apps.
3. **Keyboard**: `.nonactivatingPanel` + `canBecomeKeyWindow = true`
   override lets the panel take key focus without activating the app.
   `makeKeyWindow` on show and on click. Forward `keyDown/keyUp` to the
   same event-callback wire format (type, vkCode, codePoint, modifiers) —
   `dispatchNativeKeyEvent` then handles KEY_TYPED + keyLocation for free.
4. **Host Kotlin**: `TaoStandalonePopupHostMac` mirroring the Windows
   host but on the Metal render path (no `nativeEnsureHeadlessContext`;
   create the Metal device/queue standalone — check how
   `TaoComposeSceneHost` bootstraps Metal and factor if needed).
   Render on-demand; CAMetalLayer presents are cheap, keep the 60 fps
   pacing pattern.
5. **Composable**: extend `TaoStandalonePopup` to route per platform
   (currently early-returns unless Windows).
6. **Smoke test** JUnit (guarded on `os.name` = mac) before any UI test.
7. **TrayApp**: route macOS to `TrayAppImplPanel` in the router, delete
   the mac branch of `TrayAppImplWindow` once validated. Check
   `TrayScreenGeometry`/`TrayPosition` for the menu-bar coordinate space
   (tray icon is top of screen on macOS; `defaultVerticalOffset` is +5).
8. **GraalVM**: add reachability-metadata.json entries for any new named
   callback classes (JNI-called classes must be named, not anonymous).

Validation loop (the user runs the demo themselves — do NOT launch it):
rebuild natives → smoke test → `publishToMavenLocal` (verify the jar
really contains the fresh dylib, md5 vs `src/main/resources/nucleus/native/…`)
→ recompile ComposeNativeTray → hand off. Never compile while a demo JVM
is running.

## Linux (decide, then maybe implement)

Transparency requires a compositing WM + ARGB visual; there is no DComp/
CAMetalLayer equivalent that works everywhere (X11 vs Wayland, GNOME vs
KDE vs bare WMs). Options:

- **A (pragmatic, recommended default)**: keep the opaque
  `TrayAppImplWindow`. Accept animations over the window background, or
  set Linux defaults back to `None` (one-line change in the
  `defaultTrayApp*Transition` vals).
- **B (best-effort)**: GTK layer — an undecorated, override-redirect
  GTK window with RGBA visual when a compositor is present
  (`gdk_screen_is_composited`), falling back to A otherwise. Sizeable
  native work; only worth it if users ask.

Outside-click on Wayland cannot use global pointer grabs from a regular
client — focus-loss dismissal (current behavior) is the only reliable
signal. Keep it.

## Also pending (unrelated to the port)

- README still documents the pre-Tao API; rewrite around
  `nucleusApplication` + `NucleusApplicationScope.TrayApp`.
- No IME composition in the Windows panel (WM_CHAR/dead keys/CJK); direct
  Latin input only. Port `startInputMethod` plumbing if needed.
- `TaoWindow.setInvisibleHelperStyle()` and the `nativePopupLayers` flag
  in Nucleus are leftovers from the abandoned anchor-window design; safe
  to remove after checking for external users.
