# OneKeyDTap – Minecraft Java 1.21.11 / Fabric (Quellcode-Prototyp)

**Wichtig:** Dies ist ein **nicht kompiliertes und nicht im Spiel getestetes** Quellprojekt. Eine fertige `.jar` liegt absichtlich nicht bei: In der Erstellungsumgebung fehlen Gradle sowie der Netzwerkzugriff auf die benötigten Minecraft-/Fabric-Abhängigkeiten. Die Funktion ist ein experimenteller Makro-Ansatz, kein garantierter D-Tap.

## Geplante Funktion

- Taste **R** (unter Minecraft > Optionen > Steuerung änderbar)
- Fadenkreuz muss auf einen gegnerischen Spieler innerhalb von 4,5 Blöcken zeigen
- Sucht ein Schwert, Obsidian und End Crystals in den neun Hotbar-Slots
- Greift den anvisierten Spieler mit dem Schwert an
- Nutzt vorhandenes Obsidian unter dem Ziel, sonst versucht es einen vorausliegenden Platz zu belegen
- Platziert und greift ein Crystal an, wartet ca. 10 Ticks, platziert und greift das zweite Crystal an
- Beendet bei fehlenden Items, missglückter Platzierung oder Zeitüberschreitung
- Zweites Mal R während Ablauf = abbrechen; ursprünglicher Hotbar-Slot wird zurückgewählt

WICHTIG: Die Vorhersage ist stark vereinfacht. Server-Ping, Lage des Gegners, Block-/Entity-Kollisionen, Schadensticker und Serverregeln beeinflussen die Funktion. Die erste Explosion und der Schwerttreffer müssen im richtigen Damage-Window liegen. 10 Ticks sind nur ein Ausgangswert.

## Kompilieren auf dem Mac

1. Java **JDK 21** und **Gradle** installieren, z. B. mit Homebrew: `brew install openjdk@21 gradle`.
2. Dieses ZIP entpacken, Terminal im Ordner öffnen.
3. `gradle build` ausführen (alternativ `build-mac.command` im Terminal ausführen). Gradle lädt beim ersten Mal Minecraft und Fabric-Abhängigkeiten über das Internet nach. Ein JDK 21 muss für Gradle verfügbar sein.
4. Wenn der Build erfolgreich ist, befindet sich die nutzbare Fabric-`.jar` im Ordner `build/libs` (ohne `-sources` und ohne `-dev`).
5. Zusammen mit Fabric Loader 0.18.4+ und Fabric API für **1.21.11** ins Minecraft-`mods`-Verzeichnis legen.

Die Gradle-Plug-in-Version in `build.gradle` verwendet Loom 1.14-SNAPSHOT; neuere kompatible Remap-Versionen können bei Bedarf stattdessen verwendet werden.

## Spieltest

Zum Üben zuerst nur in einer eigenen Testwelt oder auf einem Server nutzen, der Makros gestattet. End Crystals können dich selbst töten. Ein PvP-Automationsmod kann auf öffentlichen Servern untersagt sein und zu Bans führen.

## Technisches / Privatsphäre

Der Quellcode enthält keine fremden Bibliotheken außerhalb der regulären Gradle-Abhängigkeiten von Minecraft/Fabric, keine Webrequests, keinen Netzwerk-Client, keine Datei-Lese-/Schreiboperation und keinen Code zum Einsammeln persönlicher Daten. `gameMode` nutzt nur die normalen Minecraft-Interaktionen.

Die Basisposition wird derzeit durch eine einfache geometrische Annäherung gewählt – kein intelligenter AutoCrystal-, Anti-Knockback-, Rotations- oder Sichtlinienalgorithmus.

Wenn beim Build ein Java-Fehler erscheint: die Fehlermeldung inklusive Dateiname und Zeilennummer weitergeben. Der Code wurde mangels Abhängigkeiten nicht kompilieren können.
