# SpotHelper + HolyWorld

Fabric 1.16.5. This build keeps the original SpotHelper functions and adds a HolyWorld section to the client GUI.

## GUI

Right Shift opens the custom SpotHelper menu.

The interface uses a dark click-GUI style with a salad/lime-green accent. HolyWorld is split into:

- **Ивенты** — reads cached events from the local HolyWorld Control app. The app refreshes them automatically; the mod also polls the local cache.
- **Шахты** — pressing **Обновить шахты** sends one request to HolyLite through HolyWorld Control. Automatic Telegram polling is not used by the mod.
- **Настройки** — Bridge address/port and event polling options.

The original SpotHelper pages for AutoTool and block ESP remain available under **Модули** and **Настройки ESP**.

## HolyWorld Control

Run the separate `HolyWorldControl.exe` application first. By default it exposes:

`http://127.0.0.1:48765`

The Minecraft mod does not store Telegram sessions and does not ask for the user's phone number. Telegram authentication remains inside HolyWorld Control.

## Build

On Windows with a working Java/Gradle environment:

```bat
gradlew.bat build
```

The resulting mod jar is placed in `build/libs/`.
