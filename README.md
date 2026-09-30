# 🧱 Block Shuffle — Minecraft Mini-Game Plugin

Block Shuffle is a fast-paced, competitive Minecraft minigame inspired by popular YouTube challenges.
Players are given a random block every round and must stand on it before the timer runs out — or they’re eliminated.

Perfect for:
✔ Survival servers
✔ Events & tournaments
✔ SMP mini-games
✔ Content creators

---

## 🌟 Features

- 🎯 **Random Block Challenges**
  - Each player receives a unique block every round.

- 🧠 **Smart Difficulty Scaling**
  - Difficulty increases as rounds progress.
  - Fewer players = harder challenges.

- ⚖️ **Weighted Block System**
  - Common blocks appear more often.
  - Rare blocks stay rare.
  - Fully configurable.

- 🧱 **Dimension Control**
  - Enable/disable Overworld, Nether, or End blocks.

- ❌ **Automatic Elimination**
  - Fail to find your block → eliminated.
  - Last player standing wins.

- ⚡ **Optimized for Paper**
  - Lightweight
  - Lag-free
  - Event-based logic

---

## 🎮 How It Works

1. Run:
```

/bs start

```

2. Each player receives a random block.

3. Players must stand on that block before the timer ends.

4. Players who fail are eliminated.

5. Rounds continue until one player remains.


❤️ Why Use Block Shuffle?

✔ Clean UI
✔ Competitive & fun
✔ Highly customizable
✔ Lightweight
✔ Perfect for events
✔ Actively developed

---

## ⚙️ Configuration

Everything is configurable via `config.yml`.

### Example:
```yaml
round-time: 300
min-players: 2
allow-bossbar: true

allow:
  overworld: true
  nether: true
  end: false

messages:
  start: "&a🟢 Block Shuffle started!"
  win: "&6🏆 {player} won the game!"
  fail: "&cYou failed this round!"

weight-categories:
  common-blocks: 10
  building-blocks: 8
  decorative: 7
  common-ores: 6
  nether-blocks: 5
  default: 5
  uncommon-ores: 4
  end-blocks: 3
  rare-ores: 2
  ultra-rare: 1

weights:
  # STONE: 10
  # DIRT: 9

difficulty:
  weight-reduction-per-round: 0.15

blacklist:
  - END_PORTAL_FRAME
  - SPAWNER
  - BARRIER
  - DEEPSLATE_EMERALD_ORE
  - ANCIENT_DEBRIS
  - LODESTONE
  - COMMAND_BLOCK
  - STRUCTURE_BLOCK
  // more like player heads

````

---

## 🧠 Smart Difficulty System

✔ Early rounds → easy blocks
✔ Mid rounds → moderate challenge
✔ Late rounds → rare & difficult blocks

The game adapts automatically to keep gameplay fair and exciting.

---

## 📦 Installation

1. Download the mod `.jar`
2. Place it in `/mods`
3. launch the game
4. Edit `config.yml`
5. Run `/bs start`

---

## 🏆 Why Use Block Shuffle?

✔ Fun & competitive
✔ Easy to configure
✔ Works great for events
✔ Scales with player skill
✔ Lightweight & optimized

---

## ❤️ Support & Community

⭐ Star the project
🐞 Report bugs
💡 Suggest features

---

### 🤝 Sponsor

[GitHub Sponsors](https://github.com/sponsors/hellofaizan)

### 💬 Discord

[https://discord.gg/vUHMxPvege](https://discord.gg/vUHMxPvege)
