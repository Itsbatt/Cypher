using System;
using System.Globalization;
using System.IO;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;

// Cypher SMTC bridge.
//
// This helper owns a Windows.Media.Playback.MediaPlayer instance (created through
// WinRT activation, no XAML and no window) and exposes its
// SystemMediaTransportControls session so Windows shows Cypher in the media flyout.
//
// It is compiled against .NET Framework only and resolves the WinRT types by name
// at runtime, so it needs no Windows SDK metadata at build time.
//
// Protocol (UTF-8 lines):
//   in : meta|title|artist|album|artPath
//        status|Playing|Paused|Stopped|Closed
//        timeline|positionMs|durationMs
//        quit
//   out: ready
//        button|Play|Pause|Next|Previous|Stop
//        error|message
public static class SmtcHelper
{
    private static readonly object OutLock = new object();
    private static object smtc;
    private static object displayUpdater;
    private static object musicProperties;
    private static Type timelineType;
    private static Type playbackStatusType;
    private static Type mediaPlaybackType;
    private static string lastArtPath;

    private static void Emit(string line)
    {
        lock (OutLock)
        {
            Console.Out.WriteLine(line);
            Console.Out.Flush();
        }
    }

    private static Type WinRt(string typeName, string ns)
    {
        Type type = Type.GetType(typeName + ", " + ns + ", ContentType=WindowsRuntime");
        if (type == null)
        {
            throw new InvalidOperationException("WinRT type not found: " + typeName);
        }
        return type;
    }

    private static object Get(object target, string property)
    {
        PropertyInfo info = target.GetType().GetProperty(property);
        if (info == null)
        {
            throw new MissingMemberException(target.GetType().FullName, property);
        }
        return info.GetValue(target, null);
    }

    private static void Set(object target, string property, object value)
    {
        PropertyInfo info = target.GetType().GetProperty(property);
        if (info == null)
        {
            throw new MissingMemberException(target.GetType().FullName, property);
        }
        info.SetValue(target, value, null);
    }

    private static void SetEnum(object target, string property, Type enumType, string name)
    {
        PropertyInfo info = target.GetType().GetProperty(property);
        if (info == null)
        {
            throw new MissingMemberException(target.GetType().FullName, property);
        }
        info.SetValue(target, Enum.Parse(enumType, name), null);
    }

    /**
     * Waits for an IAsyncOperation<T> using its own Status/GetResults surface.
     * `System.Runtime.WindowsRuntime` is not part of every .NET Framework install, and
     * depending on it made album artwork fail on machines where it is absent.
     */
    private static object Await(object operation, Type resultType)
    {
        if (operation == null)
        {
            throw new ArgumentNullException("operation");
        }
        PropertyInfo status = operation.GetType().GetProperty("Status");
        MethodInfo getResults = operation.GetType().GetMethod("GetResults");
        if (status == null || getResults == null)
        {
            // Some projections expose the operation as a bare __ComObject; AsTask can still
            // bridge it. The assembly is not in the GAC on every install, so load it from
            // the framework directory the process is already running on.
            Assembly runtime = null;
            string candidate = Path.Combine(RuntimeEnvironment.GetRuntimeDirectory(), "System.Runtime.WindowsRuntime.dll");
            if (File.Exists(candidate))
            {
                runtime = Assembly.LoadFrom(candidate);
            }
            if (runtime == null)
            {
                throw new InvalidOperationException("Not an IAsyncOperation and System.Runtime.WindowsRuntime is missing");
            }
            Type extensions = runtime.GetType("System.WindowsRuntimeSystemExtensions");
            MethodInfo asTask = null;
            foreach (MethodInfo method in extensions.GetMethods())
            {
                ParameterInfo[] parameters = method.GetParameters();
                if (method.Name == "AsTask" && parameters.Length == 1 &&
                    parameters[0].ParameterType.Name == "IAsyncOperation`1")
                {
                    asTask = method;
                    break;
                }
            }
            if (asTask == null)
            {
                throw new MissingMethodException("AsTask(IAsyncOperation<T>) not found");
            }
            object task = asTask.MakeGenericMethod(resultType).Invoke(null, new object[] { operation });
            MethodInfo wait = task.GetType().GetMethod("Wait", new Type[] { typeof(int) });
            object finished = wait.Invoke(task, new object[] { 10000 });
            if (!(bool)finished)
            {
                throw new TimeoutException("WinRT operation timed out");
            }
            return task.GetType().GetProperty("Result").GetValue(task, null);
        }
        int started = 0;
        int completed = 1;
        int deadline = Environment.TickCount + 10000;
        while ((int)status.GetValue(operation, null) == started)
        {
            if (Environment.TickCount > deadline)
            {
                throw new TimeoutException("WinRT operation timed out");
            }
            Thread.Sleep(15);
        }
        if ((int)status.GetValue(operation, null) != completed)
        {
            throw new InvalidOperationException("WinRT operation failed: " + status.GetValue(operation, null));
        }
        return getResults.Invoke(operation, null);
    }

