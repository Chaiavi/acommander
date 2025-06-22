ICLView.wlx
v21.8.2009
Free for noncommercial use.

Lister plugin for Total Commander. Show icons in files.
Supported extentions: ico, icl, exe, dll, scr, ocx, bpl, wlx, wfx, wcx, wdx, cpl, acm.

WARNING! Remove previously installed plugin from lister plugins list (it will show icons in ico files using TC 6 or later and new file types too).

Install (TC 5.51 or later):

1. Uninstall previous version of plugin if exist.
2. Select Configuration -> Options... -> Edit/View tab.
3. Press button "Configure internal viewer...".
4. In "Configure Lister" window press button "LS-Plugins". Window "Lister Plugins" will be shown.
5. Press "Add" button and select ICLView.wlx.
6. Press "OK" button in standard File Select Dialog ("Browse" title) and "Lister Plugins" (in next windows you can press "Cancel" if want).

Try use plugin - select *.icl file and press F3, in Lister window select Options -> Image/Multimedia if need.


Small FAQ.

Q. What the items who contains zero icons?
A. This compressed icon (cannot be extrtacted) or icon who contain only valid header without image (can be extracted).
Q. I can't see icons in exe, dll files. What can I do?
A. If started other plugin first then try press button 4 in Lister.
Q. Can I open Stardock Icon Packager icon files (*.ip)?
A. Stardock Icon Packager icon files (*.ip) is zip archives contained *.icl file inside. Enter to *.ip file by pressing Ctrl-PgDown in TC and see (by F3) *.icl file. (Answer by Wuyko WINDOWS).

Changes.

 v9.1.2005
 + view *.ico files in TC 6 or later;
 + saving single icons to *.bmp (bpp sets by default!);
 + deleting icons from *.icl files by Del button pressing;
 + all icons may be saved as *.icl;
 * some internal changes.

 v28.1.2005
 + hint about sizes and bpp icons in lib;
 * when save icon from lib by default filename will be set to "number - name.ico";
 * some internal changes.

 v16.4.2005
 * fixed "Out of rersources" bug;
 + new file types added: scr, ocx, bpl, wlx, wfx, wcx, wdx, cpl, acm;
 * some internal changes.

 v18.4.2005
 - hints removed;
 + Editor mode:
  + renaming icons in libraries;
  + adding icons to libraries;
  + rearranging icons in libraries dragging by mouse;
 * some internal changes.

 v4.6.2005
 * fixed automatic rename (manual renaming lost) icons in ICL files when saving;
 * improved support of big icons (Win XP only);
 * DetectString changed to 'MULTIMEDIA & ext="ICL" | ext="ICO" | force';
 * some internal changes.

 v25.9.2005
 * improved sort order of big icons (Win XP only);
 * fix next plugin window focus losing (when button 4 pressed);
 * some internal changes.

 v28.3.2006 Beta
 * max item height is 255 pixels now for best view big (256 x 256) icons;
 + search icons by name in libraries;
 + horisontal scroll bar for big or many icons (note: some draw errors for selected icons);
 + png-compressed icons support;
 + new view mode for libraries;
 + buttons for view next/previous same type files in directory;
 * some changes.

 v22.4.2006
 + view mode is stored now;
 + new thumbnails view options: draw border, draw icon in center of thumb;
 + select thumbnails view sizes: (32x32, 48x48, 96x96 etc.);
 + separetely thumbnails sizes and view mode save for Quick View and Normal view modes;
 + key for plugin setup file "StartMaximized" for maximize Lister window when plugin starts;
 + Select Duplicates function for lib's (compares icons by content and select dup's, ex. if 2 same icons in lib then second of it only will be selected);
 * default View mode for Quick View is thumbnails;
 * thumbnails draw improvements;
 * BPP for compressed icons correctly showed;
 * some changes and improvements.

 v26.6.2006
 * improved draw 1bit icons in thumbnail mode;
 * changed back arrow draw of non-selected items (don't draw it).

 v3.10.2006
 + new menu item "View Icon...";
 + full support of icon transparency and alpha channel when save to PNG.

 v5.5.2009
 + view 16bit icl files in Vista (and possible in Windows 7);
 * fix many icons draw corruptions.

 v21.8.2009
 * Delphi virus fix;
 + save to 32bit icl files;
 + show library is 32 or 16 bit in status bar;
 * height of status bar fix.


Initial developer: Pavel (pavel_r2003@mail.ru)

Contact the author:
ProgMan13@mail.ru