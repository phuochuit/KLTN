using System.Net;
using System.Net.Http.Json;
using System.Text;
using System.Text.Json;
using LumiPark.Desktop.Services;
using ParkingGateDesktop;
using Xunit;

namespace LumiPark.Desktop.Tests;

public sealed class GateWorkspaceTests
{
    [Fact]
    public async Task DelayedLookupDoesNotReplaceNewPlateContext()
    {
        var handler = new GateHandler { DelayLookup = true };
        await using var fixture = await Fixture.Create(handler);
        var workspace = fixture.Workspace;
        workspace.SetPlate("11A-111.11");
        var lookup = workspace.LookupAsync();
        await handler.LookupStarted.Task.WaitAsync(TimeSpan.FromSeconds(5));
        workspace.SetPlate("22B-222.22");
        handler.ReleaseLookup.TrySetResult();
        Assert.Null(await lookup);
        Assert.Null(workspace.Lookup);
        Assert.Equal("22B-222.22", workspace.Plate);
    }

    [Fact]
    public async Task PreviewTicketIsInvalidatedWhenPlateChanges()
    {
        await using var fixture = await Fixture.Create(new GateHandler());
        var workspace = fixture.Workspace;
        workspace.SetDirection(GateDirection.Exit);
        workspace.SetPlate("11A-111.11");
        Assert.NotNull(await workspace.PreviewExitAsync());
        workspace.SetPlate("22B-222.22");
        Assert.Throws<InvalidOperationException>(() => workspace.CreateConfirmationTicket("CASH"));
    }

    [Fact]
    public async Task DoubleSubmitDispatchesOnlyOneMutation()
    {
        var handler = new GateHandler { DelayMutation = true };
        await using var fixture = await Fixture.Create(handler);
        fixture.Workspace.SetPlate("11A-111.11");
        var first = fixture.Workspace.SubmitEntryAsync();
        await handler.MutationStarted.Task.WaitAsync(TimeSpan.FromSeconds(5));
        var second = await fixture.Workspace.SubmitEntryAsync();
        Assert.Equal(SingleAttemptOutcome.NotSent, second.Outcome);
        handler.ReleaseMutation.TrySetResult();
        Assert.Equal(SingleAttemptOutcome.Success, (await first).Outcome);
        Assert.Equal(1, handler.MutationCount);
        Assert.True(Guid.TryParse(handler.OperationId, out _));
        using var payload = JsonDocument.Parse(handler.MutationBody!);
        Assert.Equal("11A-111.11", payload.RootElement.GetProperty("plateNumber").GetString());
        Assert.Equal("11A11111", payload.RootElement.GetProperty("cardCode").GetString());
        Assert.Equal("/api/parking/entry", handler.MutationPath);
        Assert.True(fixture.Workspace.CanNextContext);
        Assert.Equal(SingleAttemptOutcome.NotSent, (await fixture.Workspace.SubmitEntryAsync()).Outcome);
        Assert.Equal(1, handler.MutationCount);
        Assert.True(fixture.Workspace.NextContext());
        Assert.Equal("", fixture.Workspace.Plate);
        Assert.Equal("Sẵn sàng", fixture.Workspace.Status);
    }

    [Fact]
    public async Task UnknownMutationHoldsContextWithoutRetryOrNext()
    {
        var handler = new GateHandler { UnknownMutation = true };
        await using var fixture = await Fixture.Create(handler);
        var confirmed = 0; fixture.Workspace.TransactionConfirmed += (_, _) => confirmed++;
        fixture.Workspace.SetPlate("11A-111.11");
        Assert.Equal(SingleAttemptOutcome.Unknown, (await fixture.Workspace.SubmitEntryAsync()).Outcome);
        Assert.Equal(0, confirmed);
        Assert.True(fixture.Workspace.IsHeld);
        Assert.False(fixture.Workspace.NextContext());
        Assert.Equal(SingleAttemptOutcome.NotSent, (await fixture.Workspace.SubmitEntryAsync()).Outcome);
        Assert.Equal(1, handler.MutationCount);
    }

