param([string]$Program,[string]$Arguments)
$ErrorActionPreference='Stop'
Add-Type -TypeDefinition @"
using System; using System.Runtime.InteropServices; using System.Text;
public static class UserDesktopLaunch {
[StructLayout(LayoutKind.Sequential, CharSet=CharSet.Unicode)] public struct STARTUPINFO {public int cb; public string reserved; public string desktop; public string title; public int x,y,xsize,ysize,xchars,ychars,fill,flags; public short show,reserved2; public IntPtr reservedPtr,input,output,error;}
[StructLayout(LayoutKind.Sequential)] public struct PROCESS_INFORMATION { public IntPtr process,thread; public uint processId,threadId; }
[DllImport("kernel32.dll",CharSet=CharSet.Unicode,SetLastError=true)] public static extern bool CreateProcess(string application,StringBuilder command,IntPtr pa,IntPtr ta,bool inherit,uint flags,IntPtr env,string cwd,ref STARTUPINFO start,out PROCESS_INFORMATION info);
[DllImport("kernel32.dll")] public static extern bool CloseHandle(IntPtr h);
public static uint Launch(string app,string args) { var s=new STARTUPINFO(); s.cb=Marshal.SizeOf(s); s.desktop=@"winsta0\default"; PROCESS_INFORMATION p; if(!CreateProcess(app,new StringBuilder("\""+app+"\" "+args),IntPtr.Zero,IntPtr.Zero,false,0,IntPtr.Zero,null,ref s,out p)) throw new System.ComponentModel.Win32Exception(Marshal.GetLastWin32Error()); CloseHandle(p.process); CloseHandle(p.thread); return p.processId; }
}
"@
[UserDesktopLaunch]::Launch($Program,$Arguments)