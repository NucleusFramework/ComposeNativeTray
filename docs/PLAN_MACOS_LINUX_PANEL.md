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

## Linux (option B chosen — raw X11/XWayland panel, IMPLEMENTED)

Status 2026-07-12: macOS is DONE (Nucleus PR #302, `TaoStandalonePopupHostMac`
+ `popup_panel.m`; TrayApp already routes macOS to `TrayAppImplPanel`).
Nucleus repo moved to `~/IdeaProjects/Nucleus`. Linux option B implemented on
Nucleus branch `feat/standalone-popup-panel-linux` (commit 1f037863): native
module + host + routing + smoke test (passes on GNOME Wayland via XWayland)
+ GraalVM metadata + CI. Published locally as `2.0.0-tao-local`;
ComposeNativeTray routes Linux → `TrayAppImplPanel` gated on
`isTaoStandalonePopupAvailable()`. AWAITING: user demo validation on
GNOME Wayland (topmost stacking above Wayland windows, position vs tray
click coords, keyboard into text fields, outside-click dismissal), then
Nucleus PR + published alpha + drop the mavenLocal pin.

### Decision: raw X11 window, NOT a GTK window

GDK's backend is process-wide (native Wayland on Wayland sessions), so a
GTK popup window would only work by forcing the whole app onto XWayland
(`NUCLEUS_TAO_LINUX_RENDERER=x11`). Instead the panel is a **raw X11
override-redirect ARGB32 window on its own `XOpenDisplay` connection** —
an independent X client that works even while the app itself is a native
Wayland client, via XWayland (present on effectively all desktops).
Feasibility was validated on GNOME Wayland (Ubuntu, Mutter): OR window
maps and moves at exact global coords, ARGB visual + desktop-GL 3.3 EGL
context all work through XWayland.

Native Wayland (no XWayland/DISPLAY) stays unsupported: no layer-shell in
vendored Tao, `gtk_window_move` is a no-op on xdg-toplevels, `keep_above`
ignored. In that case the panel reports unavailable and TrayApp falls back
to the opaque `TrayAppImplWindow`.

### Architecture (mirrors Windows, reuses the Linux EGL bridge)

- **Visual selection**: query EGL first (alpha=8 desktop-GL configs →
  `EGL_NATIVE_VISUAL_ID`), create the window with exactly that visual —
  guarantees `NativeTaoEglBridge.nativeAttachX11` matches directly (no
  child-window fallback, alpha preserved).
- **Rendering**: reuse `nucleus_tao_egl.c` per-attachment API
  (`nativeAttachX11`/`nativeMakeCurrent`/`nativePresent`/`nativeResize` +
  `nativeGetProcAddrFunctionPointer`) — Linux convention is one EGL context
  per surface, no headless bootstrap needed. `eglSwapInterval(0)` +
  the 60 fps pacer pattern from the Windows host (don't block the Tao
  main thread on vsync).
- **Threading**: two X connections. Command connection owned by the Tao
  main thread (create/move/map/cursor/focus + EGL). Event connection owned
  by a dedicated per-panel thread (`XSelectInput` on the panel XID works
  cross-connection); quit via ClientMessage. No `XInitThreads` dependency.
- **Input**: Button/Motion/scroll(buttons 4-7) → `TaoNativeWireFormat`
  pointer wire; keys send the raw **X keysym** as vkCode + Unicode
  codePoint; a new `linuxNativeKeyToAwt` in `dispatchNativeKeyEvent`
  translates keysym → AWT VK (same pattern as `macNativeKeyToAwt`).
- **Keyboard focus**: `XSetInputFocus(RevertToParent)` on click while
  focusable (Windows `takeKeyboardFocus` equivalent). If some WMs don't
  deliver keys to OR windows, escalate to `XGrabKeyboard` later.
- **Outside-click**: XI2 raw ButtonPress on the root window (the X11
  analog of `WH_MOUSE_LL`; multiple clients allowed, no grab, doesn't
  consume). Fully global on X11 sessions; under XWayland only fires while
  X11 surfaces have input — acceptable because the tray-icon toggle and
  focus-loss cover the rest.
- **Scale**: `Xft.dpi`-derived (X clients live in the X coordinate space,
  which under XWayland is logical — GDK's Wayland scale would mis-size the
  panel). Fractional/HiDPI XWayland caveats accepted for v1.

New files: `nucleus_tao_linux_popup.c` (→ `libnucleus_tao_linux_popup.so`,
dlopen libX11/libXi only, linked `-ldl` like siblings),
`PopupNativeBridgeLinux.kt`, `render/TaoStandalonePopupHostLinux.kt`,
`render/TaoKeyLinux.kt`, smoke test guarded on Linux + `DISPLAY`. Plus:
route Linux in `TaoStandalonePopup.kt`, a public availability check for
TrayApp's router, `linux/build.sh` step, GraalVM reachability metadata,
CI `build-natives.yaml` + verify arrays in consumer workflows.

## Also pending (unrelated to the port)

- README still documents the pre-Tao API; rewrite around
  `nucleusApplication` + `NucleusApplicationScope.TrayApp`.
- No IME composition in the Windows panel (WM_CHAR/dead keys/CJK); direct
  Latin input only. Port `startInputMethod` plumbing if needed.
- `TaoWindow.setInvisibleHelperStyle()` and the `nativePopupLayers` flag
  in Nucleus are leftovers from the abandoned anchor-window design; safe
  to remove after checking for external users.
