# 🛠️ Compose Native Tray

<p align="center">
  <img src="screenshots/logo.png" alt="logo">
</p>
<p align="center">
  <a href="https://central.sonatype.com/artifact/dev.nucleusframework/composenativetray"><img src="https://img.shields.io/maven-central/v/dev.nucleusframework/composenativetray" alt="Maven Central"></a>
  <a href="https://opensource.org/licenses/MIT"><img src="https://img.shields.io/badge/License-MIT-yellow.svg" alt="License: MIT"></a>
  <a href="https://github.com/NucleusFramework/ComposeNativeTray"><img src="https://img.shields.io/badge/Platform-Linux%20%7C%20Windows%20%7C%20macOS-lightgrey.svg" alt="Platform"></a>
  <a href="https://github.com/NucleusFramework/ComposeNativeTray/commits/main"><img src="https://img.shields.io/github/last-commit/NucleusFramework/ComposeNativeTray" alt="Last Commit"></a>
  <a href="https://nucleusframework.github.io/ComposeNativeTray/"><img src="https://img.shields.io/badge/docs-Dokka-blue.svg" alt="Documentation"></a>
  <a href="https://github.com/NucleusFramework/ComposeNativeTray/issues"><img src="https://img.shields.io/badge/contributions-welcome-brightgreen.svg" alt="Contributions Welcome"></a>
  <a href="https://github.com/NucleusFramework/ComposeNativeTray/actions"><img src="https://img.shields.io/badge/build-passing-brightgreen.svg" alt="Build Passing"></a>
</p>

## 📖 Introduction

**Compose Native Tray** is a modern Kotlin library for creating applications with system tray icons, offering native support for Linux, Windows, and macOS. It uses an intuitive Kotlin DSL syntax and fixes issues with the standard Compose for Desktop solution.

## ✨ Features

