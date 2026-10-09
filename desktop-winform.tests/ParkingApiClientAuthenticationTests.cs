using System.Net;
using System.Text;
using ParkingGateDesktop;
using Microsoft.VisualStudio.TestTools.UnitTesting;

namespace ParkingGateDesktop.Tests;

[TestClass]
public sealed class ParkingApiClientAuthenticationTests
{
    private const string BaseUrl = "http://localhost:8080";

    [TestMethod]
    public async Task SafeReadRefreshesOnceAndRetriesWithNewBearerAtPinnedOrigin()
    {
        var handler = new RecordingHandler((request, index) => index switch
        {
            0 => Json(HttpStatusCode.OK, TokenResponse("access-old", "refresh-old")),
            1 => new HttpResponseMessage(HttpStatusCode.Unauthorized),
            2 => Json(HttpStatusCode.OK, TokenResponse("access-new", "refresh-new")),
            3 => Json(HttpStatusCode.OK, "[]"),
            _ => throw new AssertFailedException("Unexpected HTTP request")
        });
        using var client = new ParkingApiClient(handler) { BaseUrl = BaseUrl };

        await client.LoginAsync("gate", "synthetic test password");
        await client.GetSlotsAsync();

        Assert.HasCount(4, handler.Requests);
        Assert.AreEqual("/api/auth/login", handler.Requests[0].Path);
        Assert.IsNull(handler.Requests[0].Authorization);
        Assert.AreEqual("Bearer access-old", handler.Requests[1].Authorization);
        Assert.AreEqual("/api/auth/refresh", handler.Requests[2].Path);
        Assert.IsNull(handler.Requests[2].Authorization);
        StringAssert.Contains(handler.Requests[2].Body!, "refresh-old");
        Assert.AreEqual("Bearer access-new", handler.Requests[3].Authorization);
        Assert.IsTrue(handler.Requests.All(r => r.Origin == BaseUrl));
    }

    [TestMethod]
    public async Task ConcurrentUnauthorizedReadsShareOneRefreshRotation()
    {
        var handler = new SingleFlightHandler();
        using var client = new ParkingApiClient(handler) { BaseUrl = BaseUrl };
        await client.LoginAsync("gate", "synthetic test password");

        await Task.WhenAll(client.GetSlotsAsync(), client.GetSlotsAsync());

        Assert.AreEqual(1, handler.RefreshCount);
        Assert.AreEqual(4, handler.SlotReadCount);
    }

    [TestMethod]
    public async Task UnauthorizedMutationRefreshesForNextOperationButIsNeverReplayed()
    {
        var handler = new RecordingHandler((request, index) => index switch
        {
            0 => Json(HttpStatusCode.OK, TokenResponse("access-old", "refresh-old")),
            1 => Json(HttpStatusCode.Unauthorized, "{\"error\":\"unauthorized\"}"),
            2 => Json(HttpStatusCode.OK, TokenResponse("access-new", "refresh-new")),
            _ => throw new AssertFailedException("A mutation must not be automatically retried")
        });
        using var client = new ParkingApiClient(handler) { BaseUrl = BaseUrl };
        await client.LoginAsync("gate", "synthetic test password");

        await Assert.ThrowsAsync<DesktopAuthenticationException>(() =>
            client.EntryAsync(new ParkingRequest("59A123456", "", "MOTORBIKE", false, null,
                "evidence-test-id", null)));

        Assert.HasCount(3, handler.Requests);
        Assert.AreEqual("POST", handler.Requests[1].Method);
        Assert.AreEqual("Bearer access-old", handler.Requests[1].Authorization);
        Assert.Contains("\"evidenceId\":\"evidence-test-id\"", handler.Requests[1].Body!);
        Assert.AreEqual("/api/auth/refresh", handler.Requests[2].Path);
    }

