# Changes Since 1.21.8 Update

## 🎮 Folia Support
**Full Folia compatibility added** through FoliaLib integration:
- **FoliaLib v0.5.1** - Cross-platform scheduler support
- **Region-based scheduling** - All entity operations use proper Folia schedulers
- **Thread-safe operations** - NPCs, trades, and equipment work across regions
- **Automatic detection** - Works on both Paper/Spigot and Folia servers

## 🤖 AI System Enhancements
- **Multiple AI Providers** - Support for Anthropic Claude and OpenAI
- **Enhanced Talk Mode** - Natural chat with `@VillagerName` syntax
- **Home Tools** - AI villagers can now go home when asked
- **Improved Response Parsing** - Better handling of tool calls and responses

## 💰 Trading & Economy Systems
- **Inventory-Based Trading** - Villagers check inventory before trading
- **Physical Item Delivery** - Items physically travel to recipients
- **Trade Completion Tracking** - Monitor and react to completed trades
- **Stock Management** - Configurable stock multipliers per profession

## 🍖 Work & Hunger System
- **Hunger Mechanics** - Villagers consume food during work
- **Work Refusal** - Won't work when hungry or enslaved
- **Anti-Enslavement** - Detect and refuse to work in confined spaces
- **Inventory Cleanup** - Automatically remove useless items during work

## ⚔️ Combat & Equipment
- **Equipment Request System** - Villagers share gear during threats
- **Threat-Based Equipment** - Auto-equip based on danger level
- **Alert System** - Track and debug villager threat states
- **Better Equipment Distribution** - Smart sharing of weapons and armor

## 🔌 Integrations
- **LamCore Integration** - Full reputation system support
- **Reputation Providers** - Pluggable reputation system architecture
- **Enhanced Compatibility** - Better integration with other plugins

## 🗂️ New Configuration Files
- `gui-config.yml` - GUI customization settings
- `trading-config.yml` - Trading behavior configuration
- `work-hunger-config.yml` - Work and hunger mechanics

## 🐛 Bug Fixes & Improvements
- Fixed invisible NPC issues
- Improved UUID handling in NMSConverter
- Better packet handling and data management
- Reduced log spam (combat/enslavement messages now at FINE level)
- Fixed villager focus and nametag display issues
- Enhanced breeding mechanics for animals

## 🏗️ Technical Changes
- **Removed older versions** - Dropped v1_21 and v1_21_4 modules
- **SafeEntityDataAPI** - New entity data management system
- **Enhanced NPCPool** - Better NPC tracking and management
- **Debug Commands** - Added `/rv debug-alerts` and `/rv clear-alerts`