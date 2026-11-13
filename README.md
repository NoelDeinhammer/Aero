# Aero

An extremely lightweight background chat app written in **Java** — no external libraries, no database, no accounts.

## Features
- **Runs in the background**: System tray icon named **Aero**. Its menu offers Open chat, Settings, Change name, and Quit.
- **Global keyboard shortcut**: Opens the floating chat window from anywhere. The hotkey works reliably every time; if the primary shortcut is already taken, Aero automatically falls back to `Ctrl + Shift + Space` and then `Ctrl + Shift + K`. Clicking the tray icon also opens the chat.
- **Contact picker & minimal chat**: Click a person and start typing immediately. `Esc` closes the window.
- **Group chats**: Click **+ Gruppe** to create a local group with a name and members. Group messages are sent directly to every online member; recipients automatically create or sync the group. Groups persist across restarts.
- **Notifications**: When a new message arrives, a small floating popup appears at the top center of the screen (not a Windows toast notification). Click it to open the matching chat or group. Popups can be disabled in Settings.
- **Settings**: Display name, notifications, and optional always-on-top mode are available directly in the chat window via the gear icon. By default, the window is **not** permanently on top; the hotkey only raises it briefly.
- **No database / no accounts**: Peer-to-peer communication on the local network (UDP multicast) plus automatic localhost discovery for easy testing on a single PC. You can also connect to a friend's IP manually.

## Settings location
All settings are saved automatically under `%APPDATA%\Aero`:
- `settings.properties` for the normal instance
- `instance-<name>.properties` when started with `--name <name>`

These files contain the display name, notification and always-on-top settings, the stable peer ID, and local groups. Because the peer ID is persisted, groups remain valid after a restart.

## Open & run in IntelliJ
1. Open **IntelliJ IDEA** → **Open** → select the `Aero` project folder.
2. If IntelliJ asks for an SDK, select your installed Java JDK.
3. Open `src/com/quickchat/Main.java` and click the green **Run ▶** button.

Alternatively, use the command line (PowerShell):

```powershell
javac -encoding UTF-8 -d out (Get-ChildItem src -Recurse -Filter *.java).FullName
java -cp out com.quickchat.Main
```

## Test with 2 people on one PC
1. Start the first instance normally (it automatically uses your Windows name).
2. In IntelliJ, go to **Run → Edit Configurations...**:
   - Enable **Allow multiple instances** under *Modify options*.
   - Or create a second run configuration with **Program arguments**: `--name Anna`.
3. Start the second instance — both instances discover each other automatically within about 2 seconds.
4. Press `Ctrl + Alt + Space`, choose a person, and send a message.

Profiles (`--name Anna`) have separate settings files and stable peer IDs. This lets you test multiple instances on one PC without them overwriting each other's configuration.

## Using groups
1. Click **+ Gruppe** in the chat window.
2. Enter a group name and select at least one person who is online.
3. Click **Erstellen** (or press `Enter`) to create the group and open the group chat directly.
4. New group messages appear for recipients as a notification and as a group entry with an unread counter.
5. Right-click a group in the chat window to access **Gruppe löschen**.
