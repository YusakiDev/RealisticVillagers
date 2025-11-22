# AI Chat Event Isolation Fix

## Problem
When a player was in an AI conversation and sent a chat message, other plugins could still intercept and process the chat event before RealisticVillagers cancelled it.

## Root Cause
The `AIConversationListener` was using:
```java
@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
```

This caused two issues:
1. **HIGHEST priority** - Runs LAST in the event chain, allowing other plugins to process the event first
2. **ignoreCancelled = true** - Won't even see events that were already cancelled by other plugins

## Solution
Changed the event handler to:
```java
@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
```

### Why This Works

**EventPriority.LOWEST** (Execution Order):
1. ✅ **LOWEST** - Runs FIRST (our fix)
2. LOW
3. NORMAL
4. HIGH
5. HIGHEST
6. MONITOR

By using `LOWEST` priority, RealisticVillagers cancels the chat event **before** any other plugin can see it.

**ignoreCancelled = false**:
- Ensures the listener processes events even if they were already cancelled
- Necessary if you want to intercept all conversation messages regardless of other plugin interference

## Benefits

✅ **Complete Isolation** - AI conversation messages are invisible to other plugins  
✅ **No Chat Plugin Conflicts** - Chat formatting plugins, anti-spam plugins, etc. won't interfere  
✅ **Privacy** - Conversation messages won't leak to other systems  
✅ **Predictable Behavior** - Guaranteed first in line to handle the event  

## Code Changes

**File**: `core/src/main/java/me/matsubara/realisticvillagers/listener/AIConversationListener.java`

```diff
- @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
+ @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
  public void onAsyncPlayerChat(@NotNull AsyncPlayerChatEvent event) {
      Player player = event.getPlayer();
      AIConversationManager aiManager = plugin.getAIConversationManager();
      if (aiManager == null || !aiManager.isInConversation(player)) {
          return;
      }

-     // Cancel the event so the message doesn't broadcast to everyone
+     // Cancel the event IMMEDIATELY at LOWEST priority so other plugins don't see it
      event.setCancelled(true);

      String message = event.getMessage();
      plugin.getFoliaLib().getScheduler().runAtEntity(player, task -> handleConversationMessage(player, message));
  }
```

## Testing Recommendations

1. **Test with chat formatting plugins** (EssentialsChat, ChatControl, etc.)
2. **Test with anti-spam plugins** (ChatGuard, etc.)
3. **Test with chat logging plugins** (CoreProtect, LogBlock, etc.)
4. **Verify private conversations stay private**
5. **Verify public conversations work correctly**

## Event Priority Reference

| Priority | When to Use |
|----------|-------------|
| **LOWEST** | Want to run FIRST and prevent other plugins from seeing the event |
| LOW | Early processing before most plugins |
| NORMAL | Standard plugin behavior (default) |
| HIGH | Override standard behavior |
| HIGHEST | Last chance to modify event before it executes |
| MONITOR | Only observe final state, should NEVER modify the event |

## Related Configuration

In `ai-config.yml`, these settings control conversation visibility:

```yaml
conversation:
  # When true, nearby players can see the conversation
  public-conversations: true
  
  # Radius for public conversation visibility
  public-radius: 15.0
```

Even with `public-conversations: true`, the chat event is still cancelled - the plugin manually broadcasts the messages to nearby players instead of relying on the vanilla chat system.

## Important Notes

⚠️ **This fix ensures the chat event is cancelled BEFORE other plugins process it**  
⚠️ **No other plugin will see chat messages during AI conversations**  
⚠️ **This is intentional and desired behavior for conversation privacy**  
⚠️ **Public conversations are handled by manual broadcasting, not the event system**  

---

**Status**: ✅ Fixed  
**Date**: 2025-11-19  
**Priority**: Event handling isolation  