- Cross-platform support for Linux, Windows, and macOS.
- DSL-style syntax to define tray menus with ease.
- Supports standard items, submenus, dividers, and checkable items.
- Ability to enable/disable menu items dynamically.
- Corrects issues with the [Compose for Desktop tray](https://github.com/JetBrains/compose-multiplatform/blob/master/tutorials/Tray_Notifications_MenuBar_new), particularly HDPI support on Windows and Linux.
- Improves the appearance of the tray on Linux, which previously resembled Windows 95.
- Adds support for checkable items, dividers, and submenus, including nested submenus.
- Supports primary action for Windows, macOS, and Linux.
    - On Windows and macOS, the primary action is triggered by a left-click on the tray icon.
    - On Linux, on GNOME the primary action is triggered by a double left-click on the tray icon, while on the majority of other environments, primarily KDE Plasma, it is triggered by a single left-click, similar to Windows and macOS.
- **Single Instance Management**: Ensures that only one instance of the application can run at a time and allows restoring focus to the running instance when another instance is attempted.
- **Tray Position Detection**: Allows determining the position of the system tray, which helps in positioning related windows appropriately.
- **Compose Recomposition Support**: The tray supports Compose recomposition, making it possible to dynamically show or hide the tray icon, for example:

<p align="center">
  <img src="screenshots/demo.gif" alt="demo">
</p>

## 📑 Table of Contents

- [📖 Introduction](#-introduction)
- [🎯 Why Compose Native Tray?](#-why-compose-native-tray)
- [📸 Preview](#-preview)
- [⚡ Installation](#-installation)
- [🚀 Quick Start](#-quick-start)
- [📚 Usage Guide](#-usage-guide)
    - [🎨 Creating the System Tray Icon](#-creating-the-system-tray-icon)
    - [🖱️ Primary Action](#️-primary-action)
    - [📋 Building the Menu](#-building-the-menu)
    - [Icons with painterResource](#icons-with-painterresource)
    - [New: Icons with DrawableResource](#new-icons-with-drawableresource-in-menu-items)
- [🔧 Advanced Features](#-advanced-features)
    - [🔄 Fully Reactive System Menu](#-fully-reactive-system-menu)
    - [🔑 Single Instance Management](#-single-instance-management)
    - [📍 Position Detection](#-position-detection)
    - [🌓 Dark Mode Detection](#-dark-mode-detection)
    - [🎨 Icon Rendering Customization](#-icon-rendering-customization)
- [⚠️ Platform-Specific Notes](#️-platform-specific-notes)
    - [Icon Limitations](#icon-limitations)
    - [Theme Behavior](#theme-behavior)
    - [ProGuard / R8](#proguard--r8)
- [🧪 TrayApp (Alpha)](#-trayapp-alpha)
- [📱 Apps Using Compose Native Tray](#-apps-using-compose-native-tray)
- [📄 License](#-license)
- [🤝 Contribution](#-contribution)
- [👨‍💻 Author](#-author)


## 🎯 Why Compose Native Tray?

This library was created to solve several limitations of the standard Compose for Desktop solution:
- ✅ **Improved HDPI support** on Windows and Linux
- ✅ **Modern appearance** on Linux (no more Windows 95 look!)
- ✅ **Extended features**: checkable items, nested submenus, separators
- ✅ **Native primary action**: left-click on Windows/macOS, single-click (KDE) or double-click (GNOME) on Linux
- ✅ **Full Compose recomposition support**: fully reactive icon and menu, allowing dynamic updates of items, their states, and visibility

## 📸 Preview

<table>
  <tr>
    <td><img src="screenshots/windows.png" alt="Windows" /><br /><center>Windows</center></td>
    <td><img src="screenshots/mac.png" alt="macOS" /><br /><center>macOS</center></td>
  </tr>
  <tr>
    <td><img src="screenshots/gnome.png" alt="Ubuntu GNOME" /><br /><center>Ubuntu GNOME</center></td>
    <td><img src="screenshots/kde.png" alt="Ubuntu KDE" /><br /><center>Ubuntu KDE</center></td>
  </tr>
</table>

## ⚡ Installation

Add the dependency to your `build.gradle.kts`:

```kotlin
dependencies {
  implementation("dev.nucleusframework:composenativetray:<version>")
}
```

## 🚀 Quick Start

Minimal example to create a system tray icon with menu:

```kotlin
application {
  Tray(
    icon = Icons.Default.Favorite,
    tooltip = "My Application"
  ) {
    Item(label = "Settings") {
      println("Settings opened")
    }
    
    Divider()
    
    Item(label = "Exit") {
      exitProcess(0)
    }
  }
}
```

> **💡 Recommendation**: It is highly recommended to check out the demo examples in the project's `demo` directory. These examples showcase various implementation patterns and features that will help you better understand how to use the library effectively.
>
> Notable demos:
> - DemoWithDrawableResources.kt – shows using DrawableResource directly for Tray and menu icons
> - PainterResourceWorkaroundDemo.kt – demonstrates the painterResource variable workaround
> - DemoWithoutContextMenu.kt – minimalist tray with primary action only

## 📚 Usage Guide

### 🎨 Creating the System Tray Icon

#### New: Using a DrawableResource directly
```kotlin
Tray(
  icon = Res.drawable.myIcon,  // org.jetbrains.compose.resources.DrawableResource
  tooltip = "My Application"
) { /* menu */ }
```

> Requires compose.components.resources in your project. In this library it's already included; in your app add:
> implementation(compose.components.resources)

#### Option 1: Using an ImageVector
```kotlin
Tray(
  icon = Icons.Default.Favorite,
  tint = null,  // Optional: if null, the tint automatically adapts (white in dark mode, black in light mode) according to the isMenuBarInDarkMode() API
  tooltip = "My Application"
) { /* menu */ }
```

#### Option 2: Using a Painter
```kotlin
Tray(
  icon = painterResource(Res.drawable.myIcon),
  tooltip = "My Application"
) { /* menu */ }
```

#### Option 3: Using a Custom Composable
```kotlin
Tray(
  iconContent = {
    Canvas(modifier = Modifier.fillMaxSize()) { // Important to use fillMaxSize()!
      // A simple red circle as an icon
      drawCircle(
        color = Color.Red,
        radius = size.minDimension / 2,
        center = center
      )
    }
  },
  tooltip = "My Application"
) { /* menu */ }
```

> **⚠️ Important**: Always use `Modifier.fillMaxSize()` with `iconContent` for proper icon rendering.

#### Option 4: Platform-Specific Icons

This approach allows respecting the design conventions of each platform:
- **Windows**: Traditionally uses colored icons in the system tray
- **macOS/Linux**: Prefer monochrome icons that automatically adapt to the theme

```kotlin
val windowsIcon = painterResource(Res.drawable.myIcon)
val macLinuxIcon = Icons.Default.Favorite

Tray(
  windowsIcon = windowsIcon,      // Windows: full colored icon
  macLinuxIcon = macLinuxIcon,    // macOS/Linux: adaptive icon
  tooltip = "My Application"
) { /* menu */ }
```

> **💡 Note**: If no tint is specified, ImageVectors are automatically tinted white (dark mode) or black (light mode) based on the theme.

### 🖱️ Primary Action

Define an action for clicking on the icon. The behavior varies by platform:
- **Windows/macOS**: Left-click on the icon (native implementation for macOS)
- **Linux**: Single-click on KDE or double-click on GNOME (implementation via DBus)

```kotlin
Tray(
  icon = Icons.Default.Favorite,
  tooltip = "My Application",
  primaryAction = {
    println("Icon clicked!")
    // Open a window, display a menu, etc.
  }
) { /* menu */ }
```

### 📋 Building the Menu

> **Important note**: It's not mandatory to create a context menu. You can use only an icon in the tray with a primary action (left-click) to restore your application, as shown in the `DemoWithoutContextMenu.kt` example. This minimalist approach is perfect for simple applications that only need a restore function.

The menu uses an intuitive DSL syntax with several types of elements:

```kotlin
Tray(/* configuration */) {
  // Simple item with icon
  Item(label = "Open", icon = Icons.Default.OpenInNew) {
    // Click action
  }
  
  // Item with custom icon via iconContent
  Item(
    label = "Custom",
    iconContent = {
      Icon(
        Icons.Default.Star,
        contentDescription = null,
        tint = Color.Yellow,
        modifier = Modifier.fillMaxSize() // Important!
      )
    }
  ) { }
  
  // Checkable item
  CheckableItem(
    label = "Dark Mode",
    icon = Icons.Default.DarkMode,
    checked = isDarkMode,
    onCheckedChange = { isDarkMode = it }
  )
  
  // Submenu
  SubMenu(label = "Options", icon = Icons.Default.Settings) {
    Item(label = "Option 1") { }
    Item(label = "Option 2") { }
    
    // Nested submenus supported!
    SubMenu(label = "Advanced") {
      Item(label = "Advanced Option") { }
    }
  }
  
  // Visual separator
  Divider()
  
  // Disabled item - the isEnabled property controls whether the item can be clicked
  Item(label = "Version 1.0.0", isEnabled = false)
  
  // Enabled item (isEnabled is true by default)
  Item(label = "Help", isEnabled = true) {
    // This action will be executed when clicked
  }
  
  // Exit properly
  Item(label = "Exit") {
    dispose()  // Removes the system tray icon
    exitProcess(0)
  }
}
```

### Icons with painterResource

### New: Icons with DrawableResource in menu items
You can now pass DrawableResource directly to menu builders:

```kotlin
Tray(icon = Res.drawable.app_icon, tooltip = "App") {
  SubMenu(label = "With icons", icon = Res.drawable.gear) {
    Item(label = "Action 1", icon = Res.drawable.star) { /* ... */ }
    Item(label = "Action 2", icon = Res.drawable.star) { /* ... */ }
  }

  Divider()

  CheckableItem(
    label = "Enabled",
    icon = Res.drawable.check,
    checked = true,
    onCheckedChange = { /* ... */ }
  )
}
```

See demo/DemoWithDrawableResources.kt for a complete example.
When using `painterResource` with menu items, declare it in the composable context:

```kotlin
application {
  val advancedIcon = painterResource(Res.drawable.advanced) // ✅ Correct
  
  Tray(/* config */) {
    SubMenu(
      label = "Advanced",
      icon = advancedIcon  // Use the variable
    ) { /* items */ }
  }
}
```

## 🔧 Advanced Features

### 🔄 Fully Reactive System Menu

The library supports Compose recomposition for all aspects of the system menu:

```kotlin
// Example 1: Dynamic display/hiding of the icon
var isWindowVisible by remember { mutableStateOf(true) }

// The icon only appears when the window is hidden
if (!isWindowVisible) {
  Tray(
    icon = Icons.Default.Favorite,
    tooltip = "Click to restore"
  ) {
    Item(label = "Restore") {
      isWindowVisible = true
    }
  }
}

// Example 2: Fully reactive menu
application {
  var darkMode by remember { mutableStateOf(false) }
  var showAdvancedOptions by remember { mutableStateOf(false) }
  var notificationsEnabled by remember { mutableStateOf(true) }
  var isConfigAvailable by remember { mutableStateOf(false) }

  Tray(
    // The icon changes based on the mode
    icon = if (darkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
    tooltip = "My Application"
  ) {
    // Item with reactive label and icon
    Item(
      label = if (darkMode) "Switch to Light Mode" else "Switch to Dark Mode",
      icon = if (darkMode) Icons.Default.LightMode else Icons.Default.DarkMode
    ) {
      darkMode = !darkMode
    }

    // Reactive checkable item
    CheckableItem(
      label = "Notifications",
      checked = notificationsEnabled,
      onCheckedChange = { notificationsEnabled = it }
    )

    // Conditional display of items
    if (showAdvancedOptions) {
      Divider()

      SubMenu(label = "Advanced Options") {
        // Item with dynamically changing isEnabled property
        Item(
          label = "Configuration", 
          isEnabled = isConfigAvailable
        ) { /* action */ }
        
        // This item enables the Configuration option when clicked
        Item(label = "Check Configuration Availability") { 
          isConfigAvailable = true 
        }
        
        Item(label = "Diagnostics") { /* action */ }
      }
    }

    Divider()

    // Visibility control
    Item(
      label = if (showAdvancedOptions) "Hide Advanced Options" else "Show Advanced Options"
    ) {
      showAdvancedOptions = !showAdvancedOptions
    }
  }
}
```

All menu properties (icon, labels, states, item visibility) are reactive and update automatically when application states change, without requiring manual recreation of the menu.

### 🔑 Single Instance Management

Prevent multiple instances of your application:

The single instance manager combined with the primary action (left-click) is particularly useful for restoring a minimized application in the tray rather than opening a new instance. This improves the user experience by:
- Avoiding resource duplication and confusion with multiple windows
- Preserving the current state of the application during restoration
- Offering behavior similar to native system applications

Implementation example with `SingleInstanceManager` (provided by Nucleus):

```kotlin
import dev.nucleusframework.core.runtime.SingleInstanceManager

var isWindowVisible by remember { mutableStateOf(true) }

val isSingleInstance = SingleInstanceManager.isSingleInstance(
  onRestoreRequest = {
    isWindowVisible = true  // Restore the existing window
  }
)

if (!isSingleInstance) {
  exitApplication()
  return@application
}
```

#### Passing data to the main instance

In some cases, you may want to pass some data to the main instance, e.g. pass a deeplink,
that new instance got in the arguments of the `main` function from OS.

For this purpose you can use optional `onRestoreFileCreated` handler to write required data to the special file,
that will be later accessible to read in the `onRestoreRequest` handler of the main instance.

Both handlers have the `Path` as a receiver, so you can do any read/write operations on it.

```kotlin
SingleInstanceManager.isSingleInstance(
    onRestoreFileCreated = {
        args.firstOrNull()?.let(::writeText)
    },
    onRestoreRequest = {
        log("Restored with arg: '${readText()}'")
        // restore window/etc.
    }
)
```

#### Custom Configuration

For finer control, configure the `SingleInstanceManager`:

```kotlin
SingleInstanceManager.configuration = Configuration(
  lockFilesDir = Paths.get("path/to/your/app/data/dir/single_instance_manager"),
  appIdentifier = "app_id"
)
```

This allows limiting the scope of the single instance to a specific directory or identifying different versions of your application.

### 📍 Position Detection

Precisely position your windows relative to the system tray icon:

```kotlin
val windowWidth = 800
val windowHeight = 600
val windowPosition = getTrayWindowPosition(windowWidth, windowHeight)

Window(
  state = rememberWindowState(
    width = windowWidth.dp,
    height = windowHeight.dp,
    position = windowPosition
  )
) { /* content */ }
```

**Implementation Details:**
- **Windows**: Uses the Windows native API to get the exact position
- **macOS**: Uses the Cocoa API for the position in the menu bar
- **Linux**: Captures coordinates when clicking on the icon

The window is automatically horizontally centered on the icon and vertically positioned based on whether the system tray is at the top or bottom of the screen.

### 🌓 Dark Mode Detection

Automatically adapt your icons to the theme:

```kotlin
val isMenuBarDark = isMenuBarInDarkMode()

Tray(
  iconContent = {
    Icon(
      Icons.Default.Favorite,
      contentDescription = "",
      tint = if (isMenuBarDark) Color.White else Color.Black,
      modifier = Modifier.fillMaxSize()
    )
  },
  tooltip = "My Application"
) { /* menu */ }
```

**Platform Behavior:**
- **macOS**: The menu bar depends on the wallpaper, not the system theme
- **Windows**: Follows the system theme
- **Linux**: GNOME/XFCE/CINNAMON/MATE always dark, KDE follows the theme

> **💡 macOS Note**: The system tray icon follows the menu bar color (based on the wallpaper), but the menu item icons follow the system theme.

### 🎨 Icon Rendering Customization

Two options for customizing rendering:

```kotlin
// Option 1: Optimized for the current OS
Tray(
  icon = Icons.Default.Favorite,
  iconRenderProperties = IconRenderProperties.forCurrentOperatingSystem(
    sceneWidth = 192,    // Compose scene width
    sceneHeight = 192,   // Compose scene height
    density = Density(2f) // Rendering density
  )
) { /* menu */ }

// Option 2: Without forced scaling
Tray(
  icon = Icons.Default.Favorite,
  iconRenderProperties = IconRenderProperties.withoutScalingAndAliasing(
    sceneWidth = 192,
    sceneHeight = 192,
    density = Density(2f)
  )
) { /* menu */ }
```

By default, icons are optimized by OS: 32x32px (Windows), 44x44px (macOS), 24x24px (Linux).

## ⚠️ Platform-Specific Notes

### Icon Limitations
- **GNOME**: Icons don't display in submenus
- **Windows**: Checkable items with icons don't display the check indicator

### Theme Behavior
- **macOS**: The menu bar color depends on the wallpaper, not the system theme
- **Windows**: Follows the system theme
- **Linux**: Varies by desktop environment (GNOME/KDE/etc.)

### ProGuard / R8

When building a release package (e.g. via `packageReleaseUberJarForCurrentOS`), you need to add ProGuard rules to keep JNA and library classes since this library relies on reflection. Without these rules, the tray icon may render incorrectly (semi-transparent background, broken click actions, wrong tooltip).

Add the following to your ProGuard rules file:

```
-keep class com.sun.jna.** { *; }
-keep class dev.nucleusframework.composenativetray.** { *; }
```

# 🧪 TrayApp (Alpha)

> **Status: Alpha** — The core API is functional on Windows, macOS, and Linux, but breaking changes may still occur. Feedback and bug reports are welcome!

`TrayApp` gives your desktop app a **system‑tray/menu‑bar icon** and a **tiny popup window** for quick actions. It's perfect for quick toggles, mini dashboards, and "control center" UIs.

**Works on Windows, macOS, and Linux.** Smooth fade animations, smart positioning near the tray, and a simple API so you stay productive.

---

## Why you’ll like it

* **One‑click popup** anchored to the tray/menu bar
* **Auto‑dismiss** on outside click (or **manual** if you prefer)
* **State preserved**: toggling visibility doesn’t remount your UI
* **Easy sizing** with `setWindowSize(...)`
* **Tray menu builder** for quick actions
* **Theming**: transparent/undecorated styles for a modern look

---

## Quick Start (minimal)

```kotlin
@OptIn(ExperimentalTrayAppApi::class)
application {
    val trayAppState = rememberTrayAppState(
        initialWindowSize = DpSize(300.dp, 420.dp),
        initiallyVisible = true // default is false
        // initialDismissMode defaults to TrayWindowDismissMode.AUTO
    )

    TrayApp(
        state = trayAppState,
        icon = Icons.Default.Dashboard,   // required (or Painter / platform-specific overloads)
        tooltip = "My Tray App",          // required

        // Optional visual controls (defaults shown below)
        transparent = true,               // default = true
        undecorated = true,               // default = true
        resizable = false,                // default = false
        windowsTitle = "My Tray Popup",   // default = "" — recommended (esp. on Linux & when undecorated=false)
        windowIcon = null,                // default = null — set your app icon; important on Linux & when undecorated=false

        menu = {                          // optional (default = null)
            Item("Toggle popup") { trayAppState.toggle() }
            Divider()
            Item("Quit") { exitApplication() }
        }
    ) {
        // Your Compose UI (DialogWindowScope receiver)
        MaterialTheme {
            Text("Quick Settings")
            Button(onClick = { trayAppState.hide() }) { Text("Close") }
        }
    }
}
```

---

## Common recipes

### Show / Hide / Toggle

```kotlin
trayAppState.show()
trayAppState.hide()
trayAppState.toggle()
```

### Resize the popup

```kotlin
trayAppState.setWindowSize(400.dp, 600.dp)
// or
trayAppState.setWindowSize(DpSize(250.dp, 350.dp))
```

### Dismiss mode

```kotlin
// AUTO (default): closes when user clicks outside or focus is lost
val state = rememberTrayAppState(initialDismissMode = TrayWindowDismissMode.AUTO)

// MANUAL: you decide when to hide
LaunchedEffect(Unit) {
    state.setDismissMode(TrayWindowDismissMode.MANUAL)
}
```

### Tray menu (compact)

```kotlin
TrayApp(
    state = trayAppState,
    icon = Icons.Default.Settings,
    tooltip = "Quick Settings",
    menu = {
        val isVisible by trayAppState.isVisible.collectAsState()
        Item(if (isVisible) "Hide" else "Show") { trayAppState.toggle() }
        SubMenu("Size") {
            Item("250×350") { trayAppState.setWindowSize(250.dp, 350.dp) }
            Item("350×500") { trayAppState.setWindowSize(350.dp, 500.dp) }
            Item("450×600") { trayAppState.setWindowSize(450.dp, 600.dp) }
        }
    }
) {
    // your content
}
```

---

## Tips

* **Title & icon matter:** set `windowsTitle` and `windowIcon`. Even with undecorated UIs, Linux desktop environments often show a dock/taskbar entry; providing a title/icon prevents generic placeholders and improves discoverability.

---

## 📱 Apps Using Compose Native Tray

| App | Description |
|-----|-------------|
| [AB Download Manager](https://github.com/amir1376/ab-download-manager) | A desktop download manager |
| [RIFT Intel Fusion Tool](https://riftforeve.online) | A tool for EVE Online |

> If your app uses Compose Native Tray, feel free to open a PR to add it here!

## 📄 License

This library is licensed under the MIT License. The Linux module uses Apache 2.0
## 🤝 Contribution

Contributions are welcome! Feel free to:
- Report bugs via issues
- Propose new features
- Submit pull requests
- Share your projects using this library

## 👨‍💻 Author

Developed and maintained by **Elie Gambache** with the goal of providing a modern, cross-platform solution for system tray icons in Kotlin.
