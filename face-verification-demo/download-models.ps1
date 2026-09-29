$ErrorActionPreference = 'Stop'
$demoDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$modelsDir = Join-Path $demoDir 'models'
New-Item -ItemType Directory -Force -Path $modelsDir | Out-Null

$models = @(
    @{
        Name = 'face_detection_yunet_2023mar.onnx'
        Url = 'https://github.com/opencv/opencv_zoo/raw/main/models/face_detection_yunet/face_detection_yunet_2023mar.onnx'
    },
    @{
        Name = 'face_recognition_sface_2021dec.onnx'
        Url = 'https://github.com/opencv/opencv_zoo/raw/main/models/face_recognition_sface/face_recognition_sface_2021dec.onnx'
    }
)

foreach ($model in $models) {
    $destination = Join-Path $modelsDir $model.Name
    if (Test-Path $destination) {
        Write-Host "Found $($model.Name), skipping." -ForegroundColor DarkGray
        continue
    }
    Write-Host "Downloading $($model.Name)..." -ForegroundColor Cyan
    Invoke-WebRequest -Uri $model.Url -OutFile $destination
}

Write-Host 'YuNet and SFace models are ready.' -ForegroundColor Green
