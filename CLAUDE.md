# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build and Development Commands

### Maven Build Commands
- `mvn clean compile` - Compile all modules
- `mvn clean package` - Build the complete plugin with all version modules
- `mvn clean install` - Install to local repository
- `mvn dependency:tree` - View dependency tree

### Testing the Plugin
- The plugin requires a Minecraft server (Spigot/Paper) version 1.18+ to test
- Output JAR will be in `dist/target/` after building
- Main command: `/realisticvillagers` (aliases: `/rv`, `/rvillagers`, `/realvillagers`)

## Project Architecture

### Multi-Module Maven Structure
This is a multi-version Bukkit/Spigot plugin with separate modules for different Minecraft versions:
- `core/` - Main plugin logic and shared code
- `v1_18/`, `v1_19/`, `v1_20_1/`, `v1_20_2/`, `v1_20_4/`, `v1_20_6/`, `v1_21/`, `v1_21_4/` - Version-specific NMS implementations
- `dist/` - Distribution module that combines all versions

### Core Architecture Components

#### Main Plugin Class
- `RealisticVillagers.java` - Main plugin class with initialization, config management, and core functionality

#### NMS Version Abstraction
- `INMSConverter` interface - Abstracts version-specific Minecraft server code
- Each version module implements `NMSConverter` for that specific version
- Uses reflection to load the appropriate implementation based on server version

#### Entity System
- `IVillagerNPC` interface - Core villager NPC abstraction
- Version-specific implementations in each version module (e.g., `VillagerNPC`, `OfflineVillagerNPC`)
- Supports advanced AI behaviors, combat, relationships, and interactions

#### Key Managers
- `VillagerTracker` - Tracks and manages villager data across worlds
- `GiftManager` - Handles gift system and villager preferences
- `ReviveManager` - Manages villager revival mechanics
- `CompatibilityManager` - Handles compatibility with other plugins
- `ExpectingManager` - Manages villager expectations for gifts and interactions
- `AIService` - Handles AI-powered conversations with villagers (EXPERIMENTAL)

#### Configuration System
- Uses custom config updater that preserves user settings while adding new options
- Supports complex config migrations with version tracking
- Configuration files: `config.yml`, `messages.yml`, `names.yml`, `ai-config.yml`, plus skin files

#### Event System
- Custom events for villager interactions (marriage, gifts, combat, etc.)
- Extensive listener system for player and villager interactions

### Key Features Implemented
- Advanced villager AI with custom behaviors (combat, relationships, work)
- Multi-version support (1.18-1.21.4)
- Custom skin system with male/female variants
- Marriage and family system for villagers
- Advanced combat system with equipment
- Plugin compatibility system
- Extensive GUI system for villager management
- **AI Chat Integration (EXPERIMENTAL)** - Anthropic Claude API integration for natural conversations

## AI Chat Integration (EXPERIMENTAL)

### Overview
The plugin includes an experimental AI chat system that allows players to have natural conversations with villagers using the Anthropic Claude API. This feature is disabled by default and requires configuration.

### Configuration
- **Config File**: `ai-config.yml` - Contains all AI-related settings
- **API Requirements**: Requires valid Anthropic API key
- **Recommended Model**: `claude-3-5-haiku-latest` for balance of speed and cost

### Chat Modes
1. **Natural Chat**: Use `@VillagerName message` to chat with specific villagers within range
2. **Legacy Chat Sessions**: Traditional session-based chat mode
3. **Auto-Chat**: Villagers can respond to any nearby chat automatically

### Key Components
- `AIService` - Core service handling API communication and chat logic
- `AIChatListeners` - Event handlers for chat interactions and gift reactions
- `AIResponseParser` - Parses AI responses including tool calls and text
- Tool system allowing villagers to perform actions (follow, give items, etc.)

### Features
- **Profession-based personalities** - Each villager profession has unique behavior traits
- **Tool calling** - Villagers can perform in-game actions based on AI decisions
- **Natural gift reactions** - AI-powered responses to player gifts
- **Multi-language support** - Responds in the player's language
- **Rate limiting** - Configurable cooldowns to prevent spam
- **Memory system** - Villagers remember recent conversations

### Safety Features
- Rate limiting to prevent API abuse
- Tool usage restrictions and cooldowns
- Configurable response length limits
- Optional content filtering

### Important Notes
- **EXPERIMENTAL**: Feature is subject to change and may have bugs
- **Cost considerations**: AI API calls cost money - monitor usage
- **Performance impact**: Network requests may cause minor delays
- **Privacy**: Chat messages are sent to Anthropic's API

### Development Notes
- Uses PacketEvents for packet manipulation
- Requires Java 16+ (specified in pom.xml)
- Uses Lombok for code generation
- Extensive use of reflection for version compatibility
- Custom NMS implementations for each supported version

### Important Dependencies
- PacketEvents (required dependency)
- Spigot/Paper API
- AuthLib for skin handling
- AnvilGUI for advanced GUI interactions

When working with this codebase, pay attention to the version-specific modules and ensure changes maintain compatibility across all supported Minecraft versions.