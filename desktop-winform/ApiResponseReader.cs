using System.Net;
using System.Text.Json;

namespace ParkingGateDesktop;

public static class ApiResponseReader
{
    public static async Task<T> ReadAsync<T>(HttpResponseMessage response, JsonSerializerOptions json,
        string serviceName, string action)
    {
        string body = await response.Content.ReadAsStringAsync();
        if (!response.IsSuccessStatusCode)
        {
            string message = BuildHttpError(response.StatusCode, body, action);
            if (TransientServerException.IsTransient(response.StatusCode))
                throw new TransientServerException(response.StatusCode, message);
            throw new InvalidOperationException(message);
        }

        if (string.IsNullOrWhiteSpace(body))
            throw new InvalidOperationException($"Không nhận được dữ liệu khi {action}.\n\nVui lòng thử lại.");

        if (LooksLikeHtml(body))
            throw new InvalidOperationException(
                $"Không thể {action} vì hệ thống trả về dữ liệu không hợp lệ.\n\n" +
                "Vui lòng thử lại. Nếu vẫn lỗi, liên hệ quản trị viên.");

        try
        {
            return JsonSerializer.Deserialize<T>(body, json)
                ?? throw new InvalidOperationException($"Không nhận được dữ liệu khi {action}.\n\nVui lòng thử lại.");
        }
        catch (JsonException)
        {
            throw new InvalidOperationException(
                $"Dữ liệu nhận được không hợp lệ khi {action}.\n\n" +
                "Vui lòng thử lại. Nếu vẫn lỗi, liên hệ quản trị viên.");
        }
    }

    public static async Task EnsureSuccessAsync(HttpResponseMessage response,
        string serviceName, string action)
    {
        if (response.IsSuccessStatusCode) return;
        await ReadAsync<object>(response, new JsonSerializerOptions(), serviceName, action);
    }

    private static bool LooksLikeHtml(string body)
    {
        string value = body.TrimStart();
        return value.StartsWith("<", StringComparison.Ordinal) ||
               value.StartsWith("<!DOCTYPE", StringComparison.OrdinalIgnoreCase);
    }

    private static string BuildHttpError(HttpStatusCode status, string body, string action)
    {
        string detail = Sanitize(ExtractMessage(body));
        return status switch
        {
            HttpStatusCode.BadRequest => detail.Length > 0
                ? detail
                : "Thông tin chưa hợp lệ.\n\nVui lòng kiểm tra lại.",
            HttpStatusCode.Unauthorized => "Phiên đăng nhập đã hết hạn.\n\nVui lòng đăng nhập lại.",
            HttpStatusCode.Forbidden => "Bạn không có quyền thực hiện thao tác này.\n\nVui lòng liên hệ quản trị viên nếu cần.",
            HttpStatusCode.NotFound => "Không tìm thấy dữ liệu cần xử lý.\n\nVui lòng tải lại màn hình và thử lại.",
            HttpStatusCode.Conflict => detail.Length > 0
                ? detail
                : "Dữ liệu đã thay đổi.\n\nVui lòng tải lại và thử lại.",
            HttpStatusCode.UnprocessableEntity => detail.Length > 0
                ? detail
                : "Thông tin hoặc hình ảnh chưa đạt yêu cầu.\n\nVui lòng kiểm tra lại.",
            HttpStatusCode.InternalServerError or HttpStatusCode.BadGateway or HttpStatusCode.GatewayTimeout =>
                "Hệ thống đang gặp sự cố.\n\nVui lòng thử lại sau.",
            HttpStatusCode.ServiceUnavailable => detail.Length > 0
                ? detail
                : "Tính năng nhận dạng tạm thời chưa sẵn sàng.\n\nVui lòng thử lại sau.",
            _ => detail.Length > 0
                ? detail
                : $"Không thể {action}.\n\nVui lòng thử lại."
        };
    }

    private static string Sanitize(string text)
    {
        if (string.IsNullOrWhiteSpace(text)) return "";
        return text
            .Replace("Spring Web", "hệ thống", StringComparison.OrdinalIgnoreCase)
            .Replace("ANPR", "tính năng nhận dạng", StringComparison.OrdinalIgnoreCase)
            .Replace("run-all-laragon.cmd", "ứng dụng", StringComparison.OrdinalIgnoreCase)
            .Replace("run-all.cmd", "ứng dụng", StringComparison.OrdinalIgnoreCase)
            .Trim();
    }

    private static string ExtractMessage(string body)
    {
        if (string.IsNullOrWhiteSpace(body) || LooksLikeHtml(body)) return "";
        try
        {
            using JsonDocument doc = JsonDocument.Parse(body);
            foreach (string key in new[] { "message", "detail", "error" })
                if (doc.RootElement.TryGetProperty(key, out var value)) return value.ToString();
        }
        catch (JsonException) { }
        return body.Length <= 300 ? body : body[..300];
    }
}