    [TestMethod]
    public async Task RefreshTimeoutAfterPossibleRotationClearsAuthenticationWithoutReplayingPendingMutation()
    {
        bool refreshConsumed = false;
        var handler = new RecordingHandler((request, index) => index switch
        {
            0 => Json(HttpStatusCode.OK, TokenResponse("access-old", "refresh-old")),
            1 => new HttpResponseMessage(HttpStatusCode.Unauthorized),
            2 when request.RequestUri!.AbsolutePath == "/api/auth/refresh" => ConsumeThenTimeout(),
            _ => throw new AssertFailedException("An ambiguous refresh must not be retried")
        });
        HttpResponseMessage ConsumeThenTimeout()
        {
            refreshConsumed = true;
            throw new TaskCanceledException("Simulated lost refresh response after server rotation");
        }

        using var client = new ParkingApiClient(handler) { BaseUrl = BaseUrl };
        var pending = new ParkingRequest("97A123456", "", "MOTORBIKE", false, null,
            "evidence-pending-timeout", null);
        await client.LoginAsync("gate", "synthetic test password");

        await Assert.ThrowsAsync<DesktopAuthenticationException>(() => client.EntryAsync(pending));
        Assert.IsFalse(client.IsAuthenticated);
        Assert.IsTrue(refreshConsumed);
        Assert.HasCount(3, handler.Requests);
        Assert.AreEqual(1, handler.Requests.Count(request => request.Path == "/api/parking/entry"));
        Assert.Contains("refresh-old", handler.Requests[2].Body!);
        Assert.AreEqual("evidence-pending-timeout", pending.EvidenceId);

        await Assert.ThrowsAsync<DesktopAuthenticationException>(() => client.EntryAsync(pending));
        Assert.IsFalse(client.IsAuthenticated);
        Assert.HasCount(3, handler.Requests);
    }

    [TestMethod]
    public async Task MalformedRefreshResponseClearsAuthenticationAndDoesNotReplayPendingMutation()
    {
        var handler = new RecordingHandler((_, index) => index switch
        {
            0 => Json(HttpStatusCode.OK, TokenResponse("access-old", "refresh-old")),
            1 => new HttpResponseMessage(HttpStatusCode.Unauthorized),
            2 => Json(HttpStatusCode.OK, "{malformed"),
            _ => throw new AssertFailedException("A malformed refresh must not be retried")
        });
        using var client = new ParkingApiClient(handler) { BaseUrl = BaseUrl };
        var pending = new ParkingRequest("97A123456", "", "MOTORBIKE", false, null,
            "evidence-pending-malformed", null);
        await client.LoginAsync("gate", "synthetic test password");

        await Assert.ThrowsAsync<DesktopAuthenticationException>(() => client.EntryAsync(pending));
        Assert.HasCount(3, handler.Requests);
        Assert.AreEqual(1, handler.Requests.Count(request => request.Path == "/api/parking/entry"));
        Assert.Contains("refresh-old", handler.Requests[2].Body!);
        Assert.AreEqual("evidence-pending-malformed", pending.EvidenceId);

        await Assert.ThrowsAsync<DesktopAuthenticationException>(() => client.EntryAsync(pending));
        Assert.HasCount(3, handler.Requests);
    }

    [TestMethod]
    public async Task ForbiddenIsDistinctAndDoesNotTriggerRefresh()
    {
        var handler = new RecordingHandler((request, index) => index == 0
            ? Json(HttpStatusCode.OK, TokenResponse("access", "refresh"))
            : new HttpResponseMessage(HttpStatusCode.Forbidden));
        using var client = new ParkingApiClient(handler) { BaseUrl = BaseUrl };
        await client.LoginAsync("gate", "synthetic test password");

        await Assert.ThrowsAsync<DesktopAuthorizationException>(() => client.GetSlotsAsync());

        Assert.HasCount(2, handler.Requests);
        Assert.AreEqual("Bearer access", handler.Requests[1].Authorization);
    }

    [TestMethod]
    public async Task RecognitionImageUploadsOnlyToAuthenticatedSpringOrigin()
    {
        const string recognitionResponse = """
            {"plateText":"59A112345","vehicleType":"MOTORBIKE","detectionConfidence":0.9,
             "ocrConfidence":0.8,"vehicleConfidence":0.7,"boundingBox":null,"frameIndex":0,
             "annotatedImageBase64":"","message":"synthetic result","evidenceId":"evidence-test"}
            """;
        var handler = new RecordingHandler((_, index) => index switch
        {
            0 => Json(HttpStatusCode.OK, TokenResponse("access-image", "refresh-image")),
            1 => Json(HttpStatusCode.OK, recognitionResponse),
            _ => throw new AssertFailedException("Unexpected HTTP request")
        });
        using var client = new ParkingApiClient(handler) { BaseUrl = BaseUrl };
        string path = Path.Combine(AppContext.BaseDirectory, $"synthetic-{Guid.NewGuid():N}.jpg");
        await File.WriteAllBytesAsync(path, [0xff, 0xd8, 0xff, 0x01]);

        try
        {
            await client.LoginAsync("gate-user", "synthetic password");
            var result = await client.RecognizeImageAsync(path, "ENTRY");

            Assert.AreEqual("59A112345", result.PlateText);
            Assert.AreEqual("evidence-test", result.EvidenceId);
            Assert.AreEqual("/api/parking/anpr/recognize/image", handler.Requests[1].Path);
            Assert.AreEqual(BaseUrl, handler.Requests[1].Origin);
            Assert.AreEqual("Bearer access-image", handler.Requests[1].Authorization);
            Assert.Contains("name=file; filename=", handler.Requests[1].Body!);
            Assert.Contains("name=operation", handler.Requests[1].Body!);
            Assert.Contains("ENTRY", handler.Requests[1].Body!);
        }
        finally
        {
            File.Delete(path);
        }
    }

