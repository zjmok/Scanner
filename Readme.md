
### 实现了扫码（包含选图识别）和生成二维码

```
// https://github.com/journeyapps/zxing-android-embedded
implementation("com.journeyapps:zxing-android-embedded:4.3.0") { isTransitive = false }
// https://github.com/zxing/zxing/releases
implementation("com.google.zxing:core:3.5.3")
```

### 在公有目录写入文件，适配了 Android 10+ 的 MediaStore 

将 bitmap 保存为图片 jpeg
