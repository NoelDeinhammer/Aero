# Aero

An extremely lightweight background LAN chat app written in **Java** with swing. No libraries, accounts or else.

## Features
- **LAN Chat** - Local area network chat for messaging without needing much ram like other chat clients.
- **Instant Chat** - Download, open, chat. No friend requests or other things. Just begin chatting.
- **Group setup** - Make groups with certain people. Password protected.
- **Change Language** - Choose between German/English.
- **Disable Notifications** - To completely hide the Client you can turn of Notifications.
- **On-screen-notification** - Easy to see on-screen Popup
- **Keep Messages** - Optionally keep sent and received messages after quitting Aero. Turn it on or off in Settings.

## Settings location
All settings are saved automatically under `%APPDATA%\Aero`:
- `settings.properties` for the normal instance
- `settings-messages.properties` for saved chat history

These files contain the display name, notification, always-on-top and keep-messages settings, the stable peer ID, and local groups.

The history file is only written while **Keep messages** is enabled. Turning the option off deletes it again, so messages disappear after closing Aero as before. Peer messages are stored as plain text; password-protected group messages stay encrypted.

## Install
Download Aero-portable.zip from the releases tab, extract it and run Aero.exe \
Windows will throw a security risk because this app has no certificate.
   - Click `more information` and `run anyway`


## Build from source
1. Download the repository as ZIP and extract it.
2. Double-click `build.cmd`.
   - Or open PowerShell in the folder and run `.\build.ps1`.
3. A JDK 17+ is required. If no JDK is found, the script downloads Temurin JDK 21 automatically (one-time, about 190 MB).
4. The build creates:
   - `output\Aero\Aero.exe`
   - `output\Aero-portable.zip`

If Windows blocks the downloaded files, right-click the ZIP before extracting, choose **Properties**, and enable **Unblock**.

## Using groups
1. Click **+ Group** in the chat window.
2. Enter a group name and select at least one person who is online.
3. Click **Create** (or press `Enter`) to create the group and open the group chat directly.
4. New group messages appear for recipients as a notification and as a group entry with an unread counter.
5. Right-click a group in the chat window to access **Delete Group**.
   - Groups stay until deleted.
