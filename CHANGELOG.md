# Changelog

All notable changes to Hitbox+ are written here. Dates are when the version went live on [Modrinth](https://modrinth.com/mod/hitbox-plus/versions).

## 1.3.0

**New: Players tab and Team Color**

* New Players tab in the config screen with a Team Color page
* Teammates get their own hitbox color (rainbow works too)
* Add players from the config screen with name autocomplete, or with the new `/hbteam add | remove | list | clear` commands
* Team list shows each player's skin face

**Under the hood**

* Builds now pick the right JDK automatically through Gradle toolchains, with no machine specific paths
* First public source release on GitHub

## 1.2.0 (2026-10-13)

**Fixed major bugs** for 1.21.11, 26.1.x, 26.2 and 26.3

* Hover color now asks Minecraft's own attack logic what a click would hit, so it always matches real reach and weapon attack ranges
* Spear stabs are tracked correctly, including when they hit several entities at once
* Hit color only flashes after a hit is confirmed
* Left click on the config screen fixed for 26.3

## 26.3 support (2026-10-04)

* First build for Minecraft 26.3

## Reach fix (2026-08-22)

* Fixed the reach bug on 1.21.11, 26.1.x and 26.2

## 26.2 support (2026-06-22)

* First build for Minecraft 26.2

## 1.1.0 (2026-06-12)

**Completely redesigned config UI**

* New custom config screen with Toggles, Sizing and Colors tabs
* RGB color picker with rainbow option for every color
* Builds for 1.21.11 and 26.1.x

## 1.21.11 feature update (2026-04-24)

* More features for 1.21.11

## 26.1 support (2026-04-24)

* First builds for Minecraft 26.1, 26.1.1 and 26.1.2

## 1.0.0 (2026-03-16)

* First release of Hitbox+ for Minecraft 1.21.11