    private static void OnButtonPressed(object sender, object args)
    {
        try
        {
            object button = Get(args, "Button");
            Emit("button|" + button);
        }
        catch (Exception error)
        {
            Emit("error|button: " + error.Message);
        }
    }

    private static void OnPositionRequested(object sender, object args)
    {
        try
        {
            object requested = Get(args, "RequestedPlaybackPosition");
            Emit("position|" + (long)((TimeSpan)requested).TotalMilliseconds);
        }
        catch (Exception error)
        {
            Emit("error|position: " + error.Message);
        }
    }

    private static void ApplyArtwork(string path)
    {
        if (path == lastArtPath)
        {
            return;
        }
        lastArtPath = path;
        if (string.IsNullOrEmpty(path) || !File.Exists(path))
        {
            Set(displayUpdater, "Thumbnail", null);
            return;
        }

        Type storageFileType = WinRt("Windows.Storage.StorageFile", "Windows.Storage");
        Type referenceType = WinRt("Windows.Storage.Streams.RandomAccessStreamReference", "Windows.Storage.Streams");

        MethodInfo getFile = storageFileType.GetMethod("GetFileFromPathAsync", new Type[] { typeof(string) });
        object file = Await(getFile.Invoke(null, new object[] { path }), storageFileType);

        // CreateFromFile takes the file directly; opening an IRandomAccessStream would need
        // another WinRT projection this helper deliberately avoids.
        MethodInfo createFromFile = null;
        foreach (MethodInfo method in referenceType.GetMethods())
        {
            if (method.Name == "CreateFromFile")
            {
                createFromFile = method;
                break;
            }
        }
        if (createFromFile == null)
        {
            throw new MissingMethodException("RandomAccessStreamReference.CreateFromFile");
        }
        object reference = createFromFile.Invoke(null, new object[] { file });
        Set(displayUpdater, "Thumbnail", reference);
    }

    private static void ApplyMeta(string[] parts)
    {
        string title = parts.Length > 1 ? parts[1] : string.Empty;
        string artist = parts.Length > 2 ? parts[2] : string.Empty;
        string album = parts.Length > 3 ? parts[3] : string.Empty;
        string art = parts.Length > 4 ? parts[4] : string.Empty;

        Set(musicProperties, "Title", title);
        Set(musicProperties, "Artist", artist);
        Set(musicProperties, "AlbumTitle", album);
        try
        {
            ApplyArtwork(art);
        }
        catch (Exception error)
        {
            Emit("error|artwork: " + error.Message);
        }
        displayUpdater.GetType().GetMethod("Update").Invoke(displayUpdater, null);
    }

    private static void ApplyStatus(string value)
    {
        SetEnum(smtc, "PlaybackStatus", playbackStatusType, value);
        // A finished queue stays visible as Stopped with its metadata; only closing the
        // app removes the session from Windows.
        Set(smtc, "IsEnabled", value != "Closed");
    }

    private static void ApplyTimeline(string[] parts)
    {
        long position = 0;
        long duration = 0;
        if (parts.Length > 1)
        {
            long.TryParse(parts[1], NumberStyles.Integer, CultureInfo.InvariantCulture, out position);
        }
        if (parts.Length > 2)
        {
            long.TryParse(parts[2], NumberStyles.Integer, CultureInfo.InvariantCulture, out duration);
        }
        if (duration <= 0)
        {
            return;
        }

        object timeline = Activator.CreateInstance(timelineType);
        Set(timeline, "StartTime", TimeSpan.Zero);
        Set(timeline, "EndTime", TimeSpan.FromMilliseconds(duration));
        Set(timeline, "MinSeekTime", TimeSpan.Zero);
        Set(timeline, "MaxSeekTime", TimeSpan.FromMilliseconds(duration));
        Set(timeline, "Position", TimeSpan.FromMilliseconds(position));
        MethodInfo update = smtc.GetType().GetMethod("UpdateTimelineProperties", new Type[] { timelineType });
        update.Invoke(smtc, new object[] { timeline });
    }

