using System.Net;
using ParkingGateDesktop;
using Xunit;

namespace ParkingGateDesktop.Tests;

public sealed class TransientServerExceptionTest
{
    [Theory]
    [InlineData(HttpStatusCode.InternalServerError)]
    [InlineData(HttpStatusCode.BadGateway)]
    [InlineData(HttpStatusCode.ServiceUnavailable)]
    [InlineData(HttpStatusCode.GatewayTimeout)]
    public void TreatsServerSideOutagesAsTransient(HttpStatusCode statusCode) =>
        Assert.True(TransientServerException.IsTransient(statusCode));

    [Theory]
    [InlineData(HttpStatusCode.BadRequest)]
    [InlineData(HttpStatusCode.Unauthorized)]
    [InlineData(HttpStatusCode.NotFound)]
    [InlineData(HttpStatusCode.Conflict)]
    public void KeepsClientErrorsAsPermanent(HttpStatusCode statusCode) =>
        Assert.False(TransientServerException.IsTransient(statusCode));
}
