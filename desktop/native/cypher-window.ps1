# Finds the real Cypher window instead of trusting Process.MainWindowHandle, which can
# point at a hidden/owned AWT window and produced clicks and captures at wrong rectangles.

if (-not ('CW.Windows' -as [type])) {
    Add-Type -TypeDefinition @'
using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using System.Text;

namespace CW
{
    public struct RECT { public int Left; public int Top; public int Right; public int Bottom; }

    public static class Windows
    {
        public delegate bool EnumProc(IntPtr hWnd, IntPtr lParam);

        [DllImport("user32.dll")] public static extern bool EnumWindows(EnumProc callback, IntPtr lParam);
        [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr hWnd);
        [DllImport("user32.dll", CharSet = CharSet.Unicode)] public static extern int GetWindowText(IntPtr hWnd, StringBuilder text, int count);
        [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hWnd, out RECT rect);
        [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);

        public static List<IntPtr> ByTitle(string title)
        {
            List<IntPtr> found = new List<IntPtr>();
            EnumWindows(delegate(IntPtr hWnd, IntPtr lParam)
            {
                if (!IsWindowVisible(hWnd)) return true;
                StringBuilder text = new StringBuilder(256);
                GetWindowText(hWnd, text, text.Capacity);
                if (text.ToString() == title) found.Add(hWnd);
                return true;
            }, IntPtr.Zero);
            return found;
        }
    }
}
'@
}

function Get-CypherWindow {
    param([string]$Title = 'Cypher')
    $best = [IntPtr]::Zero
    $bestArea = 0
    $bestRect = New-Object CW.RECT
    foreach ($handle in [CW.Windows]::ByTitle($Title)) {
        $rect = New-Object CW.RECT
        [CW.Windows]::GetWindowRect($handle, [ref]$rect) | Out-Null
        $area = ($rect.Right - $rect.Left) * ($rect.Bottom - $rect.Top)
        if ($area -gt $bestArea) {
            $best = $handle
            $bestArea = $area
            $bestRect = $rect
        }
    }
    if ($best -eq [IntPtr]::Zero) { return $null }
    return [pscustomobject]@{
        Handle = $best
        Left   = $bestRect.Left
        Top    = $bestRect.Top
        Width  = $bestRect.Right - $bestRect.Left
        Height = $bestRect.Bottom - $bestRect.Top
    }
}