    private static int Run(string[] args)
    {
        bool demo = args.Length > 0 && args[0] == "--demo";

        Type playerType = WinRt("Windows.Media.Playback.MediaPlayer", "Windows.Media.Playback");
        playbackStatusType = WinRt("Windows.Media.MediaPlaybackStatus", "Windows.Media");
        mediaPlaybackType = WinRt("Windows.Media.MediaPlaybackType", "Windows.Media");
        timelineType = WinRt("Windows.Media.SystemMediaTransportControlsTimelineProperties", "Windows.Media");

        object player = Activator.CreateInstance(playerType);
        object commandManager = Get(player, "CommandManager");
        Set(commandManager, "IsEnabled", false);
        smtc = Get(player, "SystemMediaTransportControls");

        Set(smtc, "IsEnabled", true);
        Set(smtc, "IsPlayEnabled", true);
        Set(smtc, "IsPauseEnabled", true);
        Set(smtc, "IsNextEnabled", true);
        Set(smtc, "IsPreviousEnabled", true);
        Set(smtc, "IsStopEnabled", true);

        EventInfo buttonEvent = smtc.GetType().GetEvent("ButtonPressed");
        Delegate handler = Delegate.CreateDelegate(buttonEvent.EventHandlerType, typeof(SmtcHelper).GetMethod("OnButtonPressed", BindingFlags.NonPublic | BindingFlags.Static));
        // WinRT events reject EventInfo.AddEventHandler; the projected add_ method is the supported path.
        MethodInfo addHandler = smtc.GetType().GetMethod("add_ButtonPressed", new Type[] { buttonEvent.EventHandlerType });
        addHandler.Invoke(smtc, new object[] { handler });

        // Windows shows a seekable timeline, so honour drags on it instead of ignoring them.
        EventInfo positionEvent = smtc.GetType().GetEvent("PlaybackPositionChangeRequested");
        Delegate positionHandler = Delegate.CreateDelegate(positionEvent.EventHandlerType, typeof(SmtcHelper).GetMethod("OnPositionRequested", BindingFlags.NonPublic | BindingFlags.Static));
        MethodInfo addPositionHandler = smtc.GetType().GetMethod("add_PlaybackPositionChangeRequested", new Type[] { positionEvent.EventHandlerType });
        addPositionHandler.Invoke(smtc, new object[] { positionHandler });

        displayUpdater = Get(smtc, "DisplayUpdater");
        SetEnum(displayUpdater, "Type", mediaPlaybackType, "Music");
        musicProperties = Get(displayUpdater, "MusicProperties");

        Emit("ready");

        if (demo)
        {
            ApplyMeta(new string[] { "meta", "Probe track one", "Cypher", "Probe album", string.Empty });
            ApplyTimeline(new string[] { "timeline", "42000", "215000" });
            ApplyStatus("Playing");
            Thread.Sleep(45000);
            return 0;
        }

        string line;
        while ((line = Console.In.ReadLine()) != null)
        {
            try
            {
                string[] parts = line.Split('|');
                switch (parts[0])
                {
                    case "meta":
                        ApplyMeta(parts);
                        break;
                    case "status":
                        ApplyStatus(parts[1]);
                        break;
                    case "timeline":
                        ApplyTimeline(parts);
                        break;
                    case "quit":
                        ApplyStatus("Closed");
                        return 0;
                }
            }
            catch (Exception error)
            {
                Emit("error|" + error.GetType().Name + ": " + error.Message);
            }
        }
        return 0;
    }

    public static int Main(string[] args)
    {
        Console.OutputEncoding = Encoding.UTF8;
        try
        {
            return Run(args);
        }
        catch (Exception error)
        {
            Emit("error|fatal: " + error.GetType().Name + ": " + error.Message);
            return 1;
        }
    }
}
