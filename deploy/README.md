# Deploy Spring Web bằng Docker + Tailscale Funnel

Tài liệu này dùng để dựng lại web quản trị bãi xe trên máy Windows, public qua Tailscale Funnel.

- URL public: `https://lumipark.tail57e32c.ts.net`
- Web trong Docker: `127.0.0.1:8080`
- ANPR chạy trên máy thật: `127.0.0.1:8001`
- Desktop WinForms chạy trên máy thật: `http://localhost:8080`

> Không chạy đồng thời `scripts\run-web.cmd` và Docker, vì cả hai cùng dùng cổng 8080.

---

## 1. Yêu cầu

| Thành phần | Ghi chú |
|---|---|
| Docker Desktop | Đã bật, dùng Linux containers |
| Tailscale | Đã đăng nhập, MagicDNS + HTTPS Certificates đã bật |
| OpenSSL | Có sẵn trong Git for Windows |
| ANPR Service | Chạy `scripts\run-anpr.cmd`, cổng 8001 |
| Laragon | Chỉ cần nếu dùng chế độ `laragon` hoặc fallback |

---

## 2. Chuẩn bị lần đầu

### 2.1. Sinh JWT key

```cmd
powershell -ExecutionPolicy Bypass -File deploy\gen-jwt-keys.ps1
```

Kết quả:

```text
deploy\secrets\jwt-private.pem
deploy\secrets\jwt-public.pem
```

Không chạy lại script khi đã có key, trừ khi muốn vô hiệu hoá toàn bộ token cũ.

### 2.2. Tạo file cấu hình

```cmd
copy deploy\.env.deploy.example deploy\.env.deploy
notepad deploy\.env.deploy
```

Điền tối thiểu:

- `PARKING_DB_MODE`: `auto`, `aiven` hoặc `laragon`
- Thông tin Aiven: `PARKING_AIVEN_*`
- Thông tin Laragon: `PARKING_LARAGON_*`
- `PARKING_INTERNAL_SERVICE_TOKEN`: chuỗi dài ngẫu nhiên
- `PARKING_DEMO_ADMIN_PASSWORD`: mật khẩu mạnh

Lưu ý:

- Không đổi `PARKING_SECURITY_JWT_ISSUER` nếu vẫn dùng domain cũ.
- Nếu mật khẩu có ký tự `#`, đặt trong dấu nháy kép.
- Không commit `.env.deploy` và `deploy/secrets/*.pem`.

### 2.3. Chuẩn bị Laragon (nếu dùng)

1. Mở `my.ini`, đặt `bind-address=0.0.0.0`.
2. Tạo user cho container:

```sql
CREATE USER 'parking'@'%' IDENTIFIED BY 'mat-khau-manh';
GRANT ALL PRIVILEGES ON parking_anpr.* TO 'parking'@'%';
FLUSH PRIVILEGES;
```

3. Mở firewall cổng 3306 cho dải mạng nội bộ.
4. Kiểm tra từ máy:

```cmd
mysql -h 127.0.0.1 -P 3306 -u parking -p parking_anpr
```

---

## 3. Build và chạy

```cmd
cd deploy
docker compose up -d --build
docker compose ps
docker compose logs -f web
```

Trong log cần thấy một trong hai dòng:

```text
[db] dang dung Aiven (...)
[db] dang dung Laragon (...)
```

Nếu container thoát ngay, xem:

```cmd
docker compose logs --tail=200 web
```

---

## 4. Kiểm tra

### 4.1. Health

```cmd
curl http://127.0.0.1:8080/api/parking/health
```

### 4.2. Smoke test

```cmd
powershell -ExecutionPolicy Bypass -File deploy\smoke-test.ps1 -Password "mat-khau-admin"
```

Hoặc không truyền mật khẩu:

```cmd
set PARKING_ADMIN_PASSWORD=mat-khau-admin
powershell -ExecutionPolicy Bypass -File deploy\smoke-test.ps1
```

### 4.3. Trình duyệt

- Local: `http://localhost:8080`
- Public: `https://lumipark.tail57e32c.ts.net`

Kiểm tra:

- Đăng nhập được.
- Sơ đồ bãi hiển thị đủ ô.
- Chặn một ô trên web, Desktop cập nhật trong tối đa 5 giây.

