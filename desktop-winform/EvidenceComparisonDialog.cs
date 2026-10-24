using System.Drawing;

namespace ParkingGateDesktop;

public sealed class EvidenceComparisonDialog : Form
{
    public bool OverrideApplied { get; private set; }
    public string OverrideReasonCode { get; private set; } = "";
    public string OverrideReasonText { get; private set; } = "";
    public string CorrectedPlate { get; private set; } = "";
    public string CorrectedVehicleType { get; private set; } = "UNKNOWN";

    public EvidenceComparisonDialog(
        EvidenceComparisonResponse data,
        bool hasActiveShift,
        string currentPlate,
        string currentVehicleType)
    {
        Text = $"Đối chiếu ảnh Vào / Ra — Lượt #{data.SessionId} ({data.EntryPlate})";
        StartPosition = FormStartPosition.CenterParent;
        MinimumSize = new Size(1020, 720);
        Size = new Size(1120, 780);
        Font = new Font("Segoe UI", 9.5f);
        BackColor = Color.White;

        var root = new TableLayoutPanel
        {
            Dock = DockStyle.Fill,
            RowCount = 4,
            ColumnCount = 1,
            Padding = new Padding(14)
        };
        root.RowStyles.Add(new RowStyle(SizeType.Absolute, 76));
        root.RowStyles.Add(new RowStyle(SizeType.Absolute, 48));
        root.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
        root.RowStyles.Add(new RowStyle(SizeType.Absolute, 52));

        // 1. Header Banner
        var banner = new Panel
        {
            Dock = DockStyle.Fill,
            Padding = new Padding(12, 8, 12, 8),
            BackColor = data.MatchStatus switch
            {
                "MATCH" => Color.FromArgb(234, 245, 239),
                "NO_MATCH" => Color.FromArgb(253, 237, 237),
                _ => Color.FromArgb(255, 247, 230)
            },
            BorderStyle = BorderStyle.FixedSingle
        };
        Color statusColor = data.MatchStatus switch
        {
            "MATCH" => Color.SeaGreen,
            "NO_MATCH" => Color.Firebrick,
            _ => Color.DarkGoldenrod
        };
        string statusTitle = data.MatchStatus switch
        {
            "MATCH" => "KHỚP DỮ LIỆU VÀO / RA (MATCH)",
            "NO_MATCH" => "KHÔNG KHỚP — CẢNH BÁO AN NINH (NO_MATCH)",
            _ => "CẦN NHÂN VIÊN KIỂM TRA LẠI (REVIEW)"
        };
        var titleLbl = new Label
        {
            Text = $"{statusTitle}  |  Lượt #{data.SessionId} — Biển số lượt vào: {data.EntryPlate} ({DisplayVehicleType(data.EntryVehicleType)})",
            AutoSize = true,
            Font = new Font("Segoe UI", 10.5f, FontStyle.Bold),
            ForeColor = statusColor,
            Location = new Point(12, 8)
        };
        var summaryLbl = new Label
        {
            Text = $"{data.SummaryMessage}  |  Trạng thái: {Blank(data.SessionStatus)}  |  Giờ vào: {FormatTime(data.EntryTime)}  |  Giờ ra: {FormatTime(data.ExitTime)}",
            AutoSize = true,
            ForeColor = Color.FromArgb(40, 48, 60),
            Location = new Point(12, 36)
        };
        banner.Controls.Add(titleLbl);
        banner.Controls.Add(summaryLbl);
        root.Controls.Add(banner, 0, 0);

        // 2. Match Badges Bar
        var badgesFlow = new FlowLayoutPanel
        {
            Dock = DockStyle.Fill,
            FlowDirection = FlowDirection.LeftToRight,
            WrapContents = false,
            Padding = new Padding(0, 6, 0, 4)
        };
        badgesFlow.Controls.Add(CreateMatchBadge(
            $"Biển số: {(data.PlateMatched ? "KHỚP" : "LỆCH")} ({Blank(data.EntryPlate)} vs {Blank(data.ExitPlate)})",
            data.PlateMatched));
        badgesFlow.Controls.Add(CreateMatchBadge(
            $"Loại xe: {(data.VehicleTypeMatched ? "KHỚP" : "LỆCH")} ({DisplayVehicleType(data.EntryVehicleType)} vs {DisplayVehicleType(data.ExitVehicleType)})",
            data.VehicleTypeMatched));
        badgesFlow.Controls.Add(CreateMatchBadge(
            $"Khuôn mặt: {(data.FaceMatched ? "ĐẠT" : "CẦN XEM XÉT")} ({Blank(data.EntryFaceDecision)} vs {Blank(data.ExitFaceDecision)})",
            data.FaceMatched));
        badgesFlow.Controls.Add(new Label
        {
            Text = "💡 Bấm vào từng ảnh để phóng to (Zoom) soi chi tiết biển số / khuôn mặt.",
            AutoSize = true,
            ForeColor = Color.DimGray,
            Margin = new Padding(12, 7, 0, 0)
        });
        root.Controls.Add(badgesFlow, 0, 1);

        // 3. 2x2 Grid of Entry vs Exit (Vehicle & Face)
        var grid = new TableLayoutPanel
        {
            Dock = DockStyle.Fill,
            RowCount = 2,
            ColumnCount = 2
        };
        grid.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 50));
        grid.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 50));
        grid.RowStyles.Add(new ColumnStyle(SizeType.Percent, 52));
        grid.RowStyles.Add(new ColumnStyle(SizeType.Percent, 48));

        grid.Controls.Add(BuildEvidenceCard(
            "1. ẢNH XE LÚC VÀO (ENTRY VEHICLE)",
            data.EntryVehicleImageBase64,
            $"Biển số AI: {Blank(data.EntryPlate)} | Loại xe: {DisplayVehicleType(data.EntryVehicleType)} ({FormatPct(data.EntryVehicleConfidence)})",
            $"Độ tin cậy YOLO: {FormatPct(data.EntryDetectionConfidence)} | OCR: {FormatPct(data.EntryOcrConfidence)} | Mã chứng cứ: {Blank(data.EntryVehicleEvidenceId)}"), 0, 0);

        grid.Controls.Add(BuildEvidenceCard(
            "2. ẢNH XE LÚC RA (EXIT VEHICLE)",
            data.ExitVehicleImageBase64,
            $"Biển số AI: {Blank(data.ExitPlate)} | Loại xe: {DisplayVehicleType(data.ExitVehicleType)} ({FormatPct(data.ExitVehicleConfidence)})",
            $"Độ tin cậy YOLO: {FormatPct(data.ExitDetectionConfidence)} | OCR: {FormatPct(data.ExitOcrConfidence)} | Mã chứng cứ: {Blank(data.ExitVehicleEvidenceId)}"), 1, 0);

        grid.Controls.Add(BuildEvidenceCard(
            "3. KHUÔN MẶT LÚC VÀO (ENTRY FACE)",
            data.EntryFaceImageBase64,
            $"Kết quả: {Blank(data.EntryFaceDecision)} | Điểm SFace: {FormatScore(data.EntryFaceSimilarity)}",
            $"Mã chứng cứ mặt vào: {Blank(data.EntryFaceEvidenceId)}"), 0, 1);

        grid.Controls.Add(BuildEvidenceCard(
            "4. KHUÔN MẶT LÚC RA (EXIT FACE)",
            data.ExitFaceImageBase64,
            $"Kết quả: {Blank(data.ExitFaceDecision)} | Điểm SFace: {FormatScore(data.ExitFaceSimilarity)}",
            $"Mã chứng cứ mặt ra: {Blank(data.ExitFaceEvidenceId)}"), 1, 1);

        root.Controls.Add(grid, 0, 2);

        // 4. Footer Actions
        var footer = new FlowLayoutPanel
        {
            Dock = DockStyle.Fill,
            FlowDirection = FlowDirection.RightToLeft,
            Padding = new Padding(0, 8, 0, 0)
        };
        var closeBtn = new Button
        {
            Text = "Đóng",
            Width = 110,
            Height = 36,
            DialogResult = DialogResult.Cancel,
            BackColor = Color.FromArgb(230, 235, 242),
            FlatStyle = FlatStyle.Flat
        };
        closeBtn.FlatAppearance.BorderSize = 0;

        var overrideBtn = new Button
        {
            Text = "XỬ LÝ NGOẠI LỆ / CHỈNH SỬA THỦ CÔNG",
            Width = 280,
            Height = 36,
            BackColor = Color.FromArgb(217, 119, 6),
            ForeColor = Color.White,
            Font = new Font("Segoe UI", 9.5f, FontStyle.Bold),
            FlatStyle = FlatStyle.Flat
        };
        overrideBtn.FlatAppearance.BorderSize = 0;
        overrideBtn.Click += (_, _) =>
        {
            if (!hasActiveShift)
            {
                MessageBox.Show(this,
                    "Tài khoản hiện chưa có ca trực đang hoạt động. Bạn cần trong ca trực hợp lệ để thực hiện ghi nhận ngoại lệ / chỉnh sửa thủ công.",
                    "Yêu cầu ca trực hợp lệ",
                    MessageBoxButtons.OK,
                    MessageBoxIcon.Warning);
                return;
            }
            string defaultPlate = !string.IsNullOrWhiteSpace(currentPlate) ? currentPlate : (data.EntryPlate ?? "");
            string defaultType = !string.IsNullOrWhiteSpace(currentVehicleType) && currentVehicleType != "UNKNOWN"
                ? currentVehicleType
                : (data.EntryVehicleType ?? "UNKNOWN");
            using var dlg = new ManualOverrideDialog(defaultPlate, defaultType);
            if (dlg.ShowDialog(this) == DialogResult.OK)
            {
                OverrideApplied = true;
                OverrideReasonCode = dlg.ReasonCode;
                OverrideReasonText = dlg.ReasonText;
                CorrectedPlate = dlg.CorrectedPlate;
                CorrectedVehicleType = dlg.CorrectedVehicleType;
                DialogResult = DialogResult.OK;
                Close();
            }
        };

        footer.Controls.Add(closeBtn);
        footer.Controls.Add(overrideBtn);
        root.Controls.Add(footer, 0, 3);

        Controls.Add(root);
        CancelButton = closeBtn;
    }

    private Control BuildEvidenceCard(string title, string? base64Image, string primaryMeta, string secondaryMeta)
    {
        var group = new GroupBox
        {
            Text = title,
            Dock = DockStyle.Fill,
            Font = new Font("Segoe UI", 9.5f, FontStyle.Bold),
            Padding = new Padding(8),
            Margin = new Padding(4)
        };
        var layout = new TableLayoutPanel
        {
            Dock = DockStyle.Fill,
            RowCount = 3,
            ColumnCount = 1
        };
        layout.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
        layout.RowStyles.Add(new RowStyle(SizeType.AutoSize));
        layout.RowStyles.Add(new RowStyle(SizeType.AutoSize));

        var picture = new PictureBox
        {
            Dock = DockStyle.Fill,
            SizeMode = PictureBoxSizeMode.Zoom,
            BackColor = Color.FromArgb(235, 240, 246),
            Cursor = Cursors.Hand
        };
        Image? decoded = DecodeBase64Image(base64Image);
        if (decoded != null)
        {
            picture.Image = decoded;
            picture.Click += (_, _) => ShowZoomDialog(title, picture.Image, this);
        }
        else
        {
            picture.Paint += (_, e) =>
            {
                TextRenderer.DrawText(
                    e.Graphics,
                    "Chưa có ảnh lưu trữ cho mục này",
                    new Font("Segoe UI", 9.5f, FontStyle.Italic),
                    picture.ClientRectangle,
                    Color.DimGray,
                    TextFormatFlags.HorizontalCenter | TextFormatFlags.VerticalCenter);
            };
        }

        var line1 = new Label
        {
            Text = primaryMeta,
            AutoSize = true,
            Font = new Font("Segoe UI", 9f, FontStyle.Regular),
            ForeColor = Color.FromArgb(23, 54, 93),
            Margin = new Padding(2, 4, 2, 0)
        };
        var line2 = new Label
        {
            Text = secondaryMeta,
            AutoSize = true,
            Font = new Font("Segoe UI", 8.5f, FontStyle.Regular),
            ForeColor = Color.DimGray,
            Margin = new Padding(2, 2, 2, 0)
        };

        layout.Controls.Add(picture, 0, 0);
        layout.Controls.Add(line1, 0, 1);
        layout.Controls.Add(line2, 0, 2);
        group.Controls.Add(layout);
        return group;
    }

    public static void ShowZoomDialog(string title, Image? sourceImage, IWin32Window? owner = null)
    {
        if (sourceImage == null)
        {
            MessageBox.Show(owner, "Chưa có hình ảnh để phóng to.", "Phóng to ảnh", MessageBoxButtons.OK, MessageBoxIcon.Information);
            return;
        }
        using var zoomForm = new Form
        {
            Text = $"Phóng to kiểm tra ảnh — {title}",
            StartPosition = FormStartPosition.CenterParent,
            Size = new Size(960, 720),
            MinimumSize = new Size(640, 480),
            BackColor = Color.FromArgb(30, 34, 42),
            Font = new Font("Segoe UI", 9.5f)
        };

        double scale = 1.0;
        var scrollPanel = new Panel
        {
            Dock = DockStyle.Fill,
            AutoScroll = true,
            BackColor = Color.FromArgb(30, 34, 42)
        };
        var pb = new PictureBox
        {
            Image = sourceImage,
            SizeMode = PictureBoxSizeMode.Zoom,
            Width = Math.Max(400, sourceImage.Width),
            Height = Math.Max(300, sourceImage.Height)
        };
        scrollPanel.Controls.Add(pb);

        var toolbar = new FlowLayoutPanel
        {
            Dock = DockStyle.Top,
            Height = 44,
            Padding = new Padding(10, 6, 10, 6),
            BackColor = Color.FromArgb(45, 52, 64)
        };
        var zoomLabel = new Label
        {
            Text = "100%",
            AutoSize = true,
            ForeColor = Color.White,
            Font = new Font("Segoe UI", 10f, FontStyle.Bold),
            Margin = new Padding(10, 6, 10, 0)
        };

        void ApplyScale(double newScale)
        {
            scale = Math.Clamp(newScale, 0.4, 4.0);
            pb.Width = (int)(Math.Max(400, sourceImage.Width) * scale);
            pb.Height = (int)(Math.Max(300, sourceImage.Height) * scale);
            zoomLabel.Text = $"{scale:P0}";
        }

        var btnZoomIn = new Button { Text = "Phóng to (+)", AutoSize = true, BackColor = Color.White };
        var btnZoomOut = new Button { Text = "Thu nhỏ (-)", AutoSize = true, BackColor = Color.White };
        var btnReset = new Button { Text = "Mặc định (100%)", AutoSize = true, BackColor = Color.White };
        btnZoomIn.Click += (_, _) => ApplyScale(scale + 0.25);
        btnZoomOut.Click += (_, _) => ApplyScale(scale - 0.25);
        btnReset.Click += (_, _) => ApplyScale(1.0);

        toolbar.Controls.Add(btnZoomOut);
        toolbar.Controls.Add(btnReset);
        toolbar.Controls.Add(btnZoomIn);
        toolbar.Controls.Add(zoomLabel);

        zoomForm.Controls.Add(scrollPanel);
        zoomForm.Controls.Add(toolbar);
        if (owner != null) zoomForm.ShowDialog(owner);
        else zoomForm.ShowDialog();
    }

    private static Label CreateMatchBadge(string text, bool matched) => new()
    {
        Text = text,
        AutoSize = true,
        Padding = new Padding(8, 4, 8, 4),
        Margin = new Padding(0, 2, 10, 2),
        BackColor = matched ? Color.FromArgb(220, 252, 231) : Color.FromArgb(254, 226, 226),
        ForeColor = matched ? Color.FromArgb(22, 101, 52) : Color.FromArgb(153, 27, 27),
        Font = new Font("Segoe UI", 9f, FontStyle.Bold)
    };

    private static Image? DecodeBase64Image(string? base64OrDataUri)
    {
        if (string.IsNullOrWhiteSpace(base64OrDataUri)) return null;
        try
        {
            string raw = base64OrDataUri.Trim();
            int commaIdx = raw.IndexOf(',');
            if (commaIdx >= 0) raw = raw[(commaIdx + 1)..];
            byte[] bytes = Convert.FromBase64String(raw);
            using var ms = new MemoryStream(bytes);
            using var img = Image.FromStream(ms);
            return new Bitmap(img);
        }
        catch
        {
            return null;
        }
    }

    private static string DisplayVehicleType(string? type) => type switch
    {
        "BICYCLE_ELECTRIC_BICYCLE" => "Xe đạp / xe đạp điện",
        "MOTORBIKE" => "Xe máy",
        "LARGE_MOTORBIKE" => "Mô tô PKL",
        "CAR" => "Ô tô 4-5 chỗ",
        "CAR_6_7" => "Ô tô 6-7 chỗ",
        "CAR_8_9" => "Ô tô 8-9 chỗ",
        _ => string.IsNullOrWhiteSpace(type) ? "-" : type
    };

    private static string Blank(string? val) => string.IsNullOrWhiteSpace(val) ? "-" : val;
    private static string FormatPct(double? val) => val.HasValue ? $"{val.Value:P0}" : "-";
    private static string FormatScore(double? val) => val.HasValue ? $"{val.Value:0.000}" : "-";
    private static string FormatTime(DateTime? val) => val.HasValue ? val.Value.ToString("dd/MM/yyyy HH:mm:ss") : "-";
}
