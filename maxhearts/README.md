# MaxHearts

A tiny Spigot/Paper plugin to control **max hearts** (max health) per player.
- Works cleanly above vanilla 20 hearts  
- Persists per-player values in `config.yml`  
- Commands for **set / give / take / donate / transfer / get**  
- Tab-completion for subcommands and online player names

## Commands

- Values are in **hearts**, not HP (e.g., `10` hearts = `20` HP).
- The HUD scales so the bar shows the exact heart count you set.

## Permissions

| Node | Default | Notes |
|------------------------|---------|----------------------------------------|
| `maxhearts.use` | op | Base node for `/maxhearts` |
| `maxhearts.set` | op | `/maxhearts set`, `/sethearts` |
| `maxhearts.give` | op | `/maxhearts give` |
| `maxhearts.take` | op | `/maxhearts take` |
| `maxhearts.donate` | true | `/maxhearts donate` (players can gift) |
| `maxhearts.transfer` | op | `/maxhearts transfer` |

## Config

```yaml
defaultHearts: 5
overrides:
  # PlayerName: 6
