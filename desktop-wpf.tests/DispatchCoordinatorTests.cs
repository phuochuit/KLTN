using System.Net;
using System.Text;
using LumiPark.Desktop.Services;
using ParkingGateDesktop;
using Xunit;

namespace LumiPark.Desktop.Tests;

public sealed class DispatchCoordinatorTests
{
    [Fact]
    public async Task DispatchSharesGlobalGateWithEntryAndLegacySubmitRejectsDispatchKind()
    {
        string directory = NewDirectory();
        var handler = new DispatchHandler { Delay = true };
        using var client = await Login(handler, directory);
        var legacy = new OfflineOutboxStore(Path.Combine(directory, "legacy.json"));
        await using var journal = await MutationJournal.OpenAsync(Path.Combine(directory, "journal.json"));
        var coordinator = new MutationSubmissionCoordinator(client, journal, legacy);

        var dispatch = coordinator.SubmitDispatchAsync(4, 12);
        await handler.Started.Task.WaitAsync(TimeSpan.FromSeconds(5));
        var entry = await coordinator.SubmitAsync(MutationKind.Entry, Request());
        var legacyDispatch = await coordinator.SubmitAsync(MutationKind.Dispatch, Request());
        Assert.Equal(SingleAttemptOutcome.NotSent, entry.Outcome);
        Assert.Equal(SingleAttemptOutcome.NotSent, legacyDispatch.Outcome);
        Assert.Single(handler.BusinessRequests);
        handler.Release.TrySetResult();

        Assert.Equal(SingleAttemptOutcome.Success, (await dispatch).Outcome);
        Assert.Single(handler.BusinessRequests);
        Assert.False((await coordinator.InspectAsync()).IsHeld);
    }

    [Fact]
    public async Task ConcurrentCleanInspectionsWaitForDispatchInsteadOfCreatingReviewHold()
    {
        string directory = NewDirectory();
        var handler = new DispatchHandler { Delay = true };
        using var client = await Login(handler, directory);
        await using var journal = await MutationJournal.OpenAsync(Path.Combine(directory, "journal.json"));
        var coordinator = new MutationSubmissionCoordinator(client, journal,
            new OfflineOutboxStore(Path.Combine(directory, "legacy.json")));

        var dispatch = coordinator.SubmitDispatchAsync(4, 12);
        await handler.Started.Task.WaitAsync(TimeSpan.FromSeconds(5));
        var firstInspection = coordinator.InspectAsync();
        var secondInspection = coordinator.InspectAsync();
        handler.Release.TrySetResult();

        Assert.Equal(SingleAttemptOutcome.Success, (await dispatch).Outcome);
        Assert.False((await firstInspection).IsHeld);
        Assert.False((await secondInspection).IsHeld);
        Assert.False((await coordinator.InspectAsync()).IsHeld);
    }

    [Fact]
    public async Task UnknownDispatchRemainsHeldAfterRestartWithoutReplay()
    {
        string directory = NewDirectory();
        var handler = new DispatchHandler { FailDispatch = true };
        using var client = await Login(handler, directory);
        var legacy = new OfflineOutboxStore(Path.Combine(directory, "legacy.json"));
        string journalPath = Path.Combine(directory, "journal.json");
        var journal = await MutationJournal.OpenAsync(journalPath);
        var coordinator = new MutationSubmissionCoordinator(client, journal, legacy);

        Assert.Equal(SingleAttemptOutcome.Unknown,
            (await coordinator.SubmitDispatchAsync(4, 12)).Outcome);
        Assert.True((await coordinator.InspectAsync()).IsHeld);
        Assert.Single(handler.BusinessRequests);
        await journal.DisposeAsync();

        await using var restartedJournal = await MutationJournal.OpenAsync(journalPath);
        var restarted = new MutationSubmissionCoordinator(client, restartedJournal, legacy);
        Assert.True((await restarted.InspectAsync()).IsHeld);
        Assert.Equal(SingleAttemptOutcome.NotSent,
            (await restarted.SubmitDispatchAsync(4, 12)).Outcome);
        Assert.Equal(SingleAttemptOutcome.NotSent,
            (await restarted.SubmitAsync(MutationKind.Entry, Request())).Outcome);
        Assert.Equal(SingleAttemptOutcome.NotSent,
            (await restarted.SubmitAsync(MutationKind.ExitConfirm, Request())).Outcome);
        Assert.Single(handler.BusinessRequests);
    }