    [Fact]
    public async Task SwitchingDirectionDoesNotCarryPlateOrEvidenceAcrossContexts()
    {
        await using var fixture = await Fixture.Create(new GateHandler());
        fixture.Workspace.SetPlate("11A-111.11");
        Assert.False(fixture.Workspace.CanNextContext);
        fixture.Workspace.SetDirection(GateDirection.Exit);
        Assert.Equal("", fixture.Workspace.Plate);
        Assert.Equal("", fixture.Workspace.VehicleEvidenceId);
        Assert.Null(fixture.Workspace.Lookup);
    }

    [Fact]
    public async Task FeePreviewRequiresMethodAndRequiredReferenceAndNeverConfirmsByItself()
    {
        var handler = new GateHandler { Fee = 1250m };
        await using var fixture = await Fixture.Create(handler);
        fixture.Workspace.SetDirection(GateDirection.Exit);
        fixture.Workspace.SetPlate("11A-111.11");
        await fixture.Workspace.PreviewExitAsync();
        Assert.Throws<InvalidOperationException>(() => fixture.Workspace.CreateConfirmationTicket());
        Assert.Throws<InvalidOperationException>(() => fixture.Workspace.CreateConfirmationTicket("BANK_TRANSFER"));
        var ticket = fixture.Workspace.CreateConfirmationTicket("BANK_TRANSFER", "BANK-REF-1");
        Assert.Equal("BANK_TRANSFER", ticket.Request.PaymentMethod);
        Assert.Equal("BANK-REF-1", ticket.Request.PaymentReference);
        Assert.Equal(0, handler.MutationCount);
    }

    [Fact]
    public async Task ZeroFeePreviewCreatesNoPaymentAndDoesNotMutateUntilExplicitConfirmation()
    {
        var handler = new GateHandler { Fee = 0m };
        await using var fixture = await Fixture.Create(handler);
        fixture.Workspace.SetDirection(GateDirection.Exit);
        fixture.Workspace.SetPlate("11A11111");
        var preview = await fixture.Workspace.PreviewExitAsync();
        Assert.Equal(0m, preview!.Fee);
        var ticket = fixture.Workspace.CreateConfirmationTicket();
        Assert.Null(ticket.Request.PaymentMethod);
        Assert.Equal(0, handler.MutationCount);
        Assert.Equal(SingleAttemptOutcome.Success, (await fixture.Workspace.ConfirmExitAsync(ticket)).Outcome);
        Assert.Equal(1, handler.MutationCount);
    }

    [Fact]
    public async Task CombinedEntryKeepsCapturedImageAndUploadsRegisteredMemberVerification()
    {
        var handler = new GateHandler { RegisteredLookup = true };
        await using var fixture = await Fixture.Create(handler);
        var result = await fixture.Workspace.RecognizeAndCaptureAsync(() => handler.VehicleResult());
        Assert.NotNull(result);
        Assert.True(fixture.Workspace.CanVerifyFaceFile);
        Assert.Contains(handler.RequestPaths, p => p == "/api/ai/face/verify-image");
        Assert.Contains("familyMemberId", handler.FaceUploadBody!);
        Assert.Contains("captured-face-bytes", handler.FaceUploadBody!);
        var entry = await fixture.Workspace.SubmitEntryAsync();
        using var body = JsonDocument.Parse(handler.MutationBody!);
        Assert.Equal(42, body.RootElement.GetProperty("familyMemberId").GetInt64());
        Assert.True(body.RootElement.GetProperty("faceVerified").GetBoolean());
        Assert.Equal("vehicle-evidence", body.RootElement.GetProperty("vehicleEvidenceId").GetString());
        Assert.Equal(SingleAttemptOutcome.Success, entry.Outcome);
    }

