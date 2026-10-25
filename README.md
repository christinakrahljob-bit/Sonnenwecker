# Sonnenwecker

*Stand: 25.10.2026, 21:30 Uhr*

Android-App, die deinen normalen Handywecker erkennt und dazu:

- **15 Minuten vorher** die Hue Iris einen Sonnenaufgang simulieren lässt
- **zur Weckzeit** auf dem JBL Link begrüßt: „Guten Morgen, Christina! Es ist 6 Uhr 30. Heute wird es bis zu 12 Grad und bewölkt.“
- **montags und mittwochs** zusätzlich das Wetter zwischen 12 und 14 Uhr ansagt
- danach **SR3** spielt

Du stellst nur den Wecker in der Uhr-App. Alles andere passiert automatisch.

## Voraussetzungen

- Handy, Hue Bridge und JBL Link im selben WLAN
- Das Handy ist nachts zu Hause und im WLAN
- Deutsche Stimme für die Sprachausgabe ist installiert (Android-Einstellungen → Sprachausgabe)

## App bauen und installieren

1. Projekt in ein GitHub-Repository laden (Branch `main`).
2. GitHub baut die App automatisch (Reiter **Actions**, dauert ca. 5 Minuten).
3. Auf dem Handy im Repository unter **Releases → neueste-version** die Datei `Sonnenwecker.apk` herunterladen und öffnen.
4. Android fragt einmalig, ob Installationen aus dieser Quelle erlaubt sind – zustimmen.

Jede Änderung am Code erzeugt automatisch eine neue Version, die sich über die alte installiert.

## Einrichten in der App

1. **Akku-Optimierung ausschalten** (Knopf ganz oben) – sonst stoppt Android die App nachts.
2. **Hue:** „Bridge automatisch suchen“ → runden Knopf auf der Bridge drücken → „Mit Bridge koppeln“ → Iris auswählen.
3. **Lautsprecher:** „Lautsprecher im WLAN suchen“ → JBL auswählen.
4. **Speichern.**
5. Mit „Sonnenaufgang testen“ und „Begrüßung + Radio jetzt testen“ prüfen, ob alles klappt.

Oben in der App steht immer, für wann Sonnenaufgang und Begrüßung geplant sind. Ganz unten steht ein Protokoll, falls etwas nicht klappt.

## Gut zu wissen

- **Schlummern:** Wird der Wecker innerhalb einer Stunde nach der Begrüßung erneut gestellt (Schlummern), gibt es keine zweite Begrüßung.
- **Sonnenaufgang:** Kann die Lampe den Hue-Sonnenaufgangseffekt, wird dieser genutzt. Sonst dimmt die App langsam von tiefem Orange auf warmes Weiß hoch.
- **Wetter:** kommt von Open-Meteo (kostenlos, ohne Konto). Ort in der App einstellbar.
- **Radio:** Die SR3-Adresse ist in der App änderbar, falls der Sender seine Stream-Adresse ändert.