---

## 5. Tailscale Funnel

```cmd
tailscale funnel --bg 8080
tailscale funnel status
```

Funnel chỉ chuyển tiếp tới `127.0.0.1:8080`, nên Docker phải publish đúng cổng này.

Sau khi reboot, kiểm tra lại:

```cmd
tailscale status
tailscale funnel status
docker compose ps
```

Nếu laptop ngủ, web sẽ mất kết nối. Đặt power plan không ngủ khi cắm điện.

---

## 6. Chế độ database

| Giá trị | Hành vi |
|---|---|
| `auto` | Thử Aiven trước, thất bại thì chuyển Laragon |
| `aiven` | Chỉ dùng Aiven, không fallback |
| `laragon` | Chỉ dùng Laragon |

- Aiven được thử tối đa 2 lần, mỗi lần timeout 8 giây.
- Trên VPS không có Laragon, luôn đặt `PARKING_DB_MODE=aiven`.
- Aiven và Laragon là hai bộ dữ liệu khác nhau. Đổi chế độ có thể thấy dữ liệu khác.

Đổi chế độ:

```cmd
notepad deploy\.env.deploy
cd deploy
docker compose up -d
```

---

## 7. Vận hành

### Xem log

```cmd
cd deploy
docker compose logs -f web
```

### Khởi động lại

```cmd
docker compose restart web
```

### Dừng

```cmd
docker compose down
```

### Cập nhật code

```cmd
cd deploy
docker compose up -d --build
```

### Kiểm tra biến môi trường đã nạp

```cmd
docker compose config
```

---

## 8. Sao lưu và phục hồi

### Aiven

```cmd
mysqldump -h HOST -P PORT -u USER -p --ssl-mode=REQUIRED DB > backup.sql
```

### Laragon

```cmd
mysqldump -u root -p parking_anpr > backup.sql
```

### Phục hồi

```cmd
mysql -h HOST -P PORT -u USER -p DB < backup.sql
```

Trước buổi demo nên sao lưu một lần và lưu file ra ngoài thư mục project.

---

## 9. Lỗi thường gặp

| Hiện tượng | Nguyên nhân | Cách xử lý |
|---|---|---|
| Container thoát ngay | Sai thông tin DB hoặc SSL | Xem `docker compose logs web`, kiểm tra `PARKING_AIVEN_*` |
| `thieu PARKING_AIVEN_URL` | Chưa điền `.env.deploy` | Điền đủ biến, chạy lại `docker compose up -d` |
| Không kết nối Laragon | `bind-address`, user `%`, firewall | Kiểm tra 3 mục ở phần 2.3 |
| Funnel trả 502 | Container chưa lên hoặc sai cổng | `docker compose ps`, `curl 127.0.0.1:8080/api/parking/health` |
| Đăng nhập xong quay lại login | Cookie secure hoặc sai issuer | Giữ `SERVER_SERVLET_SESSION_COOKIE_SECURE=false`, kiểm tra issuer |
| Desktop báo 401 | JWT key đổi hoặc Desktop giữ token cũ | Đăng xuất/đăng nhập lại Desktop |
| Web chậm khi khởi động | Đang chờ probe Aiven | Bình thường, tối đa khoảng 16 giây |
| Cổng 8080 bị chiếm | Đang chạy `scripts\run-web.cmd` | Tắt tiến trình cũ rồi chạy lại Docker |

---

## 10. Bảo mật

- Không commit `deploy/.env.deploy`.
- Không commit `deploy/secrets/*.pem`.
- Đổi mật khẩu admin ngay lần đăng nhập đầu.
- Không mở cổng 3306 ra Internet.
- Token DuckDNS cũ đã lộ trong ảnh, nên thu hồi nếu còn dùng.
- Khi đổi domain, cập nhật `PARKING_SECURITY_JWT_ISSUER` và đăng nhập lại Desktop.

---

## 11. Rollback

```cmd
cd deploy
docker compose down
```

Sau đó:

1. Khôi phục file `.env.deploy` cũ.
2. Khôi phục database từ file backup.
3. Chạy lại `docker compose up -d --build`.

Nếu lỗi do JWT key mới, khôi phục 2 file PEM cũ trong `deploy/secrets/` rồi khởi động lại container.
