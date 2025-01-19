using System.Timers;

namespace LucidDreamProvider;

public static class VibrationService
{
    private static System.Timers.Timer? _timer;
    private static long[] _pattern = Array.Empty<long>();
    private static long _interval;

    public static void Start(long interval, long[] pattern)
    {
        _pattern = pattern;
        _interval = interval;

        _timer?.Stop();
        _timer = new System.Timers.Timer(interval);
        _timer.Elapsed += OnTimerElapsed;
        _timer.Start();
    }

    public static void Stop()
    {
        _timer?.Stop();
        _timer?.Dispose();
        _timer = null;
    }

    private static async void OnTimerElapsed(object? sender, ElapsedEventArgs e)
    {
        if (_pattern.Length == 0) return;

        // Execute the entire vibration pattern
        foreach (var duration in _pattern)
        {
            Vibration.Default.Vibrate(duration);
            await Task.Delay((int)duration);
        }
    }
}
