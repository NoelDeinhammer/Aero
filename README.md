# Aero

Eine extrem leichtgewichtige Hintergrund-Chat-App in **Java** (ohne externe Libraries, ohne Datenbank, ohne Accounts).

## Features
- **Läuft im Hintergrund**: System-Tray-Icon mit dem Namen **Aero**. Das Menü bietet Chat öffnen, Einstellungen, Name ändern und Beenden.
- **Globaler Keyboard-Shortcut**: Öffnet das schwebende Chat-Fenster von überall. Der Hotkey funktioniert beliebig oft; ein zweiter Start versucht automatisch die Fallbacks `Ctrl + Shift + Space` und `Ctrl + Shift + K`. Ein Klick auf das Tray-Icon öffnet den Chat ebenfalls.
- **Personenauswahl & Minimal-Chat**: Person anklicken und sofort schreiben. `Esc` schließt das Fenster wieder.
- **Gruppenchats**: Über **+ Gruppe** eine lokale Gruppe mit Namen und Mitgliedern anlegen. Gruppennachrichten werden direkt an alle online befindlichen Mitglieder gesendet; Empfänger legen die Gruppe automatisch an bzw. gleichen sie ab. Gruppen bleiben nach einem Neustart erhalten.
- **Benachrichtigungen**: Bei neuen Nachrichten erscheint oben mittig ein kleines schwebendes Popup (nicht die Windows-Toast-Benachrichtigung). Ein Klick darauf öffnet den passenden Chat bzw. die Gruppe. In den Einstellungen lassen sich die Popups abschalten.
- **Einstellungen**: Anzeigename, Benachrichtigungen und optionales „immer im Vordergrund“ direkt im Chat-Fenster über das Zahnrad. Standardmäßig ist das Fenster **nicht** dauerhaft im Vordergrund; der Hotkey holt es nur kurz nach vorne.
- **Ohne Datenbank / ohne Konten**: Peer-to-Peer im lokalen Netzwerk (UDP Multicast) plus automatische Localhost-Erkennung zum einfachen Testen auf einem PC. Optional lässt sich eine Freund-IP manuell verbinden.

## Speicherort der Einstellungen
Alle Einstellungen werden automatisch unter `%APPDATA%\Aero` gespeichert:
- `settings.properties` für die normale Instanz
- `instance-<Name>.properties` bei Start mit `--name <Name>`

Darin liegen Anzeigename, Benachrichtigungs- und Vordergrund-Einstellung, die stabile Peer-ID und die lokalen Gruppen. Durch die gespeicherte Peer-ID bleiben Gruppen auch nach einem Neustart gültig.

## In IntelliJ öffnen & starten
1. **IntelliJ IDEA** öffnen → **Open** → den Projektordner `Aero` auswählen.
2. Falls IntelliJ nach einem SDK fragt, oben rechts das installierte Java-JDK auswählen.
3. `src/com/quickchat/Main.java` öffnen und auf das grüne **Run ▶** Symbol klicken.

Alternativ per Kommandozeile (PowerShell):

```powershell
javac -encoding UTF-8 -d out (Get-ChildItem src -Recurse -Filter *.java).FullName
java -cp out com.quickchat.Main
```

## Auf einem PC mit 2 Personen testen
1. Erste Instanz normal starten (nimmt automatisch deinen Windows-Namen).
2. In IntelliJ unter **Run → Edit Configurations...**:
   - **Allow multiple instances** aktivieren (unter *Modify options*).
   - Oder eine zweite Run-Configuration anlegen mit **Program arguments**: `--name Anna`.
3. Zweite Instanz starten – beide Instanzen finden sich innerhalb von ~2 Sekunden automatisch.
4. `Ctrl + Alt + Space` drücken, Person auswählen und Nachricht senden.

Die Profile (`--name Anna`) haben getrennte Einstellungsdateien und stabile Peer-IDs. So lassen sich mehrere Instanzen auf einem PC testen, ohne dass sie sich gegenseitig die Konfiguration überschreiben.

## Gruppen verwenden
1. Im Chat-Fenster auf **+ Gruppe** klicken.
2. Gruppennamen eingeben und mindestens eine online befindliche Person auswählen.
3. **Erstellen** (oder `Enter`) legt die Gruppe an und öffnet direkt den Gruppenchat.
4. Neue Gruppennachrichten erscheinen bei den Empfängern als Benachrichtigung und als Gruppeneintrag mit Ungelesen-Zähler.
5. Rechtsklick auf eine Gruppe im Chat-Fenster bietet **Gruppe löschen**.
