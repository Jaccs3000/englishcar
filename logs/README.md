# English Car Phone Logs

This folder stores USB Logcat captures from the physical phone.

- Start capture from `C:\englishcar`: `powershell -ExecutionPolicy Bypass -File .\logs\start_phone_log.ps1 -Serial ZY32HLST49`
- Stop capture from `C:\englishcar`: `powershell -ExecutionPolicy Bypass -File .\logs\stop_phone_log.ps1`
- Clear captures from `C:\englishcar`: `powershell -ExecutionPolicy Bypass -File .\logs\clear_phone_logs.ps1`
- Latest capture: `.\logs\englishcar_phone_latest.log`

Default physical device serial: `ZY32HLST49`.
