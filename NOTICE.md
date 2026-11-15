# 第三方资源与法律声明 / Third-Party Notices

本文件说明 PixelShelf 使用了哪些第三方资源，以及发布时需要注意的合规事项。

## 1. 本仓库包含什么 / 不包含什么

**包含**：应用源码（Java / XML）、构建脚本、图标设计文件。

**不包含**：

- ❌ 任何游戏 ROM（`.gba` `.nds` `.iso` 等）
- ❌ 任何模拟器程序或其代码
- ❌ BIOS / 固件文件
- ❌ 任何游戏封面图片
- ❌ 签名密钥（`.gitignore` 已排除）

用户需自行安装模拟器、自备合法获得的 ROM。

## 2. 封面索引（`index_*.txt`）

应用为了实现离线封面匹配，需要一份「游戏封面文件名列表」。这份索引**不随仓库分发**，由 `build_index.py` 在构建时从公开图库抓取生成。

- 数据来源：[libretro-thumbnails](https://github.com/libretro-thumbnails)（MIT 组织下的公开缩略图库）
- 内容性质：仅游戏**标题名称**（事实性元数据），不含图片本身
- 该索引已被 `.gitignore` 排除，构建者可自行重新生成或省略（省略后应用退化为纯本地封面，功能仍可用）

如果你认为这份数据不适合被再生成，请删除 `build_index.py` 并跳过该步骤，应用仍可正常构建和使用（只是没有自动封面）。

## 3. 封面图片

应用运行时按游戏名从 `thumbnails.libretro.com` **按需下载**封面图，仅缓存到本机，**不分发、不打包**。

- 封面图版权归各自游戏发行商所有（任天堂、索尼等）
- 应用仅作「本地显示」用途
- 用户可在设置中关闭「自动从网络获取封面」，关闭后应用完全不联网
- 若你是权利人并要求停止，请提交 issue，会移除对应功能或改用可配置图源

### 对图源服务的友好措施

为避免给他人免费提供的公共服务造成负担，代码中做了以下限制：

- **串行请求**，两次请求之间至少间隔 1200 ms（`CoverFetcher.MIN_GAP_MS`）
- **不重复下载**：已缓存的游戏直接读本地文件
- **限流退避**：收到 `429` / `503` 时读取 `Retry-After` 并退避，本轮放弃
- **标识来源**：`User-Agent: PixelShelf/<版本> (Android)`
- **可替换**：设置里可改为自建镜像（仅允许 https），也可完全关闭

**如果你打算大规模使用（例如做成公开服务），请自建镜像，不要依赖默认图源。**

## 4. 商标

Game Boy、Game Boy Advance、Nintendo DS、Pokémon 为任天堂株式会社商标；PlayStation、PlayStation Portable 为索尼互动娱乐商标。

本应用**与上述公司无任何关联、未获其授权或背书**。代码中出现的平台名称、模拟器包名仅用于说明兼容性与互操作性（nominative fair use）。

## 5. 模拟器兼容列表

`Emulators.java` 中列出的模拟器包名（My Boy!、DraStic、PPSSPP、RetroArch 等）是各自由第三方开发者独立开发的软件。本应用不包含、不修改、不再分发它们，仅通过系统 Intent 调用。相关商标归各自所有者。

## 6. 本应用自身的许可

MIT License，见 [LICENSE](LICENSE)。

## 7. 隐私

- 无广告、无统计埋点、无账号、无崩溃上报
- 不收集任何个人数据
- 唯一网络行为：下载封面（可关闭）
- 唯一文件写入：自身缓存目录 `Android/data/com.pixelshelf.app/files/`
- `android:allowBackup="false"`，数据不会被系统备份导出

## 8. 发布前检查清单

- [ ] 确认 `git status` 中没有 `*.keystore`、`*.apk`、`app/src/main/assets/index_*.txt`
- [ ] 正式 Release 使用固定的签名密钥，并通过 CI Secrets 注入（`PIXELSHELF_KEYSTORE` / `PIXELSHELF_KS_PASS` 等环境变量）
- [ ] 不要在 Release 中附带任何 ROM 或模拟器
- [ ] 保留本 NOTICE 与 LICENSE
