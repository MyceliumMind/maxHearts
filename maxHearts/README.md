# MaxHearts v1.1.0

A Spigot/Paper plugin that lets server admins and players manage maximum hearts.
Supports setting, giving, taking, donating, and transferring hearts between players.
Now with UUID-based storage in data.yml for reliability when players change their names.

## Features
- Store player max hearts by UUID, not username.
- data.yml stores hearts and last-known names.
- Legacy overrides in config.yml are auto-migrated on startup.
- Configurable bounds: minHearts and maxHearts.
- Supports commands with player names or UUIDs.
- Prevents donors from dropping below minimum hearts.
- Clean event handling (join/respawn/apply hearts next tick).

## Installation
1. Download the compiled JAR (maxhearts-1.1.0.jar).
2. Place it into your server’s plugins/ folder.
3. Start the server.
   - config.yml will be created with defaults.
   - data.yml will be created automatically as players join.

## Configuration
plugins/MaxHearts/config.yml

defaultHearts: 5
minHearts: 1
maxHearts: 100

- defaultHearts: How many hearts new players spawn with.
- minHearts: Minimum allowed hearts (players can’t go below this).
- maxHearts: Maximum allowed hearts (hard cap).

Do not edit data.yml by hand — it is managed by the plugin.

## Commands

/maxhearts set <player|uuid> <hearts> Set a player’s max hearts to a value (permission: maxhearts.set)
/maxhearts give <player|uuid> <amount> Add hearts to a player (permission: maxhearts.give)
/maxhearts take <player|uuid> <amount> Remove hearts from a player (permission: maxhearts.take)
/maxhearts donate <player|uuid> <amount> Donate your own hearts to another player (permission: maxhearts.donate)
/maxhearts transfer <from> <to> <amount> Transfer hearts between two players (permission: maxhearts.transfer)
/maxhearts get <player|uuid> View a player’s stored max hearts (permission: none)
/sethearts <player|uuid> <hearts> Legacy alias for set (permission: maxhearts.set)

## Permissions
- maxhearts.use: General access (default: op)
- maxhearts.set: Use the set commands (default: op)
- maxhearts.give: Use the give command (default: op)
- maxhearts.take: Use the take command (default: op)
- maxhearts.donate: Allow players to donate their own hearts (default: true)
- maxhearts.transfer: Use the transfer command (default: op)