    [Fact]
    public async Task GuestEntryCaptureSurvivesLookupAndGuestExitUsesEntryEvidence()
    {
        var handler = new GateHandler();
        await using var fixture = await Fixture.Create(handler);
        await fixture.Workspace.RecognizeAndCaptureAsync(() => handler.VehicleResult());
        Assert.False(fixture.Workspace.CanVerifyFaceFile);
        await fixture.Workspace.SubmitEntryAsync();
        using (var entry = JsonDocument.Parse(handler.MutationBody!))
            Assert.Contains("captured-face-bytes", Encoding.UTF8.GetString(Convert.FromBase64String(entry.RootElement.GetProperty("realtimeFaceImageBase64").GetString()!)));
        Assert.True(fixture.Workspace.NextContext());
        fixture.Workspace.SetDirection(GateDirection.Exit);
        fixture.Workspace.SetPlate("11A11111");
        await fixture.Workspace.RecognizeAndCaptureAsync(() => handler.VehicleResult());
        Assert.True(fixture.Workspace.CanVerifyFaceFile);
        await fixture.Workspace.LookupAsync();
        Assert.True(fixture.Workspace.CanVerifyFaceFile);
        await fixture.Workspace.VerifyFaceCameraAsync();
        Assert.Contains("/api/ai/face/guest/verify-camera", handler.RequestPaths);
        await fixture.Workspace.PreviewExitAsync();
        await fixture.Workspace.ConfirmExitAsync(fixture.Workspace.CreateConfirmationTicket());
        using var exit = JsonDocument.Parse(handler.MutationBody!);
        Assert.Equal("vehicle-evidence", exit.RootElement.GetProperty("vehicleEvidenceId").GetString());
        Assert.Equal("", exit.RootElement.GetProperty("realtimeFaceImageBase64").GetString());
    }

    [Fact]
    public async Task CombinedCaptureFollowupIsInvalidatedByContextChangeWhileLookupWaits()
    {
        var handler = new GateHandler { DelayLookup = true, RegisteredLookup = true };
        await using var fixture = await Fixture.Create(handler);
        var pending = fixture.Workspace.RecognizeAndCaptureAsync(() => handler.VehicleResult());
        await handler.LookupStarted.Task.WaitAsync(TimeSpan.FromSeconds(5));
        fixture.Workspace.SetMember(99);
        handler.ReleaseLookup.TrySetResult();
        await pending;
        Assert.DoesNotContain("/api/ai/face/verify-image", handler.RequestPaths);
    }

    [Fact]
    public async Task CombinedCaptureCannotApplyAfterMutationStarts()
    {
        var handler = new GateHandler { DelayMutation = true };
        await using var fixture = await Fixture.Create(handler);
        var capture = fixture.Workspace.RecognizeAndCaptureAsync(() => handler.VehicleResult());
        var mutation = fixture.Workspace.SubmitEntryAsync();
        await handler.MutationStarted.Task.WaitAsync(TimeSpan.FromSeconds(5));
        var evidence = fixture.Workspace.VehicleEvidenceId;
        handler.ReleaseMutation.TrySetResult();
        await mutation; await capture;
        Assert.Equal(evidence, fixture.Workspace.VehicleEvidenceId);
    }

    [Fact]
    public async Task FaceAndPreviewTicketsRejectLateOrMalformedResults()
    {
        var handler = new GateHandler { DelayFace = true, MissingPreviewSession = true };
        await using var fixture = await Fixture.Create(handler);
        fixture.Workspace.SetDirection(GateDirection.Exit);
        await fixture.Workspace.RecognizeAndCaptureAsync(() => handler.VehicleResult());
        Assert.True(fixture.Workspace.CanVerifyFaceFile);
        await fixture.Workspace.LookupAsync();
        var face = fixture.Workspace.VerifyFaceCameraAsync();
        await handler.FaceStarted.Task.WaitAsync(TimeSpan.FromSeconds(5));
        fixture.Workspace.SetPlate("22B22222");
        handler.ReleaseFace.TrySetResult(); Assert.Null(await face);
        Assert.Equal("", fixture.Workspace.FaceEvidenceId);
        Assert.Null(await fixture.Workspace.PreviewExitAsync());
    }

