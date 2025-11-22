# RealisticVillagers - Villager Behavior Alterations

**Plugin Version:** 3.3.6.1  
**Last Updated:** October 2025  
**Minecraft Versions:** 1.18 - 1.21.10

---

## Table of Contents

1. [Overview](#overview)
2. [Visual Appearance Changes](#visual-appearance-changes)
3. [AI & Intelligence Systems](#ai--intelligence-systems)
4. [Work & Profession Behaviors](#work--profession-behaviors)
5. [Combat & Defense Systems](#combat--defense-systems)
6. [Social & Relationship Systems](#social--relationship-systems)
7. [Survival & Basic Needs](#survival--basic-needs)
8. [Movement & Pathfinding](#movement--pathfinding)
9. [Trading System Modifications](#trading-system-modifications)
10. [Death & Revival Systems](#death--revival-systems)
11. [Pet & Animal Interactions](#pet--animal-interactions)
12. [Comparison with Vanilla Villagers](#comparison-with-vanilla-villagers)

---

## Overview

**RealisticVillagers** fundamentally transforms Minecraft villagers from simple trading NPCs into interactive, intelligent human-like entities. Instead of the blocky, basic villager models, they appear as player-like characters with custom skins, equipment, and complex behavioral AI systems.

### Core Philosophy

The plugin replaces "boring villagers" with "interactive humans" by:
- **Replacing the villager model** with player models using custom skins
- **Implementing advanced AI** for realistic decision-making and conversations
- **Adding survival mechanics** like hunger, fatigue, and resource management
- **Creating social systems** for relationships, families, and communities
- **Enabling combat capabilities** with weapons, armor, and tactical behaviors
- **Introducing life cycles** including reproduction, growth, and death/revival

---

## Visual Appearance Changes

### Player-Like Models

**Vanilla Behavior:**
- Villagers appear as blocky humanoid mobs with large heads
- Limited customization (only profession-based robes)
- No equipment rendering

**RealisticVillagers Behavior:**
- Villagers render as **player entities** with full player models
- **Custom skins** differentiated by:
  - **Sex** (male/female) - affects skin texture and naming conventions
  - **Age stage** (adult/child) - proper scaling and proportions
  - **Profession** - optional profession-specific overlay textures
  - **Biome type** - maintains vanilla biome variants (desert, jungle, plains, savanna, snow, swamp, taiga)
- Full **equipment rendering**:
  - Armor pieces (helmet, chestplate, leggings, boots)
  - Main hand items (weapons, tools)
  - Offhand items (shields, torches, food)
  - Shoulder entities (parrots can perch on shoulders)

### Custom Nametags

**Configuration:** `custom-nametags` section

**Vanilla Behavior:**
- Simple name display above entity
- No additional information

**RealisticVillagers Behavior:**
- **Multi-line nametags** showing:
  - Villager name with level and profession
  - Visual indicators (hunger 🍖, confined status 🔒)
  - Optional job block icon above text
- **Customizable appearance**:
  - Shadow effects
  - See-through walls option
  - Adjustable text opacity (-128 to 127)
- **Profession-specific formatting**
  - Different templates for villagers vs wandering traders
  - Gender-aware profession titles

**Example Display:**
```
&f[Villager Name] &a(Lvl. 3) 🍖
&7Farmer
```

### Skin System

**Features:**
- **Dynamic skin generation** via Mineskin API integration
- **Skin database** storing hundreds of unique skins
- **Sex-specific skins** with male/female variants
- **Age-appropriate skins** for children vs adults
- **Profession overlays** can be applied to base skins
- **Player-based skins** - generate skins from any Minecraft player's username
- **Custom skin uploads** via console commands

**Configuration Options:**
- `mineskin-api-key` - API key for faster skin generation
- `render-distance: 56` - Distance at which villager models render
- Skin preview system with configurable duration

**Commands:**
- `/rv add-skin <sex> <texture> <signature>` - Add skin from texture data
- Right-click with "change skin" item to modify villager appearance

---

## AI & Intelligence Systems

### Conversational AI Integration

**Feature:** AI-powered conversations using OpenAI or Groq

**Vanilla Behavior:**
- Villagers only use sound effects (hrmm, hmm, huh)
- No text-based communication
- No context awareness

**RealisticVillagers Behavior:**
- **Natural language conversations** with players via chat
- **Context-aware responses** considering:
  - Villager profession and role
  - Player reputation and relationship
  - Family connections (partner, children, parents)
  - Current activity and location
  - Past conversation history
- **Personality-driven dialogue**:
  - Different tones based on relationship status
  - Profession-specific knowledge and topics
  - Emotional responses to gifts, insults, compliments
- **Command understanding**:
  - "Follow me" → Villager starts following
  - "Stay here" → Villager holds position
  - "Give me items" → Villager drops items for player
  - "Can you help me?" → Context-dependent assistance

**Technical Implementation:**
- Uses AI conversation manager with tool-calling capabilities
- Builds personality profiles from villager data
- Maintains conversation context per player-villager pair
- Cooldown system prevents spam
- Distance-based conversation activation (default: 10 blocks)

**Configuration:**
- Requires `ai-config.yml` with API provider setup
- Permission: `realisticvillagers.ai.use`
- Configurable conversation distance and timeout

### Tool System

The AI can invoke "tools" to perform actions:

**Movement Tools:**
- `move_to_location` - Navigate to coordinates
- `follow_player` - Start following specific player
- `stay_in_place` - Hold current position

**Item Tools:**
- `give_items` - Drop items for player
- `request_items` - Ask for specific items

**Interaction Tools:**
- `greet_player` - Perform greeting animation
- `thank_player` - Show gratitude
- `refuse_request` - Decline player request

**Social Tools:**
- `discuss_family` - Talk about family members
- `share_reputation` - Discuss relationship status

### Behavioral AI Architecture

**Brain System:**
- Replaces vanilla villager AI with custom behavior trees
- **Activity-based scheduling**:
  - **Core** - Essential survival behaviors
  - **Work** - Profession-specific tasks
  - **Play** - Social interactions
  - **Rest** - Sleeping in beds
  - **Meet** - Gathering at meeting points
  - **Idle** - Random wandering
  - **Panic** - Fleeing from danger
  - **Raid** - Defensive behaviors during raids
  - **Fight** - Combat actions
  - **Hide** - Taking cover from threats
  - **Stay** - Following player commands

**Custom Sensors:**
- `NearestItemSensor` - Detects dropped items and wanted resources
- `NearestLivingEntitySensor` - Identifies nearby entities
- `VillagerHostilesSensor` - Monitors hostile mobs
- `SecondaryPoiSensor` - Tracks alternative points of interest

**Behavior Packages:**
Each activity has multiple behaviors running simultaneously:

**Core Behaviors:**
- `CheckInventory` - Manage inventory space
- `Consume` - Eat food when hungry
- `EatCake` - Special cake interaction
- `EquipTotem` - Auto-equip totem of undying
- `GoToWantedItem` - Pathfind to desired items
- `HealGolem` - Repair iron golems with iron ingots
- `HelpFamily` - Assist family members in danger
- `LootChest` - Search and take items from chests
- `RideHorse` - Mount and ride owned horses
- `TameOrFeedPet` - Interact with animals
- `VillagerPanicTrigger` - Enter panic state on damage
- `YieldJobSite` - Share job blocks with unemployed

---

## Work & Profession Behaviors

### Work-Hunger Integration

**Major Modification:** Villagers now have a **hunger/stamina system** tied to work

**Vanilla Behavior:**
- Villagers work indefinitely without fatigue
- No food consumption
- Instant trade restocking at job sites

**RealisticVillagers Behavior:**

**Hunger Mechanics:**
- Villagers have **food level** (0-20, like players)
- **Work consumes hunger** (configurable, default: 1 per work action)
- **Minimum hunger threshold** required to work (default: 5)
- Below threshold: Villager refuses work tasks
- Villagers **eat food** from inventory to restore hunger

**Food Sharing System:**
- Hungry villagers can **request food** from nearby villagers (default range: 40 blocks)
- Well-fed villagers **share excess food** with hungry ones
- Two delivery modes:
  - **Instant transfer** (simple mode)
  - **Physical delivery** (villager walks and drops items)
- Respects `min-keep-food` setting (villagers keep minimum for themselves)
- **Periodic hunger checks** run at configurable intervals

**Configuration:** `work-hunger-config.yml`
```yaml
work-hunger:
  enabled: true
  hunger-cost-per-work: 1
  min-hunger-to-work: 5
  
villager-requests:
  enabled: true
  nearby-villager-range: 40
  hunger-threshold: 15  # Request food below this level
  min-keep-food: 3      # Don't give away all food
  
physical-interaction:
  enabled: true         # Walk to deliver items
  max-delivery-distance: 32
  item-claim-duration-seconds: 30
```

### Work-Based Item Generation

**Major Modification:** Replaces vanilla restocking with realistic work-based generation

**Vanilla Behavior:**
- Villagers restock trades by "working" at job site
- Instant, magical item generation
- No logical connection to work actions

**RealisticVillagers Behavior:**

**Profession-Specific Generation:**
Each profession generates appropriate items through work:

**ARMORER** (works at anvil):
- Generates: Emeralds, iron/diamond/chainmail armor pieces
- Max limits per item type (e.g., max 4 helmets)

**BUTCHER** (works at smoker):
- Generates: Emeralds, cooked meats (pork, beef, chicken, rabbit, mutton)

**CARTOGRAPHER** (works at cartography table):
- Generates: Emeralds, empty maps, filled maps

**CLERIC** (works at brewing stand):
- Generates: Emeralds, redstone, lapis lazuli, glowstone, ender pearls

**FARMER** (harvests crops):
- Generates: Emeralds, bread, wheat, carrots, potatoes, beetroot
- Actually harvests farmland blocks
- Uses bonemeal from inventory

**FISHERMAN** (fishes in water):
- Generates: Emeralds, raw fish (cod, salmon)
- Actually casts fishing rod
- Custom fishing hook entity

**FLETCHER** (works at fletching table):
- Generates: Emeralds, arrows, bows, crossbows, flint, sticks

**LEATHERWORKER** (works at cauldron):
- Generates: Emeralds, leather armor pieces, saddles

**LIBRARIAN** (works at lectern):
- Generates: Emeralds, enchanted books, bookshelves, paper

**MASON** (works at stonecutter):
- Generates: Emeralds, stone blocks, bricks, terracotta

**SHEPHERD** (works at loom):
- Generates: Emeralds, wool (all colors), beds, paintings

**TOOLSMITH** (works at smithing table):
- Generates: Emeralds, iron/diamond/stone tools

**WEAPONSMITH** (works at grindstone):
- Generates: Emeralds, iron/diamond swords, axes

**Configuration Features:**
```yaml
work-item-generation:
  enabled: true
  check-actual-trades: true  # Only generate items villager can trade
  max-inventory-percentage: 0.75  # Stop when inventory 75% full
  
  # Per-profession configuration
  items:
    ARMORER:
      emerald:
        amount: 2
        chance: 1.0
      helmet:
        items: [IRON_HELMET, DIAMOND_HELMET, CHAINMAIL_HELMET]
        max: 4
        chance: 0.3
```

### Profession-Specific Work Actions

**FARMER:**
- **Harvests farmland** - breaks fully grown crops
- **Plants seeds** - replants after harvesting
- **Uses bonemeal** - accelerates crop growth
- **Collects produce** - picks up dropped items

**FISHERMAN:**
- **Casts fishing rod** - into nearby water
- **Waits for fish** - patience system
- **Reels in catch** - retrieves fish items
- **Cooldown system** - prevents spam fishing

**All Professions:**
- **Work at POI** (Point of Interest - job site)
- **Navigate to job site** when work time arrives
- **Consume hunger** proportional to work done
- **Store generated items** in inventory
- **Stop working** when hungry or inventory full

---

## Combat & Defense Systems

### Equipment & Weapons

**Vanilla Behavior:**
- Villagers cannot equip weapons or armor
- Always flee from danger
- Cannot fight back

**RealisticVillagers Behavior:**

**Equipment Capabilities:**
- **Full armor support** (helmet, chestplate, leggings, boots)
- **Weapon wielding**:
  - **Melee**: Swords, axes, tridents
  - **Ranged**: Bows, crossbows
- **Shield usage** - active blocking mechanics
- **Offhand items** - totems, shields, torches

**Automatic Equipment Management:**
- `EquipTotem` - Auto-equips totem of undying when in danger
- Armor automatically worn if in inventory
- Best weapon selected for combat situation
- Shield raised when backing away from threats

### Combat Behaviors

**Attack Patterns:**

**Melee Combat:**
- **MeleeAttack** behavior - approaches and strikes targets
- **Attack damage**: Configurable (default: 3.0)
- **Attack cooldown**: 10 ticks (configurable)
- **Combo attacks** - multiple strikes in succession

**Ranged Combat:**
- **RangeWeaponAttack** - bow/crossbow usage
- **Projectile power**: Configurable (default: 1.6)
- **Targeting system** - leads shots for moving targets
- **Arrow management** - picks up arrows after combat
- **Trident throws** - including riptide mechanics

**Shield Combat:**
- **BlockAttackWithShield** - raises shield when attacked
- **Timing system** - blocks at optimal moments
- **Durability management** - switches to backup shield

**Tactical Behaviors:**
- **BackUpIfTooClose** - maintains optimal distance
- **SetWalkTargetFromAttackTargetIfTargetOutOfReach** - closes distance to target
- **StopAttackingIfTargetInvalid** - disengages from invalid targets

### Target Selection

**Default Hostile Entities:**
```yaml
default-target-entities:
  - DROWNED
  - EVOKER
  - HUSK
  - ILLUSIONER
  - PILLAGER
  - RAVAGER
  - VEX
  - VINDICATOR
  - ZOGLIN
  - ZOMBIE
  - ZOMBIE_VILLAGER
  - WARDEN
```

**Detection Range:**
- Base: 15 blocks for unarmed villagers
- Doubled (30 blocks) for armed villagers

**Target Management:**
- **Per-villager target lists** customizable via GUI
- **Add/remove entities** from attack list
- **Player targeting** - can add specific players
- **Category filtering** - animals, monsters, players

### Defense Systems

**Panic & Flee:**
- `VillagerPanicTrigger` - enters panic when damaged
- Flees from threats when unarmed
- Prioritizes survival over engagement
- Seeks safe locations (homes, villages)

**Iron Golem Interaction:**
- **Heal golems** with iron ingots
- **Cooldown**: 600 ticks (30 seconds, configurable)
- Villagers work together to repair damaged golems

**Family Defense:**
- `HelpFamily` - protects family members
- Engages threats attacking relatives
- Cooldown: 600 ticks (configurable)
- Requires weapon in hand

**Player Defense:**
- **Defend hero of the village** (HERO_OF_THE_VILLAGE effect)
- **Defend following player** - protects player they're following
- **Defend family members** - attacks family threats
- **Option**: Can defend players from other players (PvP)

**Configuration:**
```yaml
villager-defend-family-member: true
villager-defend-hero-of-the-village: true
villager-defend-following-player: true
villager-defend-attack-players: false  # PvP defense
```

### Special Combat Features

**Raid Participation:**
- `SetRaidStatus` - tracks raid state
- Active participation in village defense
- **Configurable raid behavior**:
  - Attack player if damaged during raid
  - Coordinate with other villagers
  - Protect village infrastructure

**Anti-Monster Features:**
- **Attack players wearing monster skulls**
  - DRAGON_HEAD, WITHER_SKELETON_SKULL, ZOMBIE_HEAD, etc.
- **Attack players using goat horn "seek" sound** (1.19+)
  - Detection range: 32 blocks (configurable)

**Trident Special Abilities:**
- `TridentAttack` behavior
- **Riptide support** - launches villager through air/water
- **Channeling support** - summons lightning (can transform villagers to witches)
- **Loyalty support** - trident returns after throw

**Arrow Mechanics:**
- **Pass through other villagers** (prevents friendly fire)
- **Pickup status**: Configurable (ALLOWED/DISALLOWED/CREATIVE_ONLY)
- Custom arrow entity tracking

---

## Social & Relationship Systems

### Reputation System

**Vanilla Behavior:**
- Simple gossip system
- Limited interaction types
- Affects trade prices

**RealisticVillagers Behavior:**

**Reputation Tracking:**
- Per-player reputation scores (-200 to +200)
- Affects all interactions and prices
- Visible in villager information GUI

**Reputation Gains:**
```yaml
wedding-ring-reputation: 20      # Giving wedding ring
cross-reputation: 10             # Giving cross item
baby-reputation: 20              # Having a child together
chat-interact-reputation: 2      # Successful interactions

# Gift-based reputation
gift:
  good: 2     # Common items (food, flowers, leather armor)
  better: 3   # Better items (potions, iron armor, bow)
  best: 5     # Best items (diamonds, enchanted apples, netherite)
```

**Reputation Losses:**
```yaml
divorce-reputation-loss: 50           # Divorcing without papers
divorce-reputation-loss-papers: 10    # Divorcing with papers
bad-gift-reputation: -5               # Giving unwanted items
# Also loses reputation when:
# - Attacking villager
# - Killing iron golems
# - Insulting villager (failed interaction)
```

**Reputation Effects:**
- **Trading prices** - better reputation = discounts
- **Conversation tone** - affects AI dialogue mood
- **Interaction success** - higher reputation = better outcomes
- **Marriage requirements** - need 75+ reputation
- **Procreation requirements** - need 110+ reputation
- **Auto-divorce threshold** - divorces if below configured level (default: 0)

### Marriage System

**Requirements:**
- Reputation ≥ 75
- Wedding ring item (craftable)
- Successful "gift" interaction with ring

**Wedding Ring Recipe:**
```yaml
wedding-ring:
  crafting:
    shaped: true
    ingredients:
      - DIAMOND, D
      - GOLD_INGOT, G
    shape:
      - "GDG"
      - "G G"
      - "GGG"
```

**Marriage Features:**
- **Married status** tracked per villager
- **Partner tracking** - knows spouse UUID
- **Multiple partners possible** (configurable)
- **Player-villager marriage** supported
- **Villager-villager marriage** supported
- **Cross-species**: Only villagers (not wandering traders)

**Marriage Effects:**
- Enables procreation interactions
- Joint family relationships
- Shared children tracking
- Special dialogue in conversations
- Divorce papers available from clerics

### Procreation & Children

**Requirements:**
- Married to partner OR high reputation (110+)
- Proper configuration of villager-farm settings

**Configuration Options:**
```yaml
villager-farm:
  ignore-sex-when-procreating: false        # Same-sex procreation
  allow-partner-cheating: false             # Procreate with non-partner
  allow-partner-cheating-for-all: false     # Cheating includes player marriages
  allow-procreation-between-family-members: false  # Incest prevention
```

**Procreation Process:**
1. Player initiates through GUI or dialogue
2. Villager evaluates requirements
3. Expects bed placement (timeout: 100 ticks)
4. Baby spawns as child villager
5. Player names baby via GUI
6. Baby grows over time (default: 20 minutes)

**Baby Features:**
- **Spawns as paper item** in player inventory
- **Sex determined** randomly or by genetics
- **Name selection** via custom input GUI
- **Growth timer** (configurable, default: 1,200,000 ms = 20 min)
- **Inherits skin** from parents (custom skin ID system)
- **Family tree tracking** - knows parents, siblings
- **Grows to adult** automatically after timer

**Configuration:**
```yaml
baby-grow-cooldown: 1200000  # 20 minutes in milliseconds
procreation-cooldown: 1200000  # Cooldown between births
reputation-required-to-marry: 75
reputation-required-to-procreate: 110
```

### Divorce System

**Methods:**
1. **Direct divorce** - Via interaction GUI
   - Reputation loss: 50 (default)
   - Immediate separation
   
2. **Divorce papers** - Obtained from Cleric villagers
   - Reputation loss: 10 (default)
   - Gentler separation
   - Must be given to villager

**Divorce Effects:**
- Marriage status cleared
- Partner reference removed
- Reputation penalty applied
- Can remarry later with sufficient reputation

**Auto-Divorce:**
```yaml
divorce-if-reputation-is-less-than: 0  # Auto-divorce threshold
```

### Family System

**Family Tracking:**
- **Parents**: Father and mother UUIDs
- **Partner(s)**: Current spouse(s)
- **Children**: List of offspring UUIDs
- **Siblings**: Shared parent tracking

**Family Interactions:**
- `HelpFamily` - defend family members
- Family members appear in whistle GUI
- Special dialogue referencing family
- Inheritance of traits (skins, professions)

**Family Identification:**
- `isFamily(UUID)` - checks family relationship
- `isPartner(UUID)` - checks marriage status
- `isFatherVillager()` / `isMotherVillager()` - checks if parents are villagers or players

### Gift System

**Gift Modes:**
```yaml
gift-mode: DROP  # or RIGHT_CLICK
```

**DROP Mode:**
- Player drops item (Q key)
- Villager detects and picks up
- Evaluates gift quality

**RIGHT_CLICK Mode:**
- Player right-clicks with item
- Single item consumed
- Immediate evaluation

**Gift Categories:**

**GOOD (2 reputation):**
- Edible foods
- Flowers
- Music discs
- Leather/chainmail/golden armor
- Wooden/stone/golden tools
- Amethyst shards, bones, arrows

**BETTER (3 reputation):**
- Potions
- Iron armor and tools
- Shields
- Ender pearls
- Bows, crossbows
- Profession-specific bonuses (e.g., arrows to Fletcher)

**BEST (5 reputation):**
- Beacon payment items (diamonds, emeralds, gold, iron, netherite)
- Diamond/netherite armor and tools
- Enchanted golden apples
- Tridents

**Profession-Specific Bonuses:**
```yaml
# Example: FLETCHER gets extra reputation for arrows
gift:
  better:
    items:
      - ?FLETCHER:$ARROWS  # 3 rep instead of 2 for Fletcher
```

**Gift Expectations:**
- Villager can "expect" gifts via interaction
- Timeout: 100 ticks (5 seconds)
- Cooldown: 3 seconds between gift interactions
- Special "Proud-of" interaction always succeeds (30s cooldown)

### Interaction Types

**Chat Interactions:**

**CHAT** (2s cooldown):
- General conversation
- Success chance: 65% (configurable)
- Reputation: +2

**GREET** (30s cooldown):
- Friendly greeting
- Always successful
- Reputation: +2

**STORY** (2s cooldown):
- Tell a story
- Success chance: 65%
- Reputation: +2

**JOKE** (2s cooldown):
- Tell a joke
- Success chance: 65%
- **Always successful with partner** (configurable)
- Reputation: +2

**FLIRT** (2s cooldown):
- Romantic interaction
- Success chance: 65%
- Reputation: +2

**PROUD-OF** (30s cooldown):
- Express pride
- Always successful
- Reputation: +2

**INSULT** (2s cooldown):
- Negative interaction
- Success chance: 65%
- Reputation: -2 (on success)

**Configuration:**
```yaml
chance-of-chat-interaction-success: 0.65
partner-joke-always-success: false
chat-interact-reputation: 2

interact-cooldown:
  chat: 2
  greet: 30
  story: 2
  joke: 2
  flirt: 2
  proud-of: 30
  insult: 2
```

### Social Behaviors

**SocializeAtBell:**
- Villagers gather at village bell
- Meet with other villagers
- Exchange gossip
- Max gossip topics: 10 (configurable)

**TradeWithVillager:**
- Villagers trade with each other
- Exchange profession-specific items
- Builds inter-villager economy

**InteractWithBreed:**
- Villagers can procreate with each other
- Follows same rules as player-villager procreation
- Creates multi-generational villages

---

## Survival & Basic Needs

### Hunger System

**Vanilla Behavior:**
- Villagers have no hunger
- No food consumption

**RealisticVillagers Behavior:**

**Hunger Mechanics:**
- **Food level**: 0-20 (same as players)
- **Saturation**: Hidden secondary hunger buffer
- **Exhaustion**: Accumulated from activities

**Hunger Consumption:**
- **Working**: 1 hunger per work action (configurable)
- **Walking**: Minor exhaustion accumulation
- **Combat**: Higher exhaustion rate
- **Sprinting**: Even higher exhaustion
- **Healing**: Consumes hunger to regenerate health

**Eating Behavior:**
- `Consume` behavior - actively eats food from inventory
- **Food preferences**: Prefers high-saturation foods
- **Auto-eating**: Triggers when hunger drops below threshold
- **Animation**: Eating particles and sounds

**Starvation:**
- Below minimum threshold (default: 5) → cannot work
- Does NOT take damage (unlike players)
- Becomes inactive and seeks food

### Sleep System

**SleepInBed:**
- Villagers sleep in beds during night/rest activity
- **Bed ownership** tracked
- **Respawn point** set to bed location
- **Set home** interaction - player can give bed to villager

**Sleep Mechanics:**
- Lies down in bed
- Sleeps through night
- Wakes at dawn
- Dreams (chat messages optional)

**Bed Requirements:**
- Must have claimed bed
- Bed must be accessible
- Nighttime or rest schedule
- Not in combat/panic state

### Inventory Management

**Inventory System:**
- Villagers have **inventory space** (default: 27 slots, max: 36)
- Displayed via "Inspect Inventory" GUI
- Separate from trade inventory

**Inventory Behaviors:**

**CheckInventory:**
- Periodically reviews inventory
- Identifies needed items
- Clears excess items
- Organizes equipment

**GoToWantedItem:**
- Detects dropped items nearby
- Pathfinds to desired items
- Picks up items
- Stores in inventory

**LootChest:**
- Searches nearby chests
- Takes needed items
- Respects ownership (optional)
- Cooldown system

**Item Delivery:**
- `PhysicalItemDelivery` - walks to target and drops items
- Reserved items (prevents other pickup)
- Claim duration: 30 seconds (configurable)

**Configuration:**
```yaml
villager-inventory-size: 27  # Max 36
drop-whole-inventory: false  # Drop all on death
```

### Resource Management

**Wanted Items System:**
- Each profession has list of "wanted" items
- Actively seeks these items when available
- Stores for later use or trading

**Example Wanted Items:**
- **FARMER**: Seeds, wheat, carrots, potatoes
- **ARMORER**: Iron ingots, diamonds, leather
- **FLETCHER**: Sticks, flint, feathers
- **CLERIC**: Blaze powder, nether wart, bottles

**Auto-Collection:**
- Picks up profession-relevant drops
- Ignores irrelevant items
- Inventory space permitting

### Cake Interaction

**EatCake:**
- Villagers can eat cake blocks
- Restores hunger
- Removes cake slices progressively
- Pathfinds to nearby cakes when hungry

---

## Movement & Pathfinding

### Enhanced Pathfinding

**Vanilla Behavior:**
- Basic pathfinding
- Frequent getting stuck
- Limited obstacle avoidance

**RealisticVillagers Behavior:**

**Movement Behaviors:**

**MoveToTargetSink:**
- Advanced pathfinding to targets
- Obstacle avoidance
- Door interaction
- Jump mechanics
- Speed adjustments based on urgency

**LookAtTargetSink:**
- Turns head to face targets
- Smooth rotation
- Maintains eye contact during interactions

**LookAndFollowPlayerSink:**
- Follows specific player
- Maintains distance (not too close/far)
- Jumps when player jumps
- Teleports if too far (configurable: 12 blocks)

**StrollAroundStayPoint:**
- Random wandering near home
- Stays within radius
- Returns to center periodically

**BackToStay:**
- Returns to commanded stay position
- Overrides other movement goals
- Uses pathfinding to return

### Special Movement

**RideHorse:**
- Villagers can mount horses they own
- Pathfinds to horse when needed
- Rides to destinations
- Dismounts at arrival

**StopRiding:**
- Dismounts from horses/mounts
- Returns mount to home position

**Teleportation:**
- Teleports to following player if distance > 12 blocks (configurable)
- Prevents getting lost during follow
- Configurable enable/disable

**Configuration:**
```yaml
teleport-when-following-if-far-away: true
teleport-when-following-distance: 12
```

### Stay System

**Stay Command:**
- Player commands villager to stay in place
- Overrides all other AI except panic
- Villager holds position
- Visual indicator (🔒 in nametag)

**ResetStayStatus:**
- Clears stay command
- Resumes normal AI
- Can be triggered by player interaction

**BackToStay:**
- If stay villager moves (pushed, knocked back)
- Automatically returns to stay position
- Maintains commanded location

---

## Trading System Modifications

### Realistic Trade Inventory

**Vanilla Behavior:**
- Infinite trade items
- Magical restocking
- No inventory limits

**RealisticVillagers Behavior:**

**Inventory-Based Trading:**
- Trades only available if items in inventory
- Finite stock based on work-generated items
- Visual inventory inspection via GUI

**Trade Restocking:**
- **Disabled vanilla restocking** (optional)
- **Work-based generation** replaces it
- Villagers must work to generate trade items
- Hunger must be satisfied to work

**Trading Configuration:**
```yaml
# trading-config.yml
realistic-trade-inventory:
  enabled: true
  check-actual-inventory: true
  
work-item-generation:
  enabled: true
  check-actual-trades: true  # Only generate tradeable items
```

**Trading Flow:**
1. Villager works at job site
2. Items generated and stored in inventory
3. Trades become available
4. Player trades items from inventory
5. Inventory depletes
6. Villager must work again to restock

### Trade Filtering

**InventoryTradeFilter:**
- Filters trade offers based on inventory
- Hides unavailable trades
- Updates in real-time
- Shows only possible trades

**FilteredTradeWrapper:**
- Wraps vanilla trading UI
- Applies inventory filter
- Maintains vanilla trade mechanics
- Custom trade completion events

---

## Death & Revival Systems

### Death Mechanics

**Vanilla Behavior:**
- Villagers die permanently
- Drop nothing or basic items
- No resurrection

**RealisticVillagers Behavior:**

**Death Handling:**
- **Equipment drops** (weapons, armor, tools)
- **Inventory drops** (optional, default: false)
- **Data persistence** for revival
- **Zombie infection** (optional, configurable)

**Zombie Transformation:**
```yaml
zombie-infection: true  # Converts to zombie villager on zombie kill
witch-convertion: true  # Lightning strike → witch
witch-convertion-from-villager-trident: false  # Channeling trident
```

**Death Data:**
- Stores offline data (inventory, equipment, family, etc.)
- Preserves relationships
- Maintains reputation
- Saves skin and appearance

### Revival System

**Cross Item:**
- Craftable revival token
- Given to villager before death
- Enables post-death revival

**Cross Recipe:**
```yaml
cross:
  crafting:
    shaped: true
    ingredients:
      - IRON_INGOT, I
      - STRING, S
    shape:
      - "SIS"
      - "III"
      - "SIS"
```

**Revival Process:**
1. **Preparation**: Give cross item to villager before death
2. **Death**: Villager dies with cross in inventory
3. **Monument Creation**: Totem monument spawns at death location
4. **Revival Ritual**: Player interacts with monument
5. **Resurrection**: Villager respawns with all data restored

**Monument Animation:**
- Multi-block totem structure
- Particle effects
- Sound effects
- Interaction prompt

**Revive Manager:**
- Tracks revivable villagers
- Manages monument locations
- Handles resurrection mechanics
- Restores all villager data:
  - Inventory
  - Equipment
  - Relationships (partner, family)
  - Reputation
  - Skin and appearance
  - Profession and level

**Configuration:**
```yaml
cross-reputation: 10  # Reputation gained for giving cross

revive:
  enabled: true
  monument-duration: 600  # Seconds before monument despawns
  head-item:  # Monument appearance
    material: PLAYER_HEAD
    url: "..."  # Skull texture
```

---

## Pet & Animal Interactions

### Pet Ownership

**TameOrFeedPet:**
- Villagers can tame animals
- Feed animals to breed them
- Own pets (cats, wolves, parrots, horses)

**Supported Pets:**
- **Cats** (PetCat)
- **Wolves** (PetWolf)
- **Parrots** (PetParrot)
- **Horses** (PetHorse, PetDonkey, PetMule)

### Pet Behaviors

**Pet AI:**
- Custom goal systems for each pet type
- Follow owner villager
- Defend owner from threats
- Sit/stand commands

**CatTemptGoal:**
- Cats follow villagers with fish
- Can be fed to gain trust

**HorseEating:**
- Horses eat hay when hungry
- Villagers feed horses

**Parrot Shoulder:**
- Parrots can perch on villager shoulders
- Rendered on player model
- `validShoulderEntityLeft()` / `validShoulderEntityRight()`

### Pet Management

**Taming:**
- Villagers use appropriate taming items
- Success chance based on item
- Pet links to villager UUID

**Breeding:**
- Villagers can breed animals
- Requires breeding items in inventory
- Creates baby animals

**RideHorse:**
- Villagers mount owned horses
- Travel faster over long distances
- Pathfind while mounted

---

## Comparison with Vanilla Villagers

### Appearance

| Feature | Vanilla | RealisticVillagers |
|---------|---------|-------------------|
| Model | Blocky villager | Player model |
| Customization | Profession robes only | Custom skins, sex, age |
| Equipment | None visible | Full armor & weapons |
| Nametag | Simple name | Multi-line with stats |

### AI & Behavior

| Feature | Vanilla | RealisticVillagers |
|---------|---------|-------------------|
| Intelligence | Simple schedules | Complex behavior trees |
| Communication | Sound effects only | AI-powered conversations |
| Decision Making | Predefined paths | Context-aware choices |
| Learning | None | Reputation-based adaptation |

### Survival

| Feature | Vanilla | RealisticVillagers |
|---------|---------|-------------------|
| Hunger | None | Full hunger system |
| Sleep | Required | Enhanced sleep mechanics |
| Inventory | Trading only | Full inventory management |
| Resource Gathering | None | Active gathering & storage |

### Combat

| Feature | Vanilla | RealisticVillagers |
|---------|---------|-------------------|
| Combat Ability | None (flee only) | Full combat system |
| Weapons | Cannot use | Swords, bows, tridents |
| Tactics | Panic and flee | Strategic combat AI |
| Defense | Iron golems only | Self-defense + cooperation |

### Social Systems

| Feature | Vanilla | RealisticVillagers |
|---------|---------|-------------------|
| Relationships | Basic gossip | Marriage, family, children |
| Reproduction | Automatic breeding | Consensual procreation |
| Player Interaction | Trading only | Chat, commands, gifts |
| Reputation | Trade prices | Full relationship system |

### Work & Profession

| Feature | Vanilla | RealisticVillagers |
|---------|---------|-------------------|
| Job Sites | Required | Enhanced work actions |
| Item Generation | Magic restocking | Work-based production |
| Limitations | None | Hunger and fatigue |
| Realism | Low | High (hunger, time, effort) |

### Lifecycle

| Feature | Vanilla | RealisticVillagers |
|---------|---------|-------------------|
| Birth | Instant spawn | Procreation process |
| Growth | Automatic | Timed with notifications |
| Death | Permanent | Revival system available |
| Transformation | Zombie/Witch | Same + data preservation |

---

## Summary of Major Behavior Changes

### Core Alterations

1. **Visual Transformation**
   - Player models replace villager models
   - Custom skin system with thousands of variations
   - Full equipment rendering

2. **Intelligence Upgrade**
   - AI-powered natural language conversations
   - Context-aware decision making
   - Tool-based action execution

3. **Survival Requirements**
   - Hunger system with food consumption
   - Sleep requirements
   - Resource management

4. **Combat Capabilities**
   - Full weapon and armor usage
   - Strategic combat behaviors
   - Cooperative defense systems

5. **Social Complexity**
   - Marriage and divorce
   - Procreation and child-raising
   - Family relationships and genealogy

6. **Work Realism**
   - Hunger-based work limitations
   - Physical item generation through labor
   - Profession-specific work actions

7. **Interaction Depth**
   - Multiple interaction types
   - Reputation-based relationships
   - Gift giving and receiving

8. **Lifecycle Management**
   - Birth through procreation
   - Timed growth periods
   - Death and resurrection systems

### Configuration Control

Nearly every behavior can be toggled or adjusted:
- Enable/disable entire systems
- Adjust cooldowns and thresholds
- Customize item generation
- Configure AI behavior
- Modify reputation values
- Tune combat parameters

This allows server administrators to tailor the villager experience from "slightly enhanced" to "fully realistic simulation."

---

## Technical Implementation Notes

**NMS Version Abstraction:**
- Multi-version support (1.18 - 1.21.10)
- Version-specific implementations in separate modules
- NMS converter interface for cross-version compatibility

**Performance Optimizations:**
- Folia threading support
- Async AI processing
- Efficient packet handling via PacketEvents
- Chunk-based entity tracking

**Data Persistence:**
- Custom serialization for villager data
- Offline data storage when villagers unload
- PDC (PersistentDataContainer) integration
- File-based save system

**Event System:**
- Custom villager events (VillagerFoodLevelChangeEvent, VillagerExhaustionEvent, etc.)
- API for external plugin integration
- Cancellable events for behavior customization

**Dependencies:**
- **Required**: PacketEvents (packet manipulation)
- **Optional**: ProtocolLib, ViaVersion, Geyser (compatibility)
- **AI**: OpenAI or Groq API (for conversations)

---

**End of Documentation**

*For configuration details, see CONFIG.md*  
*For command reference, see COMMANDS.md*  
*For API usage, see API.md*
