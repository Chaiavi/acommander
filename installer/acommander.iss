; Built by the buildInstaller Gradle task: ISCC /DAppVersion=<version> /DSourceDir=<dist folder>
; Per-user only: the app writes its settings, logs and tool updates into its own folder, so it can't live in Program Files.

[Setup]
AppId={{1D54CF8C-10E1-4E2A-BD4D-5F4CFE658C1C}
AppName=ACommander
AppVersion={#AppVersion}
AppPublisher=Chaiware.org
AppPublisherURL=https://github.com/Chaiavi/acommander
DefaultDirName={autopf}\ACommander
DisableProgramGroupPage=yes
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
SetupIconFile=..\acommander.ico
UninstallDisplayIcon={app}\acommander.exe
WizardStyle=modern
SolidCompression=yes

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[InstallDelete]
; A new Java runtime may drop files; old ones must not linger
Type: filesandordirs; Name: "{app}\runtime"

[Files]
Source: "{#SourceDir}\*"; DestDir: "{app}"; Excludes: "\acommander-v*.zip,\acommander-setup-v*.exe,\apps\extract_all\UniExtract\UniExtract.ini,\apps\multi_rename\Renamer.xml"; Flags: ignoreversion recursesubdirs createallsubdirs
; The tools rewrite these settings (toolSettingsTemplates in build.gradle): an upgrade keeps the user's
Source: "{#SourceDir}\apps\extract_all\UniExtract\UniExtract.ini"; DestDir: "{app}\apps\extract_all\UniExtract"; Flags: onlyifdoesntexist
Source: "{#SourceDir}\apps\multi_rename\Renamer.xml"; DestDir: "{app}\apps\multi_rename"; Flags: onlyifdoesntexist

[Icons]
Name: "{autoprograms}\ACommander"; Filename: "{app}\acommander.exe"; WorkingDir: "{app}"
Name: "{autodesktop}\ACommander"; Filename: "{app}\acommander.exe"; WorkingDir: "{app}"; Tasks: desktopicon

[Run]
Filename: "{app}\acommander.exe"; Description: "{cm:LaunchProgram,ACommander}"; Flags: nowait postinstall skipifsilent

[UninstallDelete]
; Settings, logs and tool updates live in the install folder too
Type: filesandordirs; Name: "{app}"