    [Fact]
    public async Task UnknownJournalAndSuccessfulContextHoldRequestsUntilNext()
    {
        var handler = new GateHandler { UnknownMutation = true };
        await using var fixture = await Fixture.Create(handler);
        await fixture.Workspace.SubmitEntryAsync();
        var reads = handler.RequestPaths.Count;
        Assert.Null(await fixture.Workspace.LookupAsync());
        fixture.Workspace.SetPlate("22B22222");
        Assert.Equal(reads, handler.RequestPaths.Count);
        Assert.False(fixture.Workspace.NextContext());
    }

    [Fact]
    public async Task CorrectionRequiresEvidenceAndPreservesItWhilePlateSetterDiscardsIt()
    {
        var handler = new GateHandler { RegisteredLookup = true };
        await using var fixture = await Fixture.Create(handler);
        await fixture.Workspace.RecognizeAndCaptureAsync(() => handler.VehicleResult());
        Assert.True(fixture.Workspace.FaceVerified);
        Assert.Equal(42, fixture.Workspace.MemberId);
        Assert.Throws<ArgumentException>(() => fixture.Workspace.ApplyCorrection("22B22222", "REASON", "Invalid reason code example", "CAR"));
        fixture.Workspace.ApplyCorrection("22B-222.22", "PLATE_CORRECTION", "Corrected plate from camera evidence", "CAR");
        Assert.Equal("vehicle-evidence", fixture.Workspace.VehicleEvidenceId);
        Assert.Equal("22B-222.22", fixture.Workspace.Plate);
        Assert.Null(fixture.Workspace.Lookup);
        Assert.False(fixture.Workspace.FaceVerified);
        Assert.Null(fixture.Workspace.MemberId);
        fixture.Workspace.SetPlate("33C-333.33");
        Assert.Equal("", fixture.Workspace.VehicleEvidenceId);
        Assert.Equal("", fixture.Workspace.FaceBase64);
    }

    [Fact]
    public async Task CorrectionRejectsVehicleTypesOutsideTheCanonicalChoices()
    {
        await using var fixture = await Fixture.Create(new GateHandler());
        await fixture.Workspace.RecognizeAndCaptureAsync(() => Task.FromResult(new AnprResponse("11A11111", "CAR", .9, .9, .9, null, 0, "", "OK", EvidenceId: "vehicle-evidence")));
        Assert.Throws<ArgumentException>(() => fixture.Workspace.ApplyCorrection("11A11111", "FACE_REVIEW", "Reason text is long enough", "BICYCLE"));
        Assert.Equal("CAR", fixture.Workspace.VehicleType);
        Assert.Equal("vehicle-evidence", fixture.Workspace.VehicleEvidenceId);
    }

    [Fact]
    public async Task ContextNotificationExposesAlreadyInvalidatedPreview()
    {
        await using var fixture = await Fixture.Create(new GateHandler());
        var workspace = fixture.Workspace;
        workspace.SetDirection(GateDirection.Exit);
        workspace.SetPlate("11A11111");
        Assert.NotNull(await workspace.PreviewExitAsync());
        ParkingResponse? observed = workspace.Preview;
        workspace.PropertyChanged += (_, _) => observed = workspace.Preview;
        workspace.SetPlate("22B22222");
        Assert.Null(observed);
    }

    [Theory]
    [InlineData(true, true)]
    [InlineData(false, false)]
    public async Task LookupRequiresSafetyInitialization(bool initialize, bool allowed)
    {
        var handler = new GateHandler();
        await using var fixture = await Fixture.Create(handler, initialize);
        fixture.Workspace.SetPlate("11A11111");
        await fixture.Workspace.LookupAsync();
        Assert.Equal(allowed, handler.RequestPaths.Contains("/api/parking/lookup"));
    }

