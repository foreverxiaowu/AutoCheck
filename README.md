# AutoCheck · 圆通国际 / 17TRACK / 顺丰国际批量查询

Java 17 桌面程序，圆通国际每批最多查询 10 单，17TRACK 每批最多查询 40 单，顺丰国际每批最多查询 20 单，按各单号的最新物流信息匹配自定义条件。各平台独立保存设置、进度及浏览器会话，无需搭建数据库或 Web 服务。

v1.1.0 新增 17TRACK 和顺丰国际查询，支持独立筛选条件与进度保存，并修复 Excel 不间断空格导入及 Java 版本选择问题。

**Windows 安装版：[下载 EXE 安装包](https://github.com/foreverxiaowu/AutoCheck/releases/download/v1.1.0/AutoCheck-1.1.0.exe)** · [版本说明与使用说明](https://github.com/foreverxiaowu/AutoCheck/releases/tag/v1.1.0)

安装包已包含 Java 和查询浏览器，适用于 Windows 10/11 的 Intel / AMD 64 位电脑。

## 启动

面向 Windows 10/11 的 Intel / AMD 64 位电脑，已提供两种自带 Java 和查询浏览器的发行包，目标电脑无需安装 Java、Maven、Chrome 或 Edge：

- **安装版**：运行 `dist/AutoCheck-1.1.0.exe`，按向导安装后从桌面或开始菜单打开。默认按当前用户安装到 `%LOCALAPPDATA%\AutoCheckApp`。
- **免安装版**：将 `dist/AutoCheck-1.1.0-Windows-x64-Portable.zip` 完整解压，然后运行 `AutoCheck/AutoCheck.exe`。复制到其他电脑时需要复制整个目录。

安装和查看已有结果可以离线进行；实时查询需要能访问圆通官网。Windows 安装包不能直接用于 macOS、Linux、32 位 Windows 或手机，不承诺兼容 Windows 7/8。安装包尚未配置商业代码签名证书，发布者可能显示为“未知发布者”。

**开发运行**仍可双击 `start.cmd`：这种方式需要本机 Java 17 或更高版本，以及 Chrome 或 Edge。没有 JAR 时，脚本会调用 Maven 构建。

启动脚本会优先检查 `JAVA_HOME`，版本不兼容时再查找 `PATH` 中的 Java，并让 Maven 和程序使用同一版本。首次构建需要 JDK 17 或更高版本和 Maven。如果手动运行 Maven，请用 `mvn -version` 检查实际使用的 Java；仅检查 `java -version` 不足以确认 Maven 的 Java 版本。

也可以在项目目录运行：

```powershell
java '-Dfile.encoding=UTF-8' -jar target/autocheck-1.1.0.jar
```

## 使用

1. 粘贴单号，或点击“导入 TXT / CSV / Excel”。`examples/waybills.txt` 是你提供的 10 个样例单号。
2. 在“筛选条件与设置”中配置地点、物流信息、包含/精确匹配、查询间隔，点击“保存并应用”。预置了清关中、成都签收、海关审核三个条件。
3. 点击“开始全部查询”。发行包会打开内置的独立查询浏览器；开发 JAR 使用本机 Chrome/Edge。每批 10 单，默认批次间隔 3 秒。
4. 遇到图片验证码时，在程序弹窗中手动填写；验证码失效可刷新。遇到网页验证时，到打开的官网查询窗口完成验证，再回到弹窗点击继续。当前批次会保留并重试。
5. 选择“匹配条件”“未匹配条件”“查询失败”等视图；也可以按单号、最新地点、物流信息或匹配条件名称搜索。选中运单可查看完整轨迹。
6. “导出当前列表”生成 UTF-8 BOM CSV；“导出完整轨迹”生成 JSON。导出遵循当前筛选和搜索条件，仅导出已查询的结果，列表中的待查询项不包含在导出文件中。

### 导入格式

- TXT / CSV：只放单号列，可有“运单号”等表头；每行一个，也支持空格、逗号、分号分隔。兼容 UTF-8 和 GB18030。
- XLSX / XLS：读取第一个工作表的 A 列，可有“运单号”等表头，其他列忽略。单号建议存为文本，避免 Excel 丢失前导零或长数字精度。公式单元格会提示先转换为文本值。
- 自动去重，保留输入顺序和大小写。只接受含数字的 5–50 位字母、数字、连字符组合；无效内容会阻止启动并提示修正。

### 17TRACK 查询

1. 顶部“查询平台”选择 **17TRACK**。原圆通功能仍在“圆通国际”入口。
2. 选择物流公司，默认 **中通国际（190175）**。“物流公司配置”支持添加、修改、删除公司名称与 `fc` 编码，编码必须是唯一的正整数，需与 17TRACK 官网一致。每次导入的一组单号使用同一家物流公司；不同公司分开查询。
3. 导入或粘贴单号后点击“开始全部查询”，每批最多 40 单。暂停、停止、继续未完成/失败项、完整轨迹查看及导出与圆通相同。
4. 程序打开独立的 17TRACK 官网浏览器，由网页生成当前请求的 `sign`、会话 `guid` 并调用 `https://t.17track.net/track/restapi`。请求的 `data` 使用各单号及所选 `fc`，`sc=0`，官网当前使用 `timeZoneOffset=-480`。不保存或复用样例中的长签名。
5. 出现安全验证时，手动在浏览器里完成，再回到程序点击“已在浏览器验证，继续”。当前批次不会提前标为完成。不要在该浏览器里改成其他单号或物流公司；修改本轮查询请停止任务后在程序中操作。

预设三条独立条件：**清关持续中、快件已开始清关、成都青龙场**，均匹配最新一条物流进度的描述文字。可在“筛选条件与设置”修改文字与匹配方式。

17TRACK 结果列表、轨迹详情、搜索和导出只使用物流进度，不显示独立的地点、国家或地区，也不再判断是否在中国大陆。进度描述中原本包含的地名照常保留。旧设置中的纯地区条件自动移除，带物流文字的条件移除位置限制，仅填写地点的条件转为匹配进度文字。原有圆通查询规则保持不变。

解析 `shipments[].shipment.latest_event` 和 `tracking.providers[].events`，合并去重后按带时区的事件时间倒序排列。逐单失败、漏返单号及仍在处理的单号不会误标为“暂无轨迹”。成功但确实没有记录才显示“暂无轨迹”。

17TRACK 属于官网查询流程，页面、签名流程或响应格式变化时可能需要更新程序。已验证真实网站会触发人工验证；无人值守测试不自动完成验证码。可选的真实网站检查会在返回结果或需要人工验证时结束。

### 顺丰国际查询

1. 顶部“查询平台”选择 **顺丰国际**，导入或粘贴单号，每批最多 20 单。示例见 `examples/sf-waybills.txt`。
2. 程序打开顺丰国际官网，填入当前批次并点击查询。请在该浏览器中手动完成验证码或手机号校验，然后回到程序点击继续。若官网要求再次点击查询，保持当前单号后点击即可。
3. “筛选条件与设置”中的条件单独保存，默认提供 **清关进行中** 和 **海关查验**，支持添加、删除、包含与精确匹配。列表、轨迹详情和导出只展示物流进度。

读取 `result[].barNewList[]` 中的 `remark`，按 `barScanDt + barScanTm` 和每条事件的 `localTimeZone` 换算后倒序排列，只有最新一条进度参与筛选。`scanDate` 不用于替代事件日期。样例 `SF5158351178464` 最新进度为“客户已取消寄件”，历史“清关进行中”不会使它命中清关条件。

查询通过官网正常流程调用 `/site/core-service/web/bills/<单号列表>/routes`，使用当前会话的验证票据，不复用示例 URL 中的 `ticket` 或 `tk`，不自动完成安全验证码。官网或验证流程变化可能需要更新程序；真实网站检查仅验证到查询结果或人工验证步骤。

顺丰数据单独存于 `sf/settings.json`、`sf/progress.json` 和 `sf/browser-profile/`，暂停、继续、导入导出与其他入口相同。切换平台后恢复各自进度。

### 通用筛选规则

只检查每个运单号时间最新的一条轨迹，不以历史上的清关、签收记录匹配当前状态。轨迹按事件时间与返回时区换算后倒序排列；缺失时区时使用 UTC 排序，并保留原始时间文本供查看。

- 圆通同一条件的“地点”和“物流信息”同时满足才匹配，留空的字段不限；17TRACK 只匹配物流进度文字。
- 多个条件满足任一条即匹配；结果显示所有命中的条件名称。
- “未匹配条件”仅包含有轨迹但没有命中条件的运单。“暂无轨迹”“查询失败”“待查询”分别展示。
- 修改筛选条件可直接重筛已有结果；修改时间视角对后续查询生效。

### 暂停、停止与续查

- 暂停/停止在当前请求返回后生效；已返回的批次仍会保存。请求超时为 30 秒。
- 每批完成后原子写入数据目录的 `progress.json`，重启后自动恢复。人工验证未通过的批次不会标为完成。
- “继续未完成 / 失败项”跳过已有成功及暂无轨迹项，仅查询新单号、未完成项和失败项。
- “开始全部查询”会刷新当前输入的全部单号，并替换上一轮进度。需要保留旧结果时请先导出。
- 网络错误或 HTTP 5xx 最多自动重试 2 次；验证请求始终等待人工处理。磁盘保存失败会中断任务，防止未保存就进入下一批。

## 数据与接口

安装版和免安装版都把数据保存在 `%LOCALAPPDATA%\AutoCheck`，与安装目录及启动工作目录无关。开发运行时默认位于项目的 `data/`。通过 JVM 参数 `-Dautocheck.data=D:/other-directory` 可以更改数据目录。

发布包只包含程序、Java、浏览器和使用说明，不包含开发电脑的查询记录、配置或浏览器 Cookie。更新、卸载时保留用户数据目录。跨电脑迁移时可复制 `progress.json` 和 `settings.json`，并在目标电脑重新完成人工验证。

| 文件/目录 | 用途 |
| --- | --- |
| `settings.json` | 筛选规则、批次间隔、时间视角 |
| `progress.json` | 运单号、查询状态、完整轨迹、错误信息 |
| `browser-profile/` | 此程序独立的浏览器会话和 Cookie |
| `app.lock` | 防止同一数据目录被多个程序同时使用 |
| `17track/carriers.json` | 17TRACK 物流公司列表及当前选中公司 |
| `17track/settings.json` | 17TRACK 独立筛选规则及批次间隔 |
| `17track/carriers/<fc>/progress.json` | 按物流公司分开的 17TRACK 单号与进度 |
| `17track/browser-profile/` | 17TRACK 独立浏览器会话 |

切换平台或物流公司时保存当前单号、规则及进度；查询运行期间不能切换。删除物流公司配置不会删除已有进度，重新添加相同编码后可以恢复。

接口：`POST https://www.ytoglobal.com/api/ytoApi/getTrajectory`

```json
{
  "waybillNos": ["G10433030158", "G10434275624"],
  "language": "zh-CN",
  "timeZoneType": "senderView"
}
```

通过浏览器同源 `fetch` 发起请求，使用该会话的 Cookie。`timeZoneType` 支持 `senderView` / `consigneeView`。官网返回 `code: 1000001` 时展示其 Base64 验证码，用户手动输入的值通过 `x-rate-limit-code` 请求头提交；验证码本身不写入进度文件。没有实现验证码自动识别。

官网的 `data.detailResponses` 是多个单号混合的扁平数组。程序按事件的 `waybillNo` 分组，缺失时回退到 `referenceChangeNo`，保留完整历史；不会使用顶层的 `waybillNo: "null"` 分组。接口失败与成功但无轨迹分别处理。

## 构建与验证

```powershell
mvn test
mvn package
# 可选：渲染桌面，截图写入 target/ui-smoke.png
mvn '-Dautocheck.uiSmoke=true' test
# 可选：使用本机 Edge 运行全部本地浏览器与界面测试（浏览器测试拦截全部网络请求）
mvn '-Dautocheck.uiSmoke=true' '-Dautocheck.browserSmoke=true' test
# 可选：真实 17TRACK 查询，可能要求人工验证，不自动作答
mvn '-Dautocheck.track17LiveSmoke=true' '-Dtest=SmokeTest#track17LiveEndpointReturnsResultsOrManualVerification' test
# 可选：访问真实圆通接口，使用样例 10 个单号；可能触发人工验证
mvn '-Dautocheck.liveSmoke=true' '-Dtest=SmokeTest#liveEndpointReturnsAnExplicitResultOrManualVerification' test
# 构建后验证最终 JAR 中的 Excel、JSON、浏览器驱动及桌面界面
java -cp 'target/test-classes;target/autocheck-1.1.0.jar' cn.autocheck.PackagedSmoke
# 构建自带 Java 和浏览器的 Windows 安装版及免安装版
powershell -ExecutionPolicy Bypass -File packaging/build-windows.ps1
# 可选：真实安装到测试目录、启动检查，然后卸载；不会操作已存在的同产品安装
powershell -ExecutionPolicy Bypass -File packaging/test-installer.ps1
```

发行构建使用 JDK 的 `jpackage`、可移植 WiX 3.14.1、Eclipse Temurin OpenJDK 17 JRE、与 Playwright 1.65.1 匹配的浏览器 revision 1255。首次构建从官方来源下载依赖并缓存到 `packaging/cache/`；Temurin 压缩包会校验官方 SHA-256。目标电脑不需要这些构建工具。

构建脚本会在只有 Windows 系统目录的 PATH、无有效 JAVA_HOME、不同启动工作目录的条件下，通过原生 `AutoCheck.exe` 执行离线自检：验证使用内置 Java 和浏览器、Excel 导入、JSON 保存及桌面窗口启动。`dist/build-manifest.json` 记录依赖版本及发行包校验值。`AutoCheck.exe --self-test <report.json>` 可显式重新运行离线自检。

测试样本来自你提供的 124 条轨迹响应，已省略操作员和内部订单等无关字段。真实接口冒烟检查只报告返回类型和条数，遇到验证时仅报告需要人工验证，不自动作答。

主要代码：`BrowserGateway` 负责浏览器与接口，`BatchRunner` 负责分批与人工验证等待，`TrajectoryParser` 负责分组及排序，`FilesService` 负责导入、导出及持久化，`MainWindow` 负责桌面界面。

依赖文档：[Playwright Java 浏览器支持](https://playwright.dev/java/docs/browsers)、[Apache POI](https://poi.apache.org/)。
