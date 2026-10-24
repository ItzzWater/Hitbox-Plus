# Contributing to Hitbox+

Thanks for wanting to help. Bug reports, ideas and code are all welcome.

## Reporting a bug

Open an [issue](https://github.com/ItzzWater/Hitbox-Plus/issues/new/choose) and include:

* Your Minecraft version and Hitbox+ version
* Your other mods (a screenshot of the mods folder is fine)
* What you expected to happen and what happened instead
* Your `latest.log` if the game crashed

## How the code is organized

Every supported Minecraft version has its own folder (`1.21.11`, `26.1`, `26.2`, `26.3`). Each folder is a complete Gradle project with the same features, adapted to that version's code.

* The 26.x folders use Mojang's official names, so their code looks almost the same
* `1.21.11` uses Yarn names, so classes and methods are called differently there (for example `MultiPlayerGameMode` is `ClientPlayerInteractionManager`)

When you change a feature, please make the same change in every version folder. If you can only test one version, say so in your pull request and that's fine.

## Building and testing

```bash
cd 26.3
./gradlew build      # builds the jar into build/libs
./gradlew runClient  # starts Minecraft with the mod loaded
```

Use JDK 25 for the 26.x folders and JDK 21 or newer for `1.21.11`.

Before opening a pull request, please check in game that:

* The config screen opens with `O` and every tab can be clicked
* Hitboxes still draw with `F3 + B`
* The setting you changed saves and loads after a restart

## Pull requests

* Keep each pull request about one thing
* Match the style of the code around your change
* Write clear, simple text for anything players will read in game
* Add a line to `CHANGELOG.md` under the next version

By sending a pull request you agree that your code is shared under the [MIT License](LICENSE).
