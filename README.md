# Block Shuffle — Fabric port

Block Shuffle is a server-side Minecraft minigame. At the beginning of each round, every participant gets a block to find and stand on. Participants who do not complete their challenge before time runs out are eliminated. The last remaining participant wins. This port is based on the Paper plugin in this repository; its original Kotlin files are retained in [`paper-source/`](paper-source/).

## Install

1. Use a Fabric server for your Minecraft version. Install Fabric API for that same version.
2. Download the matching Block Shuffle Fabric JAR from a successful [Build Fabric versions](../../actions/workflows/build.yml) workflow run, or build it yourself below.
3. Put the JAR in the server's `mods` directory. Do not install the Paper plugin alongside it.
4. Start the server. Settings are created at `config/blockshuffle/config.yml`.

Each build targets one exact Minecraft release. The build matrix covers 1.21 through 1.21.11 (including every numbered 1.21.x release) and 26.2. Use Java 21 for 1.21.x and Java 25 for 26.2. Clients can join with an unmodified game.

## Commands

| Command | Purpose |
| --- | --- |
| `/bs start` | Start a game with the players currently online. |
| `/bs quit` | Leave the current game. |
| `/bs help` | Show commands. |
| `/bs about` | Show information about the mod. |
| `/bs stop` | End a game (operator). |
| `/bs reload` | Reload the configuration (operator). |

`/blockshuffle` is the long form of `/bs`. By default, at least two players must be online to start; you can change `min-players` in the configuration. Players joining after a game starts wait until the next game.

## Build

```sh
./gradlew -PmcVersion=1.21.11 clean build
```

Change `mcVersion` to the exact target release; for example, `1.21.1` or `26.2`. The completed, remapped mod JAR is in `build/libs/` (the `-sources.jar` file is only source code). Java 25 is required to build 26.2, and Java 21 suffices for 1.21.x. The included GitHub Actions workflow builds every target individually and uploads each JAR as an artifact.

The Fabric build uses Yarn mappings for 1.21.x and Mojang's official names for 26.2. The two command implementations account for Minecraft's permission API changes. The `config.yml` format, blacklist, weighted blocks, round length, boss bar toggle, and custom messages come from the Paper plugin.
