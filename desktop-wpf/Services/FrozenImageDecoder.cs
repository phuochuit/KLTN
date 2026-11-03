using System.IO;
using System.Windows.Media.Imaging;

namespace LumiPark.Desktop.Services;

internal static class FrozenImageDecoder
{
    public static BitmapSource? DecodeBase64(string data, int maxWidth = 1600, int maxHeight = 1200)
    {
        try
        {
            var comma = data.IndexOf(',');
            var bytes = Convert.FromBase64String(comma >= 0 ? data[(comma + 1)..] : data);
            using var stream = new MemoryStream(bytes);
            var image = new BitmapImage();
            image.BeginInit(); image.CacheOption = BitmapCacheOption.OnLoad;
            image.DecodePixelWidth = maxWidth; image.DecodePixelHeight = maxHeight;
            image.StreamSource = stream; image.EndInit(); image.Freeze();
            return image;
        }
        catch (Exception ex) when (ex is FormatException or IOException or NotSupportedException) { return null; }
    }
}
