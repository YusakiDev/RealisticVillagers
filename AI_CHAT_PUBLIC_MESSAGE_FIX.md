# AI Chat Public Message Display Fix

## Problem
When `public-conversations` was enabled in `ai-config.yml`, nearby players viewing the conversation would see messages like:
```
[You → Bob] Hello!
[Bob → You] Hi there!
```

Instead of:
```
[Steve → Bob] Hello!
[Bob → Steve] Hi there!
```

The word "You" was hardcoded, making it confusing for spectators who weren't part of the conversation.

## Root Cause

The `broadcastMessageToNearbyPlayers()` method was sending the **same message** to everyone:

```java
// OLD CODE - Same message for everyone
String playerMessage = "[You → Bob] Hello!";
broadcastMessageToNearbyPlayers(player, npc, playerMessage, aiManager);
```

## Solution

Create **two different messages**:
1. **Speaker message** - Shows "You" (personalized for the person talking)
2. **Nearby message** - Shows the actual player name (for spectators)

### Code Changes

**File**: `core/src/main/java/me/matsubara/realisticvillagers/listener/AIConversationListener.java`

#### 1. Player's Message to Villager

```diff
- String playerMessage = "[You → VillagerName] message";
- broadcastMessageToNearbyPlayers(player, npc, playerMessage, aiManager);

+ // Send different messages to speaker vs nearby players
+ String speakerMessage = "[You → VillagerName] message";
+ String nearbyMessage = "[PlayerName → VillagerName] message";
+ broadcastMessageToNearbyPlayers(player, npc, speakerMessage, nearbyMessage, aiManager);
```

#### 2. Villager's Response to Player

```diff
- String villagerMessage = "[VillagerName → You] response";
- broadcastMessageToNearbyPlayers(player, npc, villagerMessage, currentManager);

+ // Send different messages to speaker vs nearby players
+ String speakerMessage = "[VillagerName → You] response";
+ String nearbyMessage = "[VillagerName → PlayerName] response";
+ broadcastMessageToNearbyPlayers(player, npc, speakerMessage, nearbyMessage, currentManager);
```

#### 3. Update Broadcast Method

```diff
- private void broadcastMessageToNearbyPlayers(..., String message, ...) {
+ private void broadcastMessageToNearbyPlayers(..., String speakerMessage, String nearbyMessage, ...) {
-     speaker.sendMessage(message);
+     speaker.sendMessage(speakerMessage);
      
      for (Player nearbyPlayer : world.getPlayers()) {
          ...
-         nearbyPlayer.sendMessage(message);
+         nearbyPlayer.sendMessage(nearbyMessage);
      }
  }
```

## Example Output

### Before Fix:
**Steve talking to Bob the villager (Steve sees):**
```
[You → Bob] Can you follow me?
[Bob → You] Of course! I'd be happy to help you at the farm.
```

**Nearby player Alice sees:**
```
[You → Bob] Can you follow me?          ❌ Confusing!
[Bob → You] Of course! I'd be happy to help you at the farm.  ❌ Who is "You"?
```

### After Fix:
**Steve talking to Bob the villager (Steve sees):**
```
[You → Bob] Can you follow me?
[Bob → You] Of course! I'd be happy to help you at the farm.
```

**Nearby player Alice sees:**
```
[Steve → Bob] Can you follow me?        ✅ Clear!
[Bob → Steve] Of course! I'd be happy to help you at the farm.  ✅ Clear!
```

## Configuration

This fix applies when `public-conversations` is enabled:

```yaml
conversation:
  # When true, nearby players can see the conversation
  public-conversations: true
  
  # Radius for public conversation visibility
  public-radius: 15.0
```

## Private Conversations

When `public-conversations: false`, only the talking player sees messages, and "You" is appropriate:
```
[You → Bob] Hello
[Bob → You] Hi there!
```

## Benefits

✅ **Clear context** - Spectators know who is talking to whom  
✅ **Immersive** - Feels like overhearing a real conversation  
✅ **Consistent** - Speaker always sees "You", spectators see names  
✅ **No confusion** - No more "who is 'You'?" questions  

## Related Files

- `AIConversationListener.java` - Main chat listener
- `ai-config.yml` - Configuration for public conversations
- `AIConversationManager.java` - Conversation state management

---

**Status**: ✅ Fixed  
**Date**: 2025-11-19  
**Issue**: Public conversation messages showing "You" to all players  
**Solution**: Send personalized messages to speaker vs nearby players  
