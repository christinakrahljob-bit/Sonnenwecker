# Sonnenwecker

*Stand: 28.10.2026, 08:00 Uhr*

Android-App, die deinen normalen Handywecker erkennt und dazu:

- die Hue-Aufwachen-Automation **„Sonnenwecker“** auf deine Weckzeit stellt – die Hue Bridge lässt die Iris dann einen Sonnenaufgang simulieren, sodass es zur Weckzeit hell ist
- **zur Weckzeit** auf dem JBL Link begrüßt: „Guten Morgen, Christina! Es ist 6 Uhr 30. Heute wird es bis zu 12 Grad und bewölkt.“
- **montags und mittwochs** zusätzlich das Wetter zwischen 12 und 14 Uhr ansagt
- danach **SR3** spielt

Du stellst nur den Wecker in der Uhr-App. Alles andere passiert automatisch.

## Voraussetzungen

- Handy, Hue Bridge und JBL Link im selben WLAN
- Für die Begrüßung ist das Handy morgens zu Hause und im WLAN
- Deutsche Stimme für die Sprachausgabe ist installiert (Android-Einstellungen → Sprachausgabe)

## App installieren

Auf dem Handy herunterladen und öffnen:
https://github.com/christinakrahljob-bit/Sonnenwecker/releases/download/neueste-version/Sonnenwecker.apk

Jede Änderung am Code erzeugt automatisch eine neue Version, die sich über die alte installiert.

## Einrichten

1. **Hue-App:** Unter Automationen → Aufwachen eine Automation mit dem Namen **„Sonnenwecker“** anlegen: Iris, Stil Sonnenaufgang, Dauer nach Wunsch. Uhrzeit und Tage sind egal – die stellt die Sonnenwecker-App.
2. **Sonnenwecker-App:** Akku-Optimierung ausschalten (Knopf ganz oben).
3. **Bridge:** „Bridge automatisch suchen“ → runden Knopf auf der Bridge drücken → „Mit Bridge koppeln“. Darunter erscheint, auf wann die Automation gestellt ist.
4. **Lautsprecher:** „Lautsprecher im WLAN suchen“ → JBL auswählen → **Speichern**.
5. Mit „Sonnenaufgang testen“ und „Begrüßung + Radio jetzt testen“ prüfen, ob alles klappt.

Ganz unten in der App steht ein Protokoll, falls etwas nicht klappt.

## Gut zu wissen

- **Wann es hell wird:** Die Uhrzeit der Hue-Automation ist die Weckzeit. Der Sonnenaufgang beginnt um die in der Hue-App eingestellte Dauer früher.
- **Ohne Wecker:** Ist kein Wecker gestellt, schaltet die App die Automation aus.
- **Erinnerungen anderer Apps** (z. B. TickTick) werden ignoriert – nur Wecker der Uhr-App zählen.
- **Schlummern:** Wird der Wecker innerhalb einer Stunde nach der Begrüßung erneut gestellt (Schlummern), gibt es keine zweite Begrüßung und keinen zweiten Sonnenaufgang.
- **Test:** Der Test stellt die Automation so, dass der Sonnenaufgang in 1–2 Minuten beginnt, und stellt sie danach automatisch auf deinen Wecker zurück.
- **Wetter:** kommt von Open-Meteo (kostenlos, ohne Konto). Ort in der App einstellbar.
- **Radio:** Die SR3-Adresse ist in der App änderbar, falls der Sender seine Stream-Adresse ändert.