    [Fact]
    public async Task InvalidatedWorkspaceRejectsNewRequestsAfterSessionEnds()
    {
        var handler = new GateHandler();
        await using var fixture = await Fixture.Create(handler);
        fixture.Workspace.Invalidate();
        fixture.Workspace.SetPlate("11A11111");
        Assert.Null(await fixture.Workspace.LookupAsync());
        Assert.DoesNotContain("/api/parking/lookup", handler.RequestPaths);
    }

    private sealed class Fixture : IAsyncDisposable
    {
        private readonly string folder;
        public ParkingApiClient Client { get; }
        public MutationJournal Journal { get; }
        public GateWorkspace Workspace { get; }
        private Fixture(string folder, ParkingApiClient client, MutationJournal journal, GateWorkspace workspace) { this.folder = folder; Client = client; Journal = journal; Workspace = workspace; }
        public static async Task<Fixture> Create(GateHandler handler, bool initialize = true)
        {
            string folder = Path.Combine(Path.GetTempPath(), "gate-workspace-tests", Guid.NewGuid().ToString("N"));
            Directory.CreateDirectory(folder);
            var client = new ParkingApiClient(handler, new OfflineOutboxStore(Path.Combine(folder, "legacy.json")), new GateStationSettings()) { BaseUrl = "http://localhost" };
            await client.LoginAsync("gate", "pass");
        var journal = await MutationJournal.OpenAsync(Path.Combine(folder, "mutation.json"));
        var mutations = new MutationSubmissionCoordinator(client, journal, new OfflineOutboxStore(Path.Combine(folder, "legacy.json")));
            var workspace = new GateWorkspace(client, mutations);
            if (initialize) await workspace.InitializeAsync();
            return new Fixture(folder, client, journal, workspace);
        }
        public async ValueTask DisposeAsync() { Client.Dispose(); await Journal.DisposeAsync(); if (Directory.Exists(folder)) Directory.Delete(folder, true); }
    }

