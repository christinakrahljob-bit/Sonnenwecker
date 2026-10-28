# Sonnenwecker

*Stand: 28.10.2026, 09:11 Uhr*

Android-Wecker-App. Jeder Wecker löst denselben Ablauf aus:

- **Sonnenaufgang:** Die App stellt die Hue-Aufwachen-Automation **„Sonnenwecker“** auf deine Weckzeit. Die Hue Bridge lässt die Iris dann einen Sonnenaufgang simulieren, sodass es zur Weckzeit hell ist.
- **Begrüßung:** Zur Weckzeit sagt der JBL Link: „Guten Morgen, Christina! Es ist 6 Uhr 45. Heute wird es bis zu 12 Grad und bewölkt.“ An den gewählten Tagen (z. B. Mo und Mi) folgt das Wetter zwischen 12 und 14 Uhr.
- **Radio:** Danach läuft SR3.
- **Backup:** Tippst du nicht innerhalb von 5 Minuten auf „Ich bin wach“, vibriert das Handy.

## Wecker

Wie in der Uhr-App: Mit **+** legst du einen Wecker an, wählst die Uhrzeit und die Wochentage und schaltest ihn ein oder aus. Ohne Wochentage klingelt ein Wecker einmal und schaltet sich danach aus. Für andere Uhrzeiten legst du einfach einen weiteren Wecker an.

Den Wecker in der Uhr-App brauchst du nicht mehr.

## Einrichten

1. **Hue-App:** Unter Automationen → Aufwachen eine Automation mit dem Namen **„Sonnenwecker“** anlegen (Iris, Stil Sonnenaufgang). Uhrzeit und Tage stellt die Sonnenwecker-App.
2. **Sonnenwecker → Einstellungen:** Bridge koppeln, Lautsprecher wählen, Ort fürs Wetter festlegen.
3. **Sonnenwecker → Status und Einrichtung:** zeigt, ob alles bereit ist, und hilft bei fehlenden Berechtigungen.

Änderungen werden sofort gespeichert.

## Gut zu wissen

- **Weck-Bildschirm:** erscheint zur Weckzeit auch auf dem Sperrbildschirm – mit Wetter, „Radio aus“ und „Ich bin wach“. Er ist immer dunkel, damit er nicht blendet.
- **Neustart in der Nacht:** Die Wecker funktionieren auch nach einem Neustart, bevor das Handy entsperrt wurde.
- **Dauer des Sonnenaufgangs:** in den Einstellungen wählbar (10, 15, 20 oder 30 Minuten); die App überträgt sie in die Hue-Automation.
- **Backup-Vibration:** läuft höchstens 30 Minuten.
- **Protokoll:** In den Einstellungen unter „Hilfe“ steht, was die App wann gemacht hat.

## App installieren

Auf dem Handy herunterladen und öffnen:
https://github.com/christinakrahljob-bit/Sonnenwecker/releases/download/neueste-version/Sonnenwecker.apk

Jede Änderung am Code erzeugt automatisch eine neue Version, die sich über die alte installiert.
