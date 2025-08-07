# LamCore Reputation Integration

## Overview
RealisticVillagers now supports an alternative reputation system that integrates with LamCore's player vouch system. This allows villager reputation to be influenced by server-wide player reputation.

## How It Works
- When enabled, villagers will consider a player's LamCore vouch score when determining reputation
- The vouch score is converted to villager reputation using configurable multipliers
- Different villager professions can have different sensitivity to server reputation

## Configuration
Edit `config.yml` and look for the `reputation` section:

```yaml
reputation:
  # Debug mode - logs reputation calculations
  debug: false
  
  lamcore:
    # Enable LamCore integration
    enabled: true
    
    # Mode: "additive" or "replace"
    # - additive: Adds LamCore reputation to vanilla reputation
    # - replace: Completely replaces vanilla reputation
    mode: additive
    
    # Multiplier for converting vouch scores to reputation
    global-multiplier: 5.0
    
    # Min/max limits
    min-reputation: -200.0
    max-reputation: 200.0
    
    # Profession-specific multipliers
    profession-multipliers:
      CLERIC: 1.5       # Clerics care most about moral standing
      WEAPONSMITH: 1.2  # Weaponsmiths are cautious
      FARMER: 0.8       # Farmers are forgiving
      # ... etc
```

## Commands
- `/rvrep check` - Check your reputation with the nearest villager
- `/rvrep debug` - Show debug information about reputation calculation
- `/rvrep providers` - List active reputation providers
- `/rvrep reload` - Reload reputation configuration (requires permission)

## Permissions
- `realisticvillagers.reputation` - Access to reputation commands
- `realisticvillagers.reputation.reload` - Reload reputation configuration

## Example Scenarios

### Scenario 1: Trusted Player
- Player has +20 vouch score in LamCore
- Global multiplier is 5.0
- Results in +100 base reputation with villagers
- Cleric (1.5x multiplier) sees +150 reputation
- Farmer (0.8x multiplier) sees +80 reputation

### Scenario 2: Untrusted Player
- Player has -10 vouch score in LamCore
- Global multiplier is 5.0
- Results in -50 base reputation with villagers
- Trading prices will be higher
- Villagers may be less willing to interact

## AI Chat Integration
When AI chat is enabled, reputation affects:

### Dialogue Tone
Villagers speak differently based on your reputation:
- **Hostile (-200 to -100)**: Cold, dismissive, tells you to leave
- **Unfriendly (-99 to -30)**: Brief, unhelpful, suspicious
- **Neutral (-29 to 29)**: Polite but reserved, professional
- **Friendly (30 to 99)**: Warm, helpful, interested in conversation
- **Beloved (100 to 200)**: Extremely affectionate, goes out of their way to help

### Tool Usage Restrictions
AI tools are restricted by reputation in `ai-config.yml`:
```yaml
tools:
  available-tools:
    follow_player:
      min-reputation: -29  # Requires neutral+ reputation
    give_item:
      min-reputation: 30   # Only friendly villagers give items
```

## Integration Benefits
1. **Server-wide Reputation**: Player behavior across the server affects villager interactions
2. **AI-Driven Behavior**: Villagers naturally adjust their attitude and helpfulness
3. **Tool-Based Actions**: Reputation controls what villagers are willing to do for you
4. **Profession Variety**: Different professions care differently about reputation
5. **Flexible Modes**: Choose between adding to or replacing vanilla reputation
6. **Configurable**: Full control over conversion rates and limits

## Troubleshooting
- Ensure LamCore is installed and the vouch module is enabled
- Check that `reputation.lamcore.enabled` is set to `true`
- Use `/rvrep debug` to see calculation details
- Check console logs for any integration errors

## Technical Details
The integration uses reflection to access LamCore's VouchModule to avoid hard dependencies. This means:
- RealisticVillagers will work with or without LamCore
- If LamCore is not present, the integration simply disables itself
- No errors will occur if LamCore is removed later