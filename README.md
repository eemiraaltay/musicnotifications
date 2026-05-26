# Music Notifications

Music Notifications is a client-side Fabric mod by aaltay. It reads the music currently playing on Windows and shows it inside Minecraft as a toast notification.

## Features

- Shows track title and artist from Windows media controls.
- Uses Minecraft-style toast notifications with a music disc icon.
- Adds notification options to the sound settings screen.
- Lets you enable or disable notifications, hide the disc icon, choose toast position, and change display duration.
- Suppresses the default toast sound for these music notifications.

## Requirements

- Minecraft 26.1.2
- Fabric Loader 0.19.1 or newer
- Fabric API
- Java 25
- Windows, for SMTC media metadata

## Build

```powershell
.\gradlew.bat build
```

The built jar will be in:

```text
build/libs/musicnotifications-1.26.1.2.jar
```

## Notes

The mod uses Windows System Media Transport Controls. Apps such as Spotify, Chrome, Edge, and other media players may expose different metadata quality depending on how they publish currently playing media to Windows.

## License

This project uses the MIT license.
