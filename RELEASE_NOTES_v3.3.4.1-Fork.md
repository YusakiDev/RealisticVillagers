# RealisticVillagers v3.3.4.1-Fork Release Notes

## 🎉 Major Reputation System Overhaul

This release includes a **complete overhaul of the reputation/gossip system** with enhanced reliability, data persistence, and Minecraft 1.21.7 compatibility.

---

## 🐛 Critical Fixes

### **Reputation System Persistence**
- **Fixed** reputation data not persisting across server restarts
- **Fixed** gossip entries being lost during server shutdown
- **Fixed** "No key Target" deserialization errors
- **Fixed** chunk serialization format conflicts causing data corruption

### **API Compatibility (Minecraft 1.21.7)**
- **Fixed** compilation errors with ValueInput API methods
- **Updated** `getDouble()` → `getDoubleOr()` with proper defaults
- **Updated** `getFloat()` → `getFloatOr()` with proper defaults  
- **Updated** `getByte()` → `getByteOr()` with proper defaults
- **Updated** `getBoolean()` → `getBooleanOr()` with proper defaults
- **Fixed** CompoundTag `putUUID()` method calls using NMSConverter

### **Data Serialization**
- **Fixed** IntArrayTag handling for UUID Target fields
- **Fixed** missing Target field during gossip loading
- **Fixed** format inconsistency between save/load operations
- **Implemented** classloader-safe fallback serialization mechanisms

---

## ✨ New Features & Improvements

### **Enhanced Gossip System**
- **New** chunk-compatible serialization format
- **New** backward compatibility with legacy gossip data
- **New** robust UUID handling with multiple format support
- **New** comprehensive error handling and recovery mechanisms

### **Improved Debugging**
- **Added** essential error logging for reputation system monitoring
- **Removed** excessive debug output for cleaner server logs
- **Enhanced** exception handling with detailed error messages
- **Improved** data validation during load operations

### **Performance Optimizations**
- **Optimized** gossip data loading/saving operations
- **Reduced** memory overhead from excessive logging
- **Improved** startup time with streamlined data processing
- **Enhanced** server shutdown handling to prevent data loss

---

## 🔧 Technical Changes

### **Serialization Architecture**
- **Redesigned** gossip storage using chunk-compatible string UUIDs
- **Implemented** dual-format support (new Entity format + legacy Gossip format)
- **Added** type-safe serialization practices throughout codebase
- **Enhanced** fallback mechanisms for data corruption scenarios

### **Code Quality Improvements**
- **Refactored** UUID handling to use consistent IntArrayTag format internally
- **Eliminated** unsafe type casting with proper DynamicOps usage
- **Standardized** error handling patterns across reputation system
- **Improved** code maintainability with cleaner separation of concerns

---

## 📋 Migration Notes

### **Automatic Data Migration**
- ✅ **No action required** - existing reputation data is automatically migrated
- ✅ **Backward compatible** - supports both old and new data formats
- ✅ **Safe upgrade** - fallback mechanisms protect against data loss

### **Server Requirements**
- **Minecraft**: 1.21.7
- **Java**: 16+ (unchanged)
- **Bukkit/Spigot**: Compatible version for MC 1.21.7

---

## 🧪 Testing Performed

### **Reputation System**
- ✅ Player reputation persists correctly across server restarts
- ✅ Villager interactions properly update reputation values
- ✅ Gossip data loads without errors or data loss
- ✅ Multiple players' reputations tracked independently

### **Compatibility Testing**
- ✅ Maven compilation succeeds without errors
- ✅ Plugin loads successfully on Minecraft 1.21.7 servers
- ✅ No chunk serialization warnings in server logs
- ✅ Backward compatibility with existing reputation data

### **Performance Testing**
- ✅ Server startup time improved with reduced debug logging
- ✅ Memory usage optimized during gossip operations
- ✅ No performance degradation during reputation calculations

---

## 🎯 Known Issues Resolved

| Issue | Status | Description |
|-------|--------|-------------|
| Build Failure | ✅ **FIXED** | Maven compilation errors with ValueInput methods |
| Reputation Reset | ✅ **FIXED** | Reputation showing 0 after server restart |
| Classloader Error | ✅ **FIXED** | "zip file closed" during gossip serialization |
| Target Field Missing | ✅ **FIXED** | UUID Target field lost during NBT operations |
| Chunk Serialization | ✅ **FIXED** | Format mismatch causing serialization warnings |
| Type Safety | ✅ **FIXED** | Unsafe casting in manual serialization methods |

---

## 🚀 Deployment Instructions

1. **Backup** your current RealisticVillagers data (recommended)
2. **Stop** your Minecraft server
3. **Replace** the old plugin JAR with `realisticvillagers-v1_21_7-3.3.4.1-Fork.jar`
4. **Start** your server
5. **Verify** reputation system functionality in-game

---

## 💬 Support

If you encounter any issues with this release:
1. Check server logs for error messages
2. Verify you're running Minecraft 1.21.7
3. Ensure Java 16+ is installed
4. Report issues with complete error logs and reproduction steps

---

## 🙏 Credits

Special thanks to the community for reporting reputation system issues and providing detailed debugging information that made this comprehensive fix possible.

---

**Full Changelog**: Comprehensive reputation system overhaul with Minecraft 1.21.7 compatibility improvements and enhanced data persistence reliability.