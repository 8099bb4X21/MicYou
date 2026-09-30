# Findings & Decisions

## Requirements
- 安卓APP新增按键，点击/长按后PC注入特定按键
- 键型：右Alt长按，或组合键Alt+Space
- fork地址：https://github.com/8099bb4X21/MicYou，本机有SSH（已验证git ls-remote通）
- 新开分支实现，本地无编译环境，只能GitHub Action云编译
- 云编译参考：E:\OpenCode\云编译\签名密钥配置说明.md（forkgram方案，需映射到MicYou）

## Research Findings
- grill定案：Q1双键并排 / Q2按下DOWN松开UP / Q3无驱动硬阻塞提示 / Q4 Draft PR触发development.yml，分支feature/remote-key已建并push（65e987b + feature-test.yml cc32cbe + core修复7a524b5）
- 新需求评估（2026-09-28，未动手）：①去连接后动态效果→MobileHome.kt:714-730（240.dp visualizer + 200.dp connecting动画），VisualizerStyle现有6种无None，设置页MobileSettingsContent.kt:540可选；②声控PTT→手机AudioEngine.kt:929每buffer已有AudioLevelData（rms/rmsDb），PC dsp.rs:1761有VAD能量门（默认关，阈值-40dB，仅做音频fade不产事件）
- MicYou线协议：TCP 8554/MicY控制 + UDP port+1/MicU音频；wifi/mDNS，usb走adb reverse，同TCP通道（AGENTS.md:11）
- PC已接插件总线：tauri-app/src-tauri/src/tcp_server.rs:725 plugin_message -> bus.handle_incoming()
- proto已预留：tauri-app/crates/micyou-protocol/proto/network.proto:7 PluginMessage（source/target/topic/payload/correlationId），proto3兼容老客户端
- 安卓缺口：composeApp/.../network/Protocol.kt:156 MessageWrapper无pluginMessage字段，需补
- 安卓UI：composeApp/.../ui/MobileHome.kt单Activity Compose，加Button/FAB极小
- Voice_VibeCoding注入：src-tauri/src/bridges/xiaomi/hid_injector.rs 633行，modifier_bit 0xA5->0x40，vk_usage Space 0x20->0x2C，右Alt扫描码0x38+EXTENDED，ALT_MENU_SUPPRESS_DUMMY_VK=0xE8；voice_inject.rs确认豆包/千问过滤SendInput须优先WinUHID
- WinUHID驱动：winuhid-fix/WinUHid修复报告.md Win10 19045须UMDF 2.15（官方2.33永不加载），复用WinUHid_Win10_2.15.zip，自签证书进Root+TrustedPublisher，需UAC/重启，Secure Boot关最稳
- MicYou CI：development.yml push到main/master/develop或PR才触发，build-android用assembleDebug免签名；release.yml才需ANDROID_KEYSTORE_*（workspace根release.keystore）+tag v*；Tauri Win走npm ci+rust stable+Inno Setup，无需签名
- 云编译文档差异：forkgram用KEYSTORE_BASE64/KEY_ALIAS/APP_ID/APP_HASH写TMessagesProj/config/release.keystore；MicYou用ANDROID_KEYSTORE_*四件套，两者不可照抄

## Technical Decisions
| Decision | Rationale |
|----------|-----------|
| 复用PluginMessage(topic=remote-key) | 不动顶层proto，老版本跳过未知字段不断连；比新增字段改动面小 |
| 移植hid_injector.rs到MicYou | 右Alt/Alt+Space/消菜单逻辑已验证，不重写 |
| 按住DOWN/松开UP | 对齐Voice_VibeCoding语音和弦与微信/豆包按住说话 |
| 无WinUHID时SendInput降级 | 不静默阻塞，日志+通知可查 |

## Issues Encountered
| Issue | Resolution |
|-------|------------|
| GitHub API 403 | curl expanded_assets/v2.0.3解析出APK+exe直链 |
| adb无线5555拒绝 | USB直装成功，包com.lanrhyme.micyou v2.0.3 |

## Resources
- fork: git@github.com:8099bb4X21/MicYou.git（master e0c01d1）
- 本地clone：E:/OpenCode/test/micyou-fork
- 云编译说明：E:/OpenCode/云编译/签名密钥配置说明.md
- 注入参考：E:/OpenCode/test/Voice_VibeCoding/src-tauri/src/bridges/xiaomi/hid_injector.rs
- 驱动包：E:/OpenCode/test/winuhid-fix/WinUHid_Win10_2.15.zip

## Visual/Browser Findings
- 无

---
*Update this file after every 2 view/browser/search operations*
