# Pack Studio (Fabric, 1.21.11)

Bauen:   ./gradlew build   (oder gradle build)  ->  build/libs/packstudio-1.0.0.jar
Rein in den mods-Ordner (mit Fabric API + Modmenu).

Oeffnen: Modmenu -> Pack Studio -> Konfig-Button, oder Keybind (Standard: P, aenderbar unter Steuerung -> Pack Studio).
Video-Export braucht ffmpeg im PATH. Videos landen in .minecraft/packstudio/exports/.

Falls Gradle eine Version nicht findet: auf https://fabricmc.net/develop/ 1.21.11 waehlen
und yarn_mappings / loader_version / loom_version / fabric_version in gradle.properties anpassen.
