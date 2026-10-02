<div align="center">

  <h1>MicYou-voice</h1>

  <img src="./img/app_icon.png" width="128" height="128" />

  <br>

  MicYou 语音输入法适配版：把手机变成 PC 的高品质麦克风，
  并用手机按键直接控制电脑上的语音输入。

</div>

## 声明

- 本仓库基于上游 [LanRhyme/MicYou](https://github.com/LanRhyme/MicYou) v2.0.3 构建。
- 除下面列出的改动外，所有功能、文档与使用方式**以上游仓库为准**。
- 感谢 LanRhyme 及上游所有贡献者的开源工作。
- 本 fork 只提供 **Windows 桌面端＋安卓 APK**，不提供 macOS / Linux 包。
- 语言只修改了英语和简体中文，其他语言缺失的文案会自动回退显示英文。

## 为适配语音输入法做了哪些改动

### Windows 端

- **远程按键注入**：收到手机发来的按键后，经 WinUHID 虚拟键盘注入电脑
  （右 Alt、右 Alt＋空格、回车，或自定义组合键）。
  没有驱动时直接弹窗引导安装，不做兼容性差的替代注入。
- **悬浮窗**：置顶迷你控制台，点击不抢走其他程序的焦点；
  单击 / 双击动作可在设置里配置，还能给单击、双击各配不同的发送按键；
  右键菜单：显示 / 启停 / CLI / TUI / 退出。
- **双向唤醒**：电脑端常驻监听，启动服务时顺手唤醒手机。
- **窗口位置记忆**：主窗口和悬浮窗下次打开回到上次的位置。

### 安卓端

- **底部远程按键**：主页底部新增按键，按住即按下、松开即松开，
  可发右 Alt、右 Alt＋空格、回车等组合键。
- **音量键组合键**：前台使用时，音量上 / 下可各配一组发送按键
  （默认上＝右 Alt＋空格，下＝回车）。
- **双向唤醒**：收到电脑的唤醒包后自动开始连接。
- **主页更紧凑**：连接状态和开始 / 停止按钮同排显示；
  连接断开只提示"远程设备断开连接"。

## 无解问题

- 灭屏时按音量键，系统直接调音量，不分发按键事件，不支持改键。

## 下载安装

1. 从本仓库 [Releases](https://github.com/8099bb4X21/MicYou/releases) 下载：
   `MicYou-Android-<日期>-voice.apk`、`MicYou-Win-<日期>-voice-installer.exe`
   （`<日期>` 为构建日期，如 `20261002`）。
2. 手机装 APK，电脑装 Windows 包，按上游文档配好虚拟麦（VB-CABLE）。
3. 同一 Wi-Fi 下选 Wi-Fi 模式，或开 USB 调试连线后选 USB 模式，点开始即可。
4. 远程按键需要装 WinUHID 驱动（新版 MicYou 会自动找到 `WinUHid.dll`，无需手动导入）：

   | 驱动包 | UMDF | 适用系统 | 下载 |
   |---|---|---|---|
   | `WinUHid_Win10_2.15.zip` | 2.15 | Windows 10 1507 及以上 / Windows 11 | 本仓库 Release 附件 |
   | `WinUHid_Win10_2.23.zip` | 2.23 | Windows 10 1709 及以上 / Windows 11 | [8099bb4X21/WinUHid](https://github.com/8099bb4X21/WinUHid) 的 Release |
   | Voice_VibeCoding 编译的 2.33 | 2.33 | 仅 Windows 11 21H2 及以上 | [Voice_VibeCoding Releases](https://github.com/mwlt/Voice_VibeCoding/releases) |

   装法：解压 → 右键 `Run-Install.cmd` 以管理员身份运行 → 双击 `Run-Status.cmd` 看到 `device reachable`。
   > 三版驱动的安装脚本都会把 `WinUHid.dll` 释放到
   > `%LOCALAPPDATA%\com.remote-bridge-hub.app\winuhid`，新版 MicYou 会自动从那里加载。
   > 仅当驱动已装好、远程按键仍提示缺 DLL 时，才在弹窗里手动选中驱动包中的
   > `WinUHid.dll`（会复制到 `%APPDATA%\micyou\WinUHid.dll`）。

## License

同上游：[GNU General Public License v3.0 with MicYou Plugin Exception](./LICENSE)
