# Task Plan: MicYou安卓远程按键 -> PC WinUHID注入（右Alt长按 / Alt+Space）

## Goal
在安卓APP加一个按键，长按经TCP控制通道发到PC，PC经WinUHID注入为右Alt或Alt+Space，全程只用GitHub Action云编译验证。

## Current Phase
Phase 6

## Phases

### Phase 1: 需求 grill + 方案锁定
- [x] 逐个确认按键语义、键型配置、通道复用方式
- [x] 确认分支名与云编译触发方式
- [x] Document findings in findings.md
- **Status:** complete

### Phase 2: 协议与传输设计
- [x] Android Protocol.kt补PluginMessage字段（对齐proto field 7）
- [x] AudioEngine加sendPluginMessage复用TCP写通道+Mutex
- [x] PC tcp_server收到topic=remote-key后路由到注入层
- **Status:** complete

### Phase 3: 安卓UI实现
- [x] MobileHome加按键（长按语义 onPress/onRelease）
- [x] ViewModel加sendKeyDown/sendKeyUp
- [x] strings.xml全语言补key
- **Status:** complete

### Phase 4: PC WinUHID注入实现
- [x] 移植Voice_VibeCoding hid_injector.rs（右Alt 0xA5/0x38+EXTENDED，Space 0x20/0x2C，vkE8消菜单）
- [x] WinUHID不可用时SendInput降级+日志
- [x] WinUHid.dll资产与安装指引（复用2.15驱动包）
- **Status:** complete
- 注：Q3最终为硬阻塞，前端RemoteKeyDriverDialog提示；驱动DLL打包进安装包列为Phase 6跟进

### Phase 5: 云编译验证
- [x] feature-test.yml自签名测试流（push即编Win+安卓）
- [x] 真机端到端：按键->PC注入->输入法响应
- **Status:** complete

### Phase 6: 双向唤醒（免密钥，用户明确）
- [x] 线格式MicW 8字节，UDP 8553（与8554/8555/8443错开）
- [x] PC常驻监听+记IP+显式启动顺发；悬浮/托盘/GUI统一走
- [x] 手机发广播+收包自启（开关默认开，可关）
- [ ] 云编译+双向真机验证
- **Status:** in_progress

### Phase 7: 批量需求（重试/托盘/窗口记忆/和弦/网格/悬浮设置）
- [x] gradle重试5次；托盘删发送项（悬浮入口保留）
- [x] 主窗+悬浮窗位置记忆localStorage+显示器钳制
- [x] 音量和弦上下独立多选+key-chord通道+PC解析
- [x] 底部2x2（静音+三键/回车第4格），按钮加高，状态区压缩
- [x] 悬浮窗设置ui.json持久化+显隐/单击/双击+热更新
- [ ] 云编译+真机验证
- **Status:** in_progress

## Key Questions
1. 一个键还是两个键？键型固定还是设置页可选？（推荐：一个键+设置页二选一，默认右Alt）
2. 长按语义是按住发DOWN松开发UP，还是单击发DOWN+UP？（推荐：按住DOWN/松开UP，对齐Voice_VibeCoding语音和弦）
3. 通道用PluginMessage还是新增顶层proto字段？（推荐：PluginMessage，兼容不断）
4. PC无WinUHID时阻塞还是SendInput降级？（推荐：降级+日志）
5. 分支名与CI触发：push分支+开PR还是workflow_dispatch？（推荐：feature/remote-key + 开Draft PR触发development.yml）

## Decisions Made
| Decision | Rationale |
|----------|-----------|
| 主页双键并排（用户Q1确认） | 一个发右Alt，一个发Alt+Space，无需设置页切换 |
| 按语义按下DOWN/松开UP（用户Q2确认） | 长按短点都成立，对齐按住说话 |
| 无WinUHID硬阻塞+提示（用户Q3确认） | 不SendInput降级，直接前端弹窗引导装2.15驱动 |
| Draft PR触发development.yml（用户Q4确认） | 分支feature/remote-key，push后开Draft PR到master |
| 只删opencode.yml（用户确认） | push分支不触发任何流；Draft PR只触发development（保留）+opencode（已删）；tag/release/mirrorchyan类保留，不会被分支/PR触发 |
| 组合键为右Alt+空格（用户纠正） | keyId2=[0xA5,0x20]，非左Alt |
| 语言只改英+简中（用户指示） | 其余9个语言文件已revert，缺失key回退英文 |
| 驱动不打包进安装包（用户指示） | 用DLL旁放或MICYOU_WINUHID_DLL环境变量指向 |
| 灭屏无障碍此路不通（logcat零记录） | 删无障碍及总线，音量上=右Alt+空格/下=回车（前台），UI与Enter保留 |
| 双向唤醒免密钥A+B都做（用户明确） | MicW/8553；PC常驻监听+记IP+显式启动顺发；手机发广播+收包自启（默认开） |
| 云编译只用development.yml验证功能分支 | release需tag+签名Secrets，feature阶段不需要；debug APK无须签名 |
| MicYou签名Secrets与云编译文档不同 | MicYou用ANDROID_KEYSTORE_*写workspace根release.keystore；forkgram文档仅参考思路 |

## Errors Encountered
| Error | Attempt | Resolution |
|-------|---------|------------|
| GitHub API 403 rate limit（查MicYou最新版时） | 1 | 改curl expanded_assets解析，拿到v2.0.3资产 |
| adb connect 192.168.1.121:5555被拒绝 | 1 | ping通但5555未开，按用户选择改USB安装成功 |

## Notes
- 本地无编译环境：不跑gradlew/cargo build，只做静态检查+云编译
- 先grill锁定再开分支写码