    [TestMethod]
    public async Task RecognitionVideoUploadsOnlyToAuthenticatedSpringOrigin()
    {
        const string recognitionResponse = """
            {"plateText":"59A112345","vehicleType":"MOTORBIKE","detectionConfidence":0.9,
             "ocrConfidence":0.8,"vehicleConfidence":0.7,"boundingBox":null,"frameIndex":3,
             "annotatedImageBase64":"","message":"synthetic result","evidenceId":"video-evidence-test"}
            """;
        var handler = new RecordingHandler((_, index) => index switch
        {
            0 => Json(HttpStatusCode.OK, TokenResponse("access-video", "refresh-video")),
            1 => Json(HttpStatusCode.OK, recognitionResponse),
            _ => throw new AssertFailedException("Unexpected HTTP request")
        });
        using var client = new ParkingApiClient(handler) { BaseUrl = BaseUrl };
        string path = Path.Combine(AppContext.BaseDirectory, $"synthetic-{Guid.NewGuid():N}.mp4");
        await File.WriteAllBytesAsync(path, [0, 0, 0, 24, (byte)'f', (byte)'t', (byte)'y', (byte)'p',
            (byte)'i', (byte)'s', (byte)'o', (byte)'m']);

        try
        {
            await client.LoginAsync("gate-user", "synthetic password");
            var result = await client.RecognizeVideoAsync(path, "EXIT");

            Assert.AreEqual("video-evidence-test", result.EvidenceId);
            Assert.AreEqual("/api/parking/anpr/recognize/video", handler.Requests[1].Path);
            Assert.AreEqual(BaseUrl, handler.Requests[1].Origin);
            Assert.AreEqual("Bearer access-video", handler.Requests[1].Authorization);
            Assert.Contains("name=file; filename=", handler.Requests[1].Body!);
            Assert.Contains("name=operation", handler.Requests[1].Body!);
            Assert.Contains("EXIT", handler.Requests[1].Body!);
        }
        finally
        {
            File.Delete(path);
        }
    }

    [TestMethod]
    public async Task GuestFaceCaptureUsesAuthenticatedSpringOrigin()
    {
        const string faceResponse = """
            {"evidenceId":"face-capture-test","decision":"CAPTURED","similarity":0.0,
             "matchThreshold":null,"message":"synthetic capture","realtimeImageBase64":"AQID"}
            """;
        var handler = new RecordingHandler((_, index) => index switch
        {
            0 => Json(HttpStatusCode.OK, TokenResponse("access-face-capture", "refresh-face-capture")),
            1 => Json(HttpStatusCode.OK, faceResponse),
            _ => throw new AssertFailedException("Unexpected HTTP request")
        });
        using var client = new ParkingApiClient(handler) { BaseUrl = BaseUrl };

        await client.LoginAsync("gate-user", "synthetic password");
        var result = await client.CaptureGuestFaceAsync("77Z99123");

        Assert.AreEqual("face-capture-test", result.EvidenceId);
        Assert.AreEqual("/api/parking/anpr/face/capture", handler.Requests[1].Path);
        Assert.AreEqual(BaseUrl, handler.Requests[1].Origin);
        Assert.AreEqual("Bearer access-face-capture", handler.Requests[1].Authorization);
        Assert.Contains("\"operation\":\"ENTRY\"", handler.Requests[1].Body!);
        Assert.Contains("\"plateNumber\":\"77Z99123\"", handler.Requests[1].Body!);
    }

