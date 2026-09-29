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
            throw new InvalidOperationException(BuildHttpError(response.StatusCode, body, serviceName, action));

        if (string.IsNullOrWhiteSpace(body))
            throw new InvalidOperationException($"{serviceName} không trả về dữ liệu khi {action}.\n\nCách xử lý: đóng và chạy lại dịch vụ, sau đó bấm Kiểm tra.");

        if (LooksLikeHtml(body))
            throw new InvalidOperationException(
                $"{serviceName} trả về một trang web thay vì dữ liệu API khi {action}.\n\n" +
                "Nguyên nhân thường gặp:\n" +
                "• Đang chạy phiên bản dịch vụ cũ.\n" +
                "• Nhập sai địa chỉ API.\n" +
                "• Yêu cầu bị chuyển tới trang đăng nhập hoặc trang báo lỗi.\n\n" +
                "Cách xử lý: đóng các cửa sổ Parking Web/ANPR cũ, chạy lại run-all-laragon.cmd rồi bấm Kiểm tra.");

        try
        {
            return JsonSerializer.Deserialize<T>(body, json)
                ?? throw new InvalidOperationException($"{serviceName} trả về dữ liệu rỗng khi {action}.");
        }
        catch (JsonException)
        {
            throw new InvalidOperationException(
                $"Dữ liệu trả về từ {serviceName} không đúng định dạng khi {action}.\n\n" +
                "Cách xử lý: đóng dịch vụ cũ, chạy lại toàn bộ hệ thống và thử lại. Nếu lỗi tiếp diễn, báo quản trị viên kiểm tra log máy chủ.");
        }
    }

    private static bool LooksLikeHtml(string body)
    {
        string value = body.TrimStart();
        return value.StartsWith("<", StringComparison.Ordinal) ||
               value.StartsWith("<!DOCTYPE", StringComparison.OrdinalIgnoreCase);
    }

    private static string BuildHttpError(HttpStatusCode status, string body, string service, string action)
    {
        string detail = ExtractMessage(body);
        string reason = status switch
        {
            HttpStatusCode.BadRequest => detail.Length > 0 ? detail : "Thông tin gửi lên chưa hợp lệ.",
            HttpStatusCode.Unauthorized or HttpStatusCode.Forbidden => "Không có quyền truy cập API hoặc phiên đăng nhập đã hết hạn.",
            HttpStatusCode.NotFound => "Không tìm thấy chức năng API. Có thể đang chạy phiên bản chương trình cũ.",
            HttpStatusCode.UnprocessableEntity => detail.Length > 0 ? detail : "Ảnh hoặc dữ liệu không đạt yêu cầu xử lý.",
            HttpStatusCode.InternalServerError => "Máy chủ gặp lỗi khi xử lý dữ liệu.",
            HttpStatusCode.ServiceUnavailable => detail.Length > 0 ? detail : "Mô hình AI hoặc dịch vụ chưa sẵn sàng.",
            _ => detail.Length > 0 ? detail : $"Máy chủ trả về mã lỗi {(int)status}."
        };
        return $"Không thể {action}.\n\nDịch vụ: {service}\nNguyên nhân: {reason}\n\nCách xử lý: kiểm tra dịch vụ bằng nút Kiểm tra; nếu vẫn lỗi, đóng các cửa sổ dịch vụ cũ và chạy lại run-all-laragon.cmd.";
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