    [Fact]
    public async Task CleanupFailurePreservesSuccessfulDispatchResultAndHoldsLaterWrites()
    {
        string directory = NewDirectory();
        var handler = new DispatchHandler { Delay = true };
        using var client = await Login(handler, directory);
        await using var journal = await MutationJournal.OpenAsync(Path.Combine(directory, "journal.json"));
        var coordinator = new MutationSubmissionCoordinator(client, journal,
            new OfflineOutboxStore(Path.Combine(directory, "legacy.json")));
        var dispatch = coordinator.SubmitDispatchAsync(4, 12);
        await handler.Started.Task.WaitAsync(TimeSpan.FromSeconds(5));

        using var holdDelete = new FileStream(Path.Combine(directory, "journal.json"), FileMode.Open, FileAccess.Read, FileShare.Read);
        handler.Release.TrySetResult();
        var result = await dispatch;

        Assert.Equal(SingleAttemptOutcome.Success, result.Outcome);
        Assert.Null(result.Response);
        Assert.True((await coordinator.InspectAsync()).IsHeld);
        Assert.Equal(SingleAttemptOutcome.NotSent,
            (await coordinator.SubmitDispatchAsync(4, 12)).Outcome);
        Assert.Single(handler.BusinessRequests);
    }

    [Fact]
    public async Task InvalidDispatchSelectionIsNotSentAndDoesNotCreateJournal()
    {
        string directory = NewDirectory();
        var handler = new DispatchHandler();
        using var client = await Login(handler, directory);
        await using var journal = await MutationJournal.OpenAsync(Path.Combine(directory, "journal.json"));
        var coordinator = new MutationSubmissionCoordinator(client, journal,
            new OfflineOutboxStore(Path.Combine(directory, "legacy.json")));

        Assert.Equal(SingleAttemptOutcome.NotSent, (await coordinator.SubmitDispatchAsync(0, 12)).Outcome);
        Assert.False(File.Exists(Path.Combine(directory, "journal.json")));
        Assert.Empty(handler.BusinessRequests);
    }

    [Fact]
    public async Task DispatchAndInvalidEnumAreRejectedByLegacySubmitWhenCoordinatorIsIdle()
    {
        string directory = NewDirectory();
        var handler = new DispatchHandler();
        using var client = await Login(handler, directory);
        await using var journal = await MutationJournal.OpenAsync(Path.Combine(directory, "journal.json"));
        var coordinator = new MutationSubmissionCoordinator(client, journal,
            new OfflineOutboxStore(Path.Combine(directory, "legacy.json")));

        Assert.Equal(SingleAttemptOutcome.NotSent,
            (await coordinator.SubmitAsync(MutationKind.Dispatch, Request())).Outcome);
        Assert.Equal(SingleAttemptOutcome.NotSent,
            (await coordinator.SubmitAsync((MutationKind)99, Request())).Outcome);
        Assert.Empty(handler.BusinessRequests);
        Assert.False(File.Exists(Path.Combine(directory, "journal.json")));
    }

    private static string NewDirectory()
    {
        string path = Path.GetFullPath(Path.Combine(AppContext.BaseDirectory, "../../../../.cache/test-temp", Guid.NewGuid().ToString("N")));
        Directory.CreateDirectory(path);
        return path;
    }

    private static ParkingRequest Request() => new("ABC123", "", "CAR", false, null, false, null, "", "", "", "", "");

    private static async Task<ParkingApiClient> Login(DispatchHandler handler, string directory)
    {
        var client = new ParkingApiClient(handler,
            new OfflineOutboxStore(Path.Combine(directory, "legacy.json")), new GateStationSettings())
        { BaseUrl = "https://example.invalid" };
        await client.LoginAsync("operator", "password");
        return client;
    }

    private sealed class DispatchHandler : HttpMessageHandler
    {
        public bool Delay { get; init; }
        public bool FailDispatch { get; init; }
        public TaskCompletionSource Started { get; } = new(TaskCreationOptions.RunContinuationsAsynchronously);
        public TaskCompletionSource Release { get; } = new(TaskCreationOptions.RunContinuationsAsynchronously);
        public List<string> BusinessRequests { get; } = [];

        protected override async Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken token)
        {
            string path = request.RequestUri!.AbsolutePath;
            if (path == "/api/auth/login")
                return Json(HttpStatusCode.OK, "{\"accessToken\":\"token\",\"tokenType\":\"Bearer\",\"expiresIn\":900,\"refreshToken\":\"refresh\",\"sessionId\":\"test\",\"absoluteExpiresAt\":\"2099-01-01T00:00:00Z\",\"username\":\"operator\",\"fullName\":\"Operator\"}");
            BusinessRequests.Add(path);
            if (path.EndsWith("/dispatch-session", StringComparison.Ordinal))
            {
                if (Delay) { Started.TrySetResult(); await Release.Task.WaitAsync(token); }
                return FailDispatch ? Json(HttpStatusCode.InternalServerError, "{}"): new(HttpStatusCode.NoContent);
            }
            return Json(HttpStatusCode.OK, "{}");
        }

        private static HttpResponseMessage Json(HttpStatusCode status, string body) =>
            new(status) { Content = new StringContent(body, Encoding.UTF8, "application/json") };
    }
}
