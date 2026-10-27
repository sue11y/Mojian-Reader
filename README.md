# 墨间 Mojian Reader

墨间是一款纯本地、无账号、无服务器的 Android 电子书阅读器。数据保存在设备上，不上传、不追踪。

## 主要功能

- 导入 EPUB、TXT、HTML、Markdown、DOCX、PDF
- TXT 自动识别 UTF-8 / GB18030，并按章节标题拆分
- 本地书架、分类、阅读状态、进度和书签
- 书名、作者、分类以及全文搜索
- 书源 JSON 模型和书源管理页
- 书源搜索、目录解析、在线章节阅读
- 在线收藏与章节/段落断点恢复
- 当前章节及后续章节的在线缓存
- 8 套阅读主题、字号调节、上下滚动和左右翻页
- 本地数据完整备份导出

## 技术栈

- Kotlin 2.0.21
- Jetpack Compose
- Android 35，最低支持 Android 8.0
- `HttpURLConnection` 负责书源请求
- Jsoup 解析书源页面
- 本地 JSON 文件存储，无内置账号体系

## 构建

```powershell
.\gradlew.bat assembleDebug
```

Debug APK 输出到 `app/build/outputs/apk/debug/`。

## 说明

项目本身不内置第三方书源规则。用户可以在书源页自行导入有权访问的 JSON 书源。
