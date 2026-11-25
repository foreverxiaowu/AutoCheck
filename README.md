# AutoCheck · 圆通国际 / 17TRACK / 顺丰国际 / EWE / 4PX 批量查询

Java 17 桌面程序，圆通国际每批最多查询 10 单，17TRACK 每批最多查询 40 单，顺丰国际每批最多查询 20 单，EWE 与 4PX 每批查询 10 单，按各单号的最新物流信息匹配自定义条件。各平台独立保存设置与进度，无需搭建数据库或 Web 服务。

v1.2.1 新增结果勾选、多选复制单号、按勾选导出 Excel，并缩小顶部统计卡片。17TRACK 与顺丰国际支持 Windows 内嵌官网验证预览，17TRACK 新增默认（0）及洋包裹（101107）配置。保留 EWE、4PX 查询、EWE 地区分类、彩色界面、自适应表格和统一图标；安装版供所有用户使用，安装与卸载需要管理员授权，没有管理员权限时可使用免安装版。

**已发布的 v1.1.0：[下载 EXE 安装包](https://github.com/foreverxiaowu/AutoCheck/releases/download/v1.1.0/AutoCheck-1.1.0.exe)** · [版本说明与使用说明](https://github.com/foreverxiaowu/AutoCheck/releases/tag/v1.1.0)。v1.2.1 构建产物位于本机 `dist/`，此链接仍是旧版。

安装包已包含 Java 和查询浏览器，适用于 Windows 10/11 的 Intel / AMD 64 位电脑。

## 启动

面向 Windows 10/11 的 Intel / AMD 64 位电脑，已提供两种自带 Java 和查询浏览器的发行包，目标电脑无需安装 Java、Maven、Chrome 或 Edge：

- **安装版**：运行 `dist/AutoCheck-1.2.1.exe`，授权管理员权限，按向导安装后从桌面或开始菜单打开。默认安装到 `%ProgramFiles%\AutoCheckApp`，供所有用户使用。
- **免安装版**：将 `dist/AutoCheck-1.2.1-Windows-x64-Portable.zip` 完整解压，然后运行 `AutoCheck/AutoCheck.exe`。复制到其他电脑时需要复制整个目录。

1.1.0 是按用户安装的版本，升级到 1.2.1 前请在原安装用户下卸载旧版；不同安装范围不能保证自动升级。关闭窗口后，程序会先等待当前查询、保存已经返回的结果并关闭查询浏览器，再退出整个进程。`app.lock` 文件可以保留，文件本身存在不会阻止启动。

如果旧版卸载出现 `D:\Config.Msi\*.rbf` 的 Error 5，即使安装目录和列表项已经消失，也可能还有未结束的 Windows Installer 回滚/权限处理。先结束或取消原卸载任务，再排查该目录和具体文件的管理员、SYSTEM 权限。新安装包不会自动修复系统回滚目录，也不要通过删除整个 `Config.Msi`、放开整个磁盘权限或禁用回滚来绕过错误。

安装和查看已有结果可以离线进行；实时查询需要能访问圆通官网。Windows 安装包不能直接用于 macOS、Linux、32 位 Windows 或手机，不承诺兼容 Windows 7/8。安装包尚未配置商业代码签名证书，发布者可能显示为“未知发布者”。

**开发运行**仍可双击 `start.cmd`：这种方式需要本机 Java 17 或更高版本，以及 Chrome 或 Edge。没有 JAR 时，脚本会调用 Maven 构建。

启动脚本会优先检查 `JAVA_HOME`，版本不兼容时再查找 `PATH` 中的 Java，并让 Maven 和程序使用同一版本。首次构建需要 JDK 17 或更高版本和 Maven。如果手动运行 Maven，请用 `mvn -version` 检查实际使用的 Java；仅检查 `java -version` 不足以确认 Maven 的 Java 版本。

也可以在项目目录运行：

```powershell
java '-Dfile.encoding=UTF-8' -jar target/autocheck-1.2.1.jar
```

## 使用

桌面界面使用 Java Swing + FlatLaf 浅色主题，提供蓝紫渐变顶部、彩色查询统计卡片和状态颜色。导入、继续查询、暂停、停止及导出操作集中显示；结果、筛选设置和日志分别查看。结果表格随窗口宽度自适应，“物流进度”列优先利用剩余空间；较窄时保留单号、时间等列的必要宽度并支持横向滚动。

1. 粘贴单号，或点击“导入 TXT / CSV / Excel”。`examples/waybills.txt` 是你提供的 10 个样例单号。
2. 在“筛选条件与设置”中配置地点、物流信息、包含/精确匹配、查询间隔，点击“保存并应用”。预置了清关中、成都签收、海关审核三个条件。
3. 点击“开始全部查询”。发行包会打开内置的独立查询浏览器；开发 JAR 使用本机 Chrome/Edge。每批 10 单，默认批次间隔 3 秒。
4. 遇到图片验证码时，在程序弹窗中手动填写；验证码失效可刷新。遇到网页验证时，到打开的官网查询窗口完成验证，再回到弹窗点击继续。当前批次会保留并重试。
5. 选择“匹配条件”“未匹配条件”“查询失败”等视图；也可以按单号、最新地点、物流信息或匹配条件名称搜索。选中运单可查看完整轨迹。
6. 在查询结果第一列勾选需要的运单，可多选，也可点击“全选当前列表”或“清空勾选”。点击行查看轨迹与勾选是两个操作；待查询项不可勾选。
7. “复制勾选单号”仅复制勾选项的物流单号，每行一个，不带表头、状态或物流详情，保留前导零和长单号。
8. “导出勾选 Excel”生成真正的 `.xlsx` 工作簿，单号保存为文本，保留前导零和长单号；“导出勾选完整轨迹”仍生成 JSON。两种导出都只处理勾选项。

勾选按运单号绑定，排序不会选错单。搜索或筛选隐藏的勾选项仍会被复制和导出，页面显示勾选总数和隐藏数量；可先“清空勾选”，再“全选当前列表”以只处理当前筛选结果。导出顺序先按当前可见列表排序，再按原输入顺序追加隐藏勾选项。切换平台、物流公司或开始全部重新查询会清空勾选，继续查询保留已选结果；勾选状态不跨程序重启保存。

### 导入格式

- TXT / CSV：只放单号列，可有“运单号”等表头；每行一个，也支持空格、逗号、分号分隔。兼容 UTF-8 和 GB18030。
- XLSX / XLS：读取第一个工作表的 A 列，可有“运单号”等表头，其他列忽略。单号建议存为文本，避免 Excel 丢失前导零或长数字精度。公式单元格会提示先转换为文本值。
- 自动去重，保留输入顺序和大小写。只接受含数字的 5–50 位字母、数字、连字符组合；无效内容会阻止启动并提示修正。

### 17TRACK 查询

1. 顶部“查询平台”选择 **17TRACK**。原圆通功能仍在“圆通国际”入口。
2. 选择物流公司，内置 **中通国际（190175）**、**中国邮政（3011）**、**递四方（190094）**、**默认（0）**、**洋包裹（101107）**，初始选中中通国际。编码 0 由官网默认识别物流公司，允许返回识别后的公司编码；其他编码按所选公司查询。升级会一次性补入本版新增的默认和洋包裹，保留已有名称、当前选择和自定义公司，不恢复之前删除的旧预置公司；之后自行删除的公司不会反复补回。“物流公司配置”支持添加、修改、删除公司名称与 `fc` 编码，编码必须是唯一的非负整数，需与 17TRACK 官网一致。各配置的查询进度分开保存。
3. 导入或粘贴单号后点击“开始全部查询”，每批最多 40 单。暂停、停止、继续未完成/失败项、完整轨迹查看及导出与圆通相同。
4. 程序打开独立的 17TRACK 官网浏览器，由网页生成当前请求的 `sign`、会话 `guid` 并调用 `https://t.17track.net/track/restapi`。请求的 `data` 使用各单号及所选 `fc`，`sc=0`，官网当前使用 `timeZoneOffset=-480`。不保存或复用样例中的长签名。
5. 出现安全验证时，手动在浏览器里完成，再回到程序点击“已在浏览器验证，继续”。当前批次不会提前标为完成。不要在该浏览器里改成其他单号或物流公司；修改本轮查询请停止任务后在程序中操作。

Windows 可在开始查询前勾选顶部 **“内嵌官网（预览）”**：将本次查询使用的 Chromium 窗口放入程序的验证窗口，沿用同一页面、Cookie 和会话，并随验证窗口大小调整。请手动完成官网验证，再点击“验证完成，继续查询”。点击“切回独立浏览器”可恢复外部窗口；关闭验证窗口或点击“停止查询”会停止当前任务并保留进度。预览默认关闭，支持 17TRACK 和顺丰国际；嵌入失败会回退到独立浏览器并在日志提示。跨进程窗口嵌入在不同 Windows 缩放设置及浏览器版本下可能存在显示或焦点兼容问题。

预设三条独立条件：**清关持续中、快件已开始清关、成都青龙场**，均匹配最新一条物流进度的描述文字。可在“筛选条件与设置”修改文字与匹配方式。

17TRACK 结果列表、轨迹详情、搜索和导出只使用物流进度，不显示独立的地点、国家或地区，也不再判断是否在中国大陆。进度描述中原本包含的地名照常保留。旧设置中的纯地区条件自动移除，带物流文字的条件移除位置限制，仅填写地点的条件转为匹配进度文字。原有圆通查询规则保持不变。

解析 `shipments[].shipment.latest_event` 和 `tracking.providers[].events`，合并去重后按带时区的事件时间倒序排列。逐单失败、漏返单号及仍在处理的单号不会误标为“暂无轨迹”。成功但确实没有记录才显示“暂无轨迹”。

17TRACK 属于官网查询流程，页面、签名流程或响应格式变化时可能需要更新程序。已验证真实网站会触发人工验证；无人值守测试不自动完成验证码。可选的真实网站检查会在返回结果或需要人工验证时结束。

### 顺丰国际查询

1. 顶部“查询平台”选择 **顺丰国际**，导入或粘贴单号，每批最多 20 单。示例见 `examples/sf-waybills.txt`。
2. 程序打开顺丰国际官网，填入当前批次并点击查询。Windows 可先勾选顶部 **“内嵌官网（预览）”**，将同一查询浏览器放入程序的“顺丰国际 · 官网验证”窗口，支持随窗口缩放。请在官网中手动完成验证码或手机号校验，再点击“验证完成，继续查询”；若官网要求再次点击查询，保持当前单号后点击即可。可点击“切回独立浏览器”，内嵌失败会自动回退并记录日志；关闭验证窗口会停止任务并保留进度。预览默认关闭，未勾选时仍使用独立浏览器。
3. “筛选条件与设置”中的条件单独保存，默认提供 **清关进行中** 和 **海关查验**，支持添加、删除、包含与精确匹配。列表、轨迹详情和导出只展示物流进度。

读取 `result[].barNewList[]` 中的 `remark`，按 `barScanDt + barScanTm` 和每条事件的 `localTimeZone` 换算后倒序排列，只有最新一条进度参与筛选。`scanDate` 不用于替代事件日期。样例 `SF5158351178464` 最新进度为“客户已取消寄件”，历史“清关进行中”不会使它命中清关条件。

查询通过官网正常流程调用 `/site/core-service/web/bills/<单号列表>/routes`，使用当前会话的验证票据，不复用示例 URL 中的 `ticket` 或 `tk`，不自动完成安全验证码。官网或验证流程变化可能需要更新程序；真实网站检查仅验证到查询结果或人工验证步骤。

顺丰数据单独存于 `sf/settings.json`、`sf/progress.json` 和 `sf/browser-profile/`，暂停、继续、导入导出与其他入口相同。切换平台后恢复各自进度。

### EWE 查询

顶部“查询平台”选择 **EWE**，导入单号后开始查询，每批最多 10 单。示例见 `examples/ewe-waybills.txt`。通过 `GET https://www.epintl.com.au/oms/api/tracking/emetro/<逗号分隔单号>` 直接查询，不需要打开浏览器。

读取 `Payload[].ConsignmentNo` 归属单号，`Details[].DateString` 为事件时间，`Place` 为地区，`Message` 为物流详情。保留完整历史，按事件时间倒序排列；同一时间的记录按接口原顺序倒序显示。接口未提供时区，不推测地区时区或进行跨时区换算。只用最新一条轨迹筛选，不使用顶层寄件地、目的地或汇总 Status 替代轨迹。

- 结果页的“地区”输入框按最新轨迹地点包含筛选，可与“匹配条件”视图、搜索叠加使用。
- 结果页“位置分类”可选择“中国大陆 / 大陆以外 / 地址未知”，并显示每票运单的当前位置分类。这里国内按中国大陆判断，境外含港澳台。
- 点击“国内 / 境外关键词配置”维护国家、城市及别名，一行一个或逗号分隔。默认识别成都、苏州、重庆、法兰克福 / Frankfurt / Germany、悉尼等常见地点；英文不区分大小写且按完整词匹配。仅按最新 `Place` 判断，不从物流文字或目的地猜测国家；未命中或同时命中两类时归为未知。保存后立即重筛已有结果。
- 在“筛选条件与设置”中可配置地点、物流信息及包含/精确匹配。同一条件的地点和物流信息须同时满足，多个条件满足任意一条即匹配。默认提供“包裹清关中”“已到达中国”“已领取”，可自由修改。
- 设置与进度分别保存在 `ewe/settings.json` 和 `ewe/progress.json`；支持暂停、停止、续查、TXT/CSV/Excel 导入和勾选结果的 Excel/JSON 导出，导出包含地点与完整物流详情。

地区分类关键词保存在 `ewe/regions.json`。筛选条件中的“所在区域”可与地点、物流信息同时使用，例如：

| 条件 | 所在区域 | 地点 | 物流信息 |
| --- | --- | --- | --- |
| 当前在国内 | 中国大陆 | 留空 | 留空 |
| 当前在境外 | 大陆以外 | 留空 | 留空 |
| 当前在成都 | 中国大陆 | 成都 | 留空 |
| 成都清关中 | 中国大陆 | 成都 | 清关中 |

以上示例使用“包含”匹配，“成都”能匹配“成都市 金牛区”。每条条件内的字段同时满足，多条条件满足任意一条即可。需要按英文城市文字筛选时，可另外配置地点为 `Chengdu` 的条件。

本次本地源码已新增 EWE；现有 GitHub 安装包须重新构建发布后才会包含此功能。开发运行可使用重新构建后的 `start.cmd`。

### 4PX 查询

顶部“查询平台”选择 **4PX**，导入单号后开始查询。当前按每批 10 单发送 `POST https://track.4px.com/track/v2/front/listTrackV3`，请求包含 `queryCodes`、`language=zh-cn` 和空的 `translateLanguage`。样例见 `examples/4px-waybills.txt`。此入口直接请求接口，无需打开浏览器。

按 `data[].queryCode` 对应查询单号，读取 `tracks[].tkDesc` 作为物流进度，用带时区的 `tkDate` 确定最新一条。显示时优先使用接口的 `tkTimezone`；未提供时，从 `tkDateStr` 与绝对时间的对应关系恢复显示时区，否则使用 `tkDate` 自带时区。不根据寄件地或目的地推测时间。

仅配置物流文字筛选，支持包含和精确匹配，默认提供“清关中”“清关完成”“派送中”“已代收”。只匹配最新进度，历史状态不参与当前筛选，不显示或筛选独立地区字段；物流描述中原本包含的地名仍保留。支持 TXT/CSV/Excel 导入、暂停、续查及勾选结果的 Excel/JSON 导出。

4PX 设置与进度分别存于 `4px/settings.json` 和 `4px/progress.json`。当前本地源码及重新构建的 JAR 已包含该入口，GitHub 的既有 EXE 安装包需重新构建发布后才会包含。

### 通用匹配规则

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
# 可选：使用 EWE 样例运单访问真实接口
mvn '-Dautocheck.eweLiveSmoke=true' '-Dtest=EweTest' test
# 可选：使用 4PX 样例运单访问真实接口
mvn '-Dautocheck.fpxLiveSmoke=true' '-Dtest=FpxTest' test
# 可选：使用本机 Edge 运行全部本地浏览器与界面测试（浏览器测试拦截全部网络请求）
mvn '-Dautocheck.uiSmoke=true' '-Dautocheck.browserSmoke=true' test
# 可选：Windows 原生窗口嵌入、缩放、验证继续及独立浏览器切换（本地页面）
mvn '-Dautocheck.embeddedSmoke=true' '-Dtest=EmbeddedBrowserTest' test
# 可选：真实 17TRACK 查询，可能要求人工验证，不自动作答
mvn '-Dautocheck.track17LiveSmoke=true' '-Dtest=SmokeTest#track17LiveEndpointReturnsResultsOrManualVerification' test
# 可选：访问真实圆通接口，使用样例 10 个单号；可能触发人工验证
mvn '-Dautocheck.liveSmoke=true' '-Dtest=SmokeTest#liveEndpointReturnsAnExplicitResultOrManualVerification' test
# 构建后验证最终 JAR 中的 Excel、JSON、浏览器驱动及桌面界面
java -cp 'target/test-classes;target/autocheck-1.2.1.jar' cn.autocheck.PackagedSmoke
# 构建自带 Java 和浏览器的 Windows 安装版及免安装版
powershell -ExecutionPolicy Bypass -File packaging/build-windows.ps1
# 构建过程也会两次启动并关闭真实 EXE，确认原生启动器/JVM 都退出且运行锁已释放
powershell -ExecutionPolicy Bypass -File packaging/test-app-exit.ps1
# 可选：在管理员 PowerShell 中真实安装到测试目录、启动检查，然后卸载；已有 AutoCheck 安装时拒绝测试
powershell -ExecutionPolicy Bypass -File packaging/test-installer.ps1
```

发行构建使用 JDK 的 `jpackage`、可移植 WiX 3.14.1、Eclipse Temurin OpenJDK 17 JRE、与 Playwright 1.65.1 匹配的浏览器 revision 1255。首次构建从官方来源下载依赖并缓存到 `packaging/cache/`；Temurin 压缩包会校验官方 SHA-256。目标电脑不需要这些构建工具。

构建脚本会在只有 Windows 系统目录的 PATH、无有效 JAVA_HOME、不同启动工作目录的条件下，通过原生 `AutoCheck.exe` 执行离线自检：验证使用内置 Java 和浏览器、Excel 导入、JSON 保存及桌面窗口启动。`dist/build-manifest.json` 记录依赖版本及发行包校验值。`AutoCheck.exe --self-test <report.json>` 可显式重新运行离线自检。

测试样本来自你提供的 124 条轨迹响应，已省略操作员和内部订单等无关字段。真实接口冒烟检查只报告返回类型和条数，遇到验证时仅报告需要人工验证，不自动作答。

主要代码：`BrowserGateway` 负责浏览器与接口，`BatchRunner` 负责分批与人工验证等待，`TrajectoryParser` 负责分组及排序，`FilesService` 负责导入、导出及持久化，`MainWindow` 负责桌面界面。

依赖文档：[Playwright Java 浏览器支持](https://playwright.dev/java/docs/browsers)、[Apache POI](https://poi.apache.org/)。