    private sealed class GateHandler : HttpMessageHandler
    {
        public bool DelayLookup { get; init; }
        public bool DelayMutation { get; init; }
        public bool UnknownMutation { get; init; }
        public bool RegisteredLookup { get; init; }
        public bool DelayFace { get; init; }
        public bool MissingPreviewSession { get; init; }
        public decimal Fee { get; init; }
        public int MutationCount { get; private set; }
        public List<string> RequestPaths { get; } = [];
        public string? FaceUploadBody { get; private set; }
        public string? OperationId { get; private set; }
        public string? MutationBody { get; private set; }
        public string? MutationPath { get; private set; }
        public TaskCompletionSource LookupStarted { get; } = new(TaskCreationOptions.RunContinuationsAsynchronously);
        public TaskCompletionSource ReleaseLookup { get; } = new(TaskCreationOptions.RunContinuationsAsynchronously);
        public TaskCompletionSource MutationStarted { get; } = new(TaskCreationOptions.RunContinuationsAsynchronously);
        public TaskCompletionSource ReleaseMutation { get; } = new(TaskCreationOptions.RunContinuationsAsynchronously);
        public TaskCompletionSource FaceStarted { get; } = new(TaskCreationOptions.RunContinuationsAsynchronously);
        public TaskCompletionSource ReleaseFace { get; } = new(TaskCreationOptions.RunContinuationsAsynchronously);
        public Task<AnprResponse> VehicleResult() => Task.FromResult(new AnprResponse("11A11111", "CAR", .9, .9, .9, null, 0, "", "OK", EvidenceId: "vehicle-evidence"));
        protected override async Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
        {
            string path = request.RequestUri!.AbsolutePath;
            RequestPaths.Add(path);
            if (path == "/api/auth/login") return Json(new { accessToken = "access", tokenType = "Bearer", expiresIn = 300, refreshToken = "refresh", sessionId = "sid", absoluteExpiresAt = DateTimeOffset.UtcNow.AddHours(1), username = "gate", fullName = "Gate", activeShift = (object?)null });
            if (path == "/api/parking/lookup")
            {
                if (DelayLookup) { LookupStarted.TrySetResult(); await ReleaseLookup.Task.WaitAsync(TimeSpan.FromSeconds(5), cancellationToken); }
                return Json(new { registered = RegisteredLookup, cardMatched = true, plateNumber = "11A11111", ownerName = "Khách", ownerPhone = "", apartmentNumber = "", vehicleType = "CAR", cardCode = "11A11111", passType = "", validUntil = (DateTime?)null, packageValid = false, message = "Khách", authorizedMembers = RegisteredLookup ? new[] { new { id = 42, fullName = "Resident", relationship = "Owner", faceImageAvailable = true } } : Array.Empty<object>(), guestEntryFaceAvailable = !RegisteredLookup });
            }
            if (path == "/api/ai/recognize/camera") return Json(await VehicleResult());
            if (path == "/api/ai/face/capture-camera") return Json(new { decision = "NOT_EVALUATED", similarity = 0, matchThreshold = .8, message = "capture", realtimeImageBase64 = Convert.ToBase64String(System.Text.Encoding.UTF8.GetBytes("captured-face-bytes")), evidenceId = "face-evidence" });
            if (path.Contains("/api/ai/face/") && path.EndsWith("verify-camera"))
            {
                if (DelayFace) { FaceStarted.TrySetResult(); await ReleaseFace.Task.WaitAsync(TimeSpan.FromSeconds(5), cancellationToken); }
                return Json(new { decision = "PASS", similarity = .95, matchThreshold = .8, message = "pass", realtimeImageBase64 = "camera-face", evidenceId = "face-evidence" });
            }
            if (path.EndsWith("verify-image")) { FaceUploadBody = await request.Content!.ReadAsStringAsync(cancellationToken); return Json(new { decision = "PASS", similarity = .95, matchThreshold = .8, message = "pass", realtimeImageBase64 = "uploaded-face", evidenceId = "face-evidence" }); }
            if (path == "/api/parking/exit-preview") return Json(new { sessionId = MissingPreviewSession ? 0 : 7, plateNumber = "11A11111", ownerName = "", vehicleType = "CAR", cardCode = "", status = "PREVIEW", entryTime = MissingPreviewSession ? default(DateTime) : DateTime.UtcNow.AddHours(-1), exitTime = DateTime.UtcNow, fee = Fee, message = "Preview", warning = false });
            if (path == "/api/parking/entry" || path == "/api/parking/exit-confirm")
            {
                MutationCount++;
                using var body = await JsonDocument.ParseAsync(await request.Content!.ReadAsStreamAsync(cancellationToken), cancellationToken: cancellationToken);
                OperationId = body.RootElement.GetProperty("operationId").GetString();
                MutationBody = body.RootElement.GetRawText(); MutationPath = path;
                MutationStarted.TrySetResult();
                if (DelayMutation) await ReleaseMutation.Task.WaitAsync(TimeSpan.FromSeconds(5), cancellationToken);
                if (UnknownMutation) return new HttpResponseMessage(HttpStatusCode.ServiceUnavailable);
                bool exit = path.EndsWith("exit-confirm", StringComparison.Ordinal);
                var now = DateTime.UtcNow;
                return Json(new { sessionId = 7, plateNumber = body.RootElement.GetProperty("plateNumber").GetString(), ownerName = "", vehicleType = "CAR", cardCode = "", status = exit ? "COMPLETED" : "OPEN", entryTime = now.AddHours(-1), exitTime = exit ? now : (DateTime?)null, fee = 0m, message = "OK", warning = false });
            }
            return new HttpResponseMessage(HttpStatusCode.NotFound);
        }
        private static HttpResponseMessage Json<T>(T body) => new(HttpStatusCode.OK) { Content = JsonContent.Create(body) };
    }
}
