# ⚡ A Commander

### (A)nother Dual Pane File Explorer

A modern take on Norton Commander for Windows — streamlined, opinionated, and built around the tools you already love.

[![Windows](https://img.shields.io/badge/platform-Windows-0078D4?logo=windows&logoColor=white)](https://github.com/Chaiavi/acommander)
[![Java](https://img.shields.io/badge/language-Java-ED8B00?logo=openjdk&logoColor=white)](https://github.com/Chaiavi/acommander)


---

## 🎯 Philosophy

A Commander is **not** a pure Norton Commander clone. It keeps what works, drops what doesn't, and delegates specialized tasks to best-in-class external tools.

> **Core principle:** Files are the focus. Everything else gets out of the way.

### Design Decisions

- ✅ Classic NC keyboard shortcuts preserved (`F1`–`F10`)
- ✅ Command Palette replaces the traditional top menu
- ✅ External tools handle what they do best (see below)
- ❌ No NCD — removed features that didn't add value
- ❌ Minimal configuration — opinionated defaults over endless settings

---

## 🔧 External Tool Integration

A Commander delegates specialized operations to dedicated tools, giving you professional-grade functionality without reinventing the wheel:

| Key | Action | Tool |
|-----|--------|------|
| `F3` | View | [Universal Viewer](https://www.uvviewsoft.com/) |
| `F4` | Edit | [Notepad4](https://github.com/zufuliu/notepad4) |
| `F5` | Copy | [FastCopy](https://fastcopy.jp/) |
| `F6` | Move | [FastCopy](https://fastcopy.jp/) |
| `F9` | Terminal | PowerShell / Windows CMD |
| — | Pack / Unpack | [7-Zip](https://www.7-zip.org/) |

---

## 💡 Smart Behaviors

A Commander is opinionated — it makes decisions so you don't have to:

| Scenario | What Happens |
|----------|--------------|
| 🔒 File can't be deleted | Automatically sent to [ThisIsMyFile](https://www.yourownnet.net/en/thisismyfile/) for unlocking + deletion |
| 📝 Batch rename files | Handed off to [Ant Renamer](https://www.antp.be/software/renamer) |
| 👁️ Hidden files | Always visible — no toggle needed |

---

## 🏗️ Development Guidelines

| Guideline | Rationale |
|-----------|-----------|
| Keep the code **super simple** | Maintainability over cleverness |
| Don't worry about storage | This isn't Volkov Commander 😄 |
| **Windows only** | No cross-platform compromises |
| **Files first** | Every feature serves file management |

---

## 🚀 Getting Started

```bash
git clone https://github.com/Chaiavi/acommander.git
cd acommander
```

> *See the repo for build and run instructions.*

---

**[⭐ Star this repo](https://github.com/Chaiavi/acommander)** if you find it useful!

