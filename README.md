# Aero

An extremely lightweight background LAN chat app written in **Java** with swing. No libraries, accounts or else.

## Features
- **LAN Chat** - Local area network chat for messaging without needing much ram like other chat clients.
- **Instant Chat** - Download, open, chat. No friend requests or other things. Just begin chatting.
- **Group setup** - Make groups with certain people. Password protected.
- **Change Language** - Choose between German/English.
- **Disable Notifications** - To completely hide the Client you can turn of Notifications.
- **On-screen-notification** - Easy to see on-screen Popup
- **Connect with IP** - (EXPERIMENTAL) You can connect to people with the IP but its still experimental.
- **Auto delete Messages** - Auto deletes all sent Messages after closing the app. (There will be a setting to toggle auto delete on/off)

## Settings location
All settings are saved automatically under `%APPDATA%\Aero`:
- `settings.properties` for the normal instance

These files contain the display name, notification and always-on-top settings, the stable peer ID, and local groups.

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
