using System.Net;

namespace ParkingGateDesktop;

/// <summary>
/// Lỗi phía máy chủ có thể thử lại an toàn. Spring trả 5xx khi mất kết nối database hoặc
/// dịch vụ AI chưa sẵn sàng; request đã có operationId nên gửi lại không tạo bản ghi trùng.
/// </summary>
public sealed class TransientServerException : Exception
{
    public TransientServerException(HttpStatusCode statusCode, string message, Exception? innerException = null)
        : base(message, innerException)
    {
        StatusCode = statusCode;
    }

    public HttpStatusCode StatusCode { get; }

    public static bool IsTransient(HttpStatusCode statusCode) => statusCode is
        HttpStatusCode.InternalServerError or
        HttpStatusCode.BadGateway or
        HttpStatusCode.ServiceUnavailable or
        HttpStatusCode.GatewayTimeout;
}