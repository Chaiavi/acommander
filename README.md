# A Commander - (A)nother Dual Pane File Explorer
A Norton Commander clone

- Attempted to keep the main key bindings (f1-f10) etc.
- This is not a pure nc clone, so I ditched the stuff I didn't find helpful (ncd??)
- Removed the upper menu (will use a command palette instead)
- Special functionality which could be done using 3rd party tools is done by them, for example:
  - View (f3) using QuickLook
  - Edit (f4) using Notepad4/TedNPad
  - Copy/Move (f5-f6) using fastcopy
  - Terminal (f9) using PowerShell/Windows Command
  - Pack/Unpack using 7z
- Command Palette for any function!

### Main guidelines
- Keep the code super simple.
- Storage space is not an issue (This is not volkov commander :-) )
- Windows only applications
- Application should attempt to only work around files, that is the main focus
- Opinionated app, less configurations, more give-the-user-what-s/he-needs, for example:
  - File which can't be deleted, will be sent to ThisIsMyFile for unlocking+deleting
  - Renaming of multiple files will be sent to AntRenamer
  - Hidden files will always be shown (This tool is for advanced users...)
