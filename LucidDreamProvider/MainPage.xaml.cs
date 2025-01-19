namespace LucidDreamProvider;

public partial class MainPage : ContentPage
{
    public MainPage()
    {
        InitializeComponent();
    }

    private async void OnStartVibration(object sender, EventArgs e)
    {
        // Validate interval input
        if (!int.TryParse(IntervalInput.Text, out var intervalMinutes) || intervalMinutes <= 0)
        {
            await DisplayAlert("Error", "Invalid interval value!", "OK");
            return;
        }

        // Convert minutes to milliseconds
        var intervalMilliseconds = intervalMinutes * 60 * 1000;

        // Get selected pattern
        var selectedPattern = PatternPicker.SelectedItem?.ToString();
        if (string.IsNullOrEmpty(selectedPattern))
        {
            await DisplayAlert("Error", "Please select a pattern!", "OK");
            return;
        }

        // Parse pattern based on selected option
        long[] pattern = selectedPattern switch
        {
            "Short-Long-Short" => new long[] { 100, 500, 100 },
            "Heartbeat" => new long[] { 200, 200, 600, 200 },
            "Triple Pulse" => new long[] { 300, 300, 300 },
            "Steady" => new long[] { 500, 500, 500, 500 },
            _ => Array.Empty<long>()
        };

        if (pattern.Sum() >= intervalMilliseconds)
        {
            await DisplayAlert(
                "Error",
                $"Interval must be longer than the total pattern duration ({pattern.Sum()} ms).",
                "OK"
            );
            return;
        }

        VibrationService.Start(intervalMilliseconds, pattern);
    }

    private void OnStopVibration(object sender, EventArgs e)
    {
        VibrationService.Stop();
    }
}
