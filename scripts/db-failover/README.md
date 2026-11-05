# Tự chuyển DB khi Aiven mất kết nối

Watchdog kiểm tra Aiven và MySQL local mỗi 15 giây. Khi Aiven không kết nối
được, nó tạo lại container web bằng
`docker compose up -d --force-recreate --no-deps web`; entrypoint
`PARKING_DB_MODE=auto` sẽ tự chọn Laragon. Khi Aiven hoạt động lại, watchdog
chạy đồng bộ một lần rồi tạo lại web để quay lại Aiven.

## Chạy

```cmd
REM Kiểm tra một lần, không tạo lại container
scripts\db-failover\watch-db.ps1 -DryRun -Once

REM Chạy nền
scripts\start-db-failover.cmd
```

`scripts\start-auto-parking.cmd` tự bật watchdog. Dùng `--no-failover` nếu không
muốn bật.

## Điều kiện

- `deploy\.env.deploy` phải có `PARKING_DB_MODE=auto`.
- MySQL local phải chạy được trước khi chuyển sang Laragon.
- Watchdog dùng `mysql.exe` của Laragon và file cấu hình client rỗng để tránh
  `my.ini` hệ thống.
- Khi quay lại Aiven, watchdog chạy `sync_two_way.py --once` trước để đẩy dữ
  liệu phát sinh ở local lên Aiven.

## Cấu hình mặc định

- Kiểm tra mỗi 15 giây.
- Aiven lỗi 2 lần liên tiếp mới chuyển sang local.
- Aiven sống lại 2 lần liên tiếp mới thử quay lại Aiven.
- Nếu sync hoặc tạo lại web thất bại, thử lại sau 1 phút.

Có thể truyền tham số:

```cmd
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\db-failover\watch-db.ps1 `
  -IntervalSeconds 10 -FailureThreshold 3 -SuccessThreshold 3
```

## Log và trạng thái

- `scripts/db-failover/logs/failover.log`
- `scripts/db-failover/.state.json`
- `scripts/db-failover/.watch.lock`

## Lưu ý

- Cơ chế này tạo lại container web, nên có vài giây gián đoạn. Dùng
  `--force-recreate` thay vì `restart` để `deploy\.env.deploy` được nạp lại.
- Nếu cả Aiven và MySQL local đều lỗi, watchdog không thể chuyển DB.
- Xóa cứng vẫn không được đồng bộ; xem thêm `scripts/sync/README.md`.
