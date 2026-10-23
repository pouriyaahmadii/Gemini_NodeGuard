# Gemini NodeGuard (محافظ نود جمینای)

[![Go Report Card](https://goreportcard.com/badge/github.com/pouriyaahmadii/gemini-nodeguard)](https://goreportcard.com/report/github.com/pouriyaahmadii/gemini-nodeguard)

ابزاری قدرتمند، توسعه‌پذیر و بدون هیچ وابستگی خارجی (Zero-Dependency) به زبان Go برای تست کانفیگ‌ها و سابسکریپشن‌های پروکسی (VLESS, VMess, Trojan, Shadowsocks, Hysteria2) که با هدف عبور از محدودیت‌ها و تحریم‌های منطقه‌ای (Geoblocking) سرویس‌های گوگل نظیر Gemini، AI Studio و NotebookLM طراحی شده است.

---

## چرا استفاده از فورک؟ (اولویت با حریم خصوصی)

این ابزار با در نظر گرفتن حفظ کامل حریم خصوصی شما طراحی شده است. لینک‌های اشتراک و اطلاعات سرور شما نباید به دست هیچکس بیفتد.

با کلیک روی گزینه **Fork** در گیت‌هاب و راه‌اندازی فرآیند اتوماسیون `Personal Node Check`:
- هیچ گزارشی از نودهای شما در ریپازیتوری عمومی آپلود نمی‌شود.
- نتایج در صورت تمایل توسط سیستم `age` کاملا رمزنگاری می‌شوند.
- امکان اتصال مستقیم خروجی نودهای سالم به **Secret Gist** شخصی شما وجود دارد تا به راحتی لینک آپدیت را مستقیماً داخل برنامه‌هایی مثل v2rayN یا V2Box قرار دهید.

جهت آموزش کامل راه‌اندازی سیستم اتوماسیون گیت‌هاب، به [راهنمای فورک و حریم خصوصی (انگلیسی)](FORK_GUIDE.md) مراجعه کنید.

---

## نحوه نصب و استفاده

**نسخه باینری:**
جدیدترین نسخه باینری را از بخش [Releases](#) دانلود کنید و در ترمینال اجرا نمایید:
```bash
./nodeguard -sub "https://subscription.url" -report report.json -out-dir ./output
```

**کامپایل از سورس:**
```bash
go build -o nodeguard ./cmd/checker
```

### پروتکل‌های پشتیبانی شده

| پروتکل | امنیت (Security) | انتقال/وب‌سوکت | توضیحات |
|---|---|---|---|
| **VLESS** | TLS, Reality, None | TCP, WS, gRPC | پشتیبانی کامل از `ed` (early data) |
| **VMess** | TLS, None | TCP, WS, gRPC | استخراج صحیح فرمت‌های Legacy مثل `type=http` |
| **Trojan** | TLS, None | TCP, WS, gRPC | |
| **Shadowsocks** | None | TCP | پشتیبانی کامل از فرمت SIP002 Base64 |
| **Hysteria2** | TLS | TCP (شبیه‌سازی UDP) | اجرای خودکار با موتور Sing-Box |
