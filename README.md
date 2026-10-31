# Sonnenwecker

*Stand: 31.10.2026, 08:16 Uhr*

Android-Wecker-App. Jeder Wecker löst denselben Ablauf aus:

- **Sonnenaufgang:** Die App stellt die Hue-Aufwachen-Automation **„Sonnenwecker“** auf deine Weckzeit. Die Hue Bridge lässt die Iris dann einen Sonnenaufgang simulieren, sodass es zur Weckzeit hell ist.
- **Begrüßung:** Zur Weckzeit spricht der JBL Link die Begrüßung des Weckers, z. B. „Guten Morgen, Christina! Es ist Montag, der 2. November, 6 Uhr 45. Heute wird es bis zu 12 Grad und bewölkt.“ An den gewählten Tagen folgt das Wetter zwischen 12 und 14 Uhr.
- **Radio:** Danach läuft SR3.
- **Backup:** Tippst du nicht innerhalb von 5 Minuten auf „Ich bin wach“, vibriert das Handy.

## Wecker

Wie in der Uhr-App: Mit **+** legst du einen Wecker an, wählst die Uhrzeit und die Wochentage und schaltest ihn ein oder aus. Ohne Wochentage klingelt ein Wecker einmal und schaltet sich danach aus. Für andere Uhrzeiten legst du einfach einen weiteren Wecker an.

Den Wecker in der Uhr-App brauchst du nicht mehr.

## Begrüßung bearbeiten

Jeder Wecker hat seine eigene Begrüßung: Wecker aufklappen → **Begrüßung**.

- Den Text schreibst du frei. Über die Knöpfe fügst du **Bausteine** an der Cursor-Stelle ein: Name, Wochentag, Datum, Uhrzeit, Wetter heute, Mittagswetter. Bausteine erscheinen als Pillen und werden nur als Ganzes gelöscht.
- **Mittagswetter** wird nur an den Tagen gesprochen, die du darunter auswählst.
- **Vorschau:** zeigt, wie die Begrüßung am nächsten Wecktag klingt – mit der aktuellen Wettervorhersage. „Auf dem Handy anhören“ spricht sie über das Handy.
- **Standardtext wiederherstellen** ist jederzeit möglich; direkt danach kannst du es rückgängig machen.
- Ist der Text leer, gibt es keine Begrüßung, nur Radio.

Standardtext: „Guten Morgen, {Name}! Es ist {Wochentag}, der {Datum}, {Uhrzeit}. {Wetter heute} {Mittagswetter}“

## Einrichten

1. **Hue-App:** Unter Automationen → Aufwachen eine Automation mit dem Namen **„Sonnenwecker“** anlegen (Iris, Stil Sonnenaufgang). Uhrzeit und Tage stellt die Sonnenwecker-App.
2. **Sonnenwecker → Einstellungen:** Bridge koppeln, Lautsprecher wählen, Name und Ort fürs Wetter festlegen.
3. **Sonnenwecker → Status und Einrichtung:** zeigt, ob alles bereit ist, und hilft bei fehlenden Berechtigungen.

Änderungen werden sofort gespeichert.

## Gut zu wissen

- **Weck-Bildschirm:** erscheint zur Weckzeit auch auf dem Sperrbildschirm – mit Wetter, „Radio aus“ und „Ich bin wach“. Er ist immer dunkel, damit er nicht blendet.
- **Neustart in der Nacht:** Die Wecker funktionieren auch nach einem Neustart, bevor das Handy entsperrt wurde.
- **Dauer des Sonnenaufgangs:** in den Einstellungen wählbar (10, 15, 20 oder 30 Minuten); die App überträgt sie in die Hue-Automation.
- **Begrüßung testen** (Einstellungen): spielt die Begrüßung des nächsten Weckers über den Lautsprecher.
- **Backup-Vibration:** läuft höchstens 30 Minuten.
- **Protokoll:** In den Einstellungen unter „Hilfe“ steht, was die App wann gemacht hat.

## App installieren

Auf dem Handy herunterladen und öffnen:
https://github.com/christinakrahljob-bit/Sonnenwecker/releases/download/neueste-version/Sonnenwecker.apk

Jede Änderung am Code erzeugt automatisch eine neue Version, die sich über die alte installiert.