    [TestMethod]
    public async Task FaceVerificationUsesAuthenticatedSpringOriginAndSendsOnlyMemberIdentity()
    {
        const string faceResponse = """
            {"evidenceId":"face-verify-test","decision":"PASS","similarity":0.8,
             "matchThreshold":0.363,"message":"synthetic verification","realtimeImageBase64":"AQID"}
            """;
        var handler = new RecordingHandler((_, index) => index switch
        {
            0 => Json(HttpStatusCode.OK, TokenResponse("access-face-verify", "refresh-face-verify")),
            1 => Json(HttpStatusCode.OK, faceResponse),
            _ => throw new AssertFailedException("Unexpected HTTP request")
        });
        using var client = new ParkingApiClient(handler) { BaseUrl = BaseUrl };

        await client.LoginAsync("gate-user", "synthetic password");
        var result = await client.VerifyFaceAsync("ENTRY", "59A112345", 27, "recognition-evidence-id");

        Assert.AreEqual("face-verify-test", result.EvidenceId);
        Assert.AreEqual("/api/parking/anpr/face/verify", handler.Requests[1].Path);
        Assert.AreEqual(BaseUrl, handler.Requests[1].Origin);
        Assert.AreEqual("Bearer access-face-verify", handler.Requests[1].Authorization);
        Assert.Contains("\"operation\":\"ENTRY\"", handler.Requests[1].Body!);
        Assert.Contains("\"plateNumber\":\"59A112345\"", handler.Requests[1].Body!);
        Assert.Contains("\"familyMemberId\":27", handler.Requests[1].Body!);
        Assert.Contains("\"evidenceId\":\"recognition-evidence-id\"", handler.Requests[1].Body!);
        Assert.IsFalse(handler.Requests[1].Body!.Contains("registrationFaceImagePath", StringComparison.Ordinal));
        Assert.IsFalse(handler.Requests[1].Body!.Contains("registeredImageUrl", StringComparison.Ordinal));
    }

    [TestMethod]
    public async Task ChangingConfiguredOriginNeverSendsTheCachedBearerToken()
    {
        var handler = new RecordingHandler((_, _) => Json(HttpStatusCode.OK, TokenResponse("access", "refresh")));
        using var client = new ParkingApiClient(handler) { BaseUrl = BaseUrl };
        await client.LoginAsync("gate", "synthetic test password");
        client.BaseUrl = "http://localhost:8081";

        await Assert.ThrowsAsync<DesktopAuthenticationException>(() => client.GetSlotsAsync());

        Assert.HasCount(1, handler.Requests);
        Assert.IsNull(handler.Requests[0].Authorization);
    }

    private static string TokenResponse(string accessToken, string refreshToken) => $$"""
        {
          "accessToken": "{{accessToken}}",
          "tokenType": "Bearer",
          "expiresIn": 900,
          "refreshToken": "{{refreshToken}}",
          "sessionId": "session-test",
          "absoluteExpiresAt": "{{DateTimeOffset.UtcNow.AddHours(10):O}}"
        }
        """;

    private static HttpResponseMessage Json(HttpStatusCode status, string body) => new(status)
    {
        Content = new StringContent(body, Encoding.UTF8, "application/json")
    };

    private sealed class RecordingHandler(Func<HttpRequestMessage, int, HttpResponseMessage> responder)
        : HttpMessageHandler
    {
        public List<RecordedRequest> Requests { get; } = [];

        protected override async Task<HttpResponseMessage> SendAsync(HttpRequestMessage request,
                CancellationToken cancellationToken)
        {
            string body = request.Content is null
                ? ""
                : await request.Content.ReadAsStringAsync(cancellationToken);
            Requests.Add(new RecordedRequest(request.Method.Method, request.RequestUri!.AbsolutePath,
                request.RequestUri.GetLeftPart(UriPartial.Authority),
                request.Headers.Authorization?.ToString(), body));
            return responder(request, Requests.Count - 1);
        }
    }

    private sealed class SingleFlightHandler : HttpMessageHandler
    {
        private readonly TaskCompletionSource _initialReadsStarted = new(TaskCreationOptions.RunContinuationsAsynchronously);
        private int _initialReadCount;
        private int _slotReadCount;
        private int _refreshCount;

        public int RefreshCount => Volatile.Read(ref _refreshCount);
        public int SlotReadCount => Volatile.Read(ref _slotReadCount);

        protected override async Task<HttpResponseMessage> SendAsync(HttpRequestMessage request,
                CancellationToken cancellationToken)
        {
            string path = request.RequestUri!.AbsolutePath;
            if (path == "/api/auth/login")
                return Json(HttpStatusCode.OK, TokenResponse("access-old", "refresh-old"));
            if (path == "/api/auth/refresh")
            {
                Interlocked.Increment(ref _refreshCount);
                return Json(HttpStatusCode.OK, TokenResponse("access-new", "refresh-new"));
            }
            if (path != "/api/parking/slots")
                throw new AssertFailedException($"Unexpected request path: {path}");

            Interlocked.Increment(ref _slotReadCount);
            int initialNumber = Interlocked.Increment(ref _initialReadCount);
            if (initialNumber <= 2)
            {
                if (initialNumber == 2) _initialReadsStarted.TrySetResult();
                await _initialReadsStarted.Task.WaitAsync(cancellationToken);
                return new HttpResponseMessage(HttpStatusCode.Unauthorized);
            }
            return Json(HttpStatusCode.OK, "[]");
        }
    }

    private sealed record RecordedRequest(string Method, string Path, string Origin,
        string? Authorization, string? Body);
}
