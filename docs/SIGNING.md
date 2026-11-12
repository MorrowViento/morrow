# 签名与发版

> 一句话：**密钥在你手里，仓库里什么都没有。** 构建时用 `~/.gradle/gradle.properties`（本地）或环境变量（CI）把四个参数喂给 Gradle，`assembleRelease` 自动签名；没喂参数时 release 保持未签名，debug 完全不受影响。

## 为什么必须换成自己的密钥

- 仓库里的 debug keystore 是 Android SDK 自动生成的，路径随机器变、卸载重装就可能换一份。用它发的包，**以后同一个应用装不上去**（签名不一致会被系统拒绝），也无法在应用商店上架。
- 换密钥的代价是**不可逆**的：已发布版本的签名一旦变了，老用户只能卸载重装（应用数据随卸载清空）。所以密钥要在第一次对外发版前定下来。
- 这次同时改了 `applicationId`（`com.jiuxina.ying` → `com.morrowviento.morrow`），旧「萤」和新「朝夕」在系统里是两个独立应用。真机上装新包前**先卸载旧的「萤」**，旧数据用「设置 → 数据 → 导出备份」先存一份，新包再用「导入」恢复。

## 一、生成密钥

### 推荐：用仓库里的脚本

```bash
cd morrow
export JAVA_HOME="/c/Users/yunbo.wang/WorkBuddy/2026-11-09-15-57-01/.toolchain/jdk/jdk-21.0.13+9"
./scripts/create-keystore.sh
```

脚本会：

1. 在 `~/.morrow/morrow-release.jks` 生成 RSA 4096、有效期 10000 天的密钥；
2. 密码由你输入两次、不回显、不写进任何文件；
3. 打印要追加到 `~/.gradle/gradle.properties` 的四行配置。

想换位置就传参数：`./scripts/create-keystore.sh ~/keys/chaoxi.jks`。

### 手动（效果一样）

```bash
keytool -genkeypair -v \
  -keystore ~/.morrow/morrow-release.jks \
  -alias morrow \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=Chaoxi, OU=Mobile, O=MorrowViento, C=CN"
```

`-alias`、`-dname` 只是元数据，写什么不影响安装；**密码、别名、keystore 文件三者必须记住**。

## 二、把参数交给 Gradle

四个键，环境变量优先，其次用户级 `gradle.properties`：

| 环境变量 | gradle.properties | 含义 |
|---|---|---|
| `MORROW_KEYSTORE` | `morrow.keystore` | keystore 文件绝对路径，支持 `~/` |
| `MORROW_KEYSTORE_PASSWORD` | `morrow.storePassword` | keystore 密码 |
| `MORROW_KEY_ALIAS` | `morrow.keyAlias` | 密钥别名，脚本默认 `morrow` |
| `MORROW_KEY_PASSWORD` | `morrow.keyPassword` | 密钥密码（用同一个密码即可） |

本地推荐写 **用户级** 文件 `~/.gradle/gradle.properties`（Windows 是 `C:\Users\<你>\.gradle\gradle.properties`）：

```properties
morrow.keystore=/c/Users/yunbo.wang/.morrow/morrow-release.jks
morrow.storePassword=你的密码
morrow.keyAlias=morrow
morrow.keyPassword=你的密码
```

> 不要写进仓库根目录的 `gradle.properties`。`.gitignore` 虽然忽略了 `*.jks`，但**不会忽略属性文件里的明文密码**。

只想临时用一次，也可以：

```bash
export MORROW_KEYSTORE="$HOME/.morrow/morrow-release.jks"
export MORROW_KEYSTORE_PASSWORD='...'
export MORROW_KEY_ALIAS='morrow'
export MORROW_KEY_PASSWORD='...'
```

## 三、构建并验证

```bash
export JAVA_HOME="/c/Users/yunbo.wang/WorkBuddy/2026-11-09-15-57-01/.toolchain/jdk/jdk-21.0.13+9"
../.toolchain/gradle-9.6.0/bin/gradle :app:assembleRelease --console=plain
```

产物：`app/build/outputs/apk/release/app-release.apk`（开了 `-Pmorrow.abiSplit=true` 时还会有按 ABI 拆分的几个包）。

验证签名：

```bash
"$JAVA_HOME/bin/keytool" -printcert -jarfile app/build/outputs/apk/release/app-release.apk
# 或者用 build-tools 里的 apksigner
$ANDROID_HOME/build-tools/37.0.0/apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

输出里的 `CN=Chaoxi, O=MorrowViento` 与 SHA-256 指纹就是你的密钥，记下来，以后每次发版都该是同一枚指纹。

## 四、CI（GitHub Actions）

在仓库 Secrets 里加四个：`MORROW_KEYSTORE_BASE64`（把 `.jks` 做 base64）、`MORROW_KEYSTORE_PASSWORD`、`MORROW_KEY_ALIAS`、`MORROW_KEY_PASSWORD`，构建前还原成文件并导出同名环境变量即可。**不要把 `.jks` 或密码提交进仓库**，即使是私有仓库。

## 五、换签名 / 换包名之后

1. 手机上先**导出备份**（设置 → 数据 → 导出），再卸载旧的「萤」。
2. 安装新「朝夕」，用「导入」把事件恢复回来；小部件样式会随备份一起导入（按 `widget.default_style` 兜底）。
3. 如果之前用 debug 包装过同名新包，也必须先卸载：debug 与 release 签名不同，覆盖安装会报 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`。
4. 重新排期提醒、重新添加桌面小部件（`applicationId` 变了，旧的 PendingIntent / widgetId 都不再属于新应用）。

## 六、备份密钥（最重要的一步）

- 把 `morrow-release.jks` 与密码分开存两处（例如密码管理器 + 离线介质）。
- 丢了密钥 = 永远无法给已发布版本发更新，只能换 `applicationId` 重新上架，老用户数据无法继承。
- 不要把密钥提交到 Git、网盘公开链接、聊天记录或 CI 日志里。

## 七、常见报错

| 报错 | 原因 | 处理 |
|---|---|---|
| `找不到 keystore：...` | 路径写错，或用了 `~` 但指向了别的用户 | 用绝对路径；脚本生成时打印的就是绝对路径 |
| `Keystore was tampered with, or password was incorrect` | 密码不对 | 核对 `morrow.storePassword` / `MORROW_KEYSTORE_PASSWORD` |
| `Cannot recover key` | `keyPassword` 与 `storePassword` 不一致 | 用同一个密码，或填生成时设置的 key 密码 |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | 设备上已有不同签名的同包名应用 | 先卸载旧包（数据先导出） |
| 构建通过但 APK 未签名 | 没提供 `morrow.keystore`，走了「未签名 release」分支 | 按第二节配置四项参数 |

安全提示：Gradle 的 `signingConfigs` 在配置期就会读取密码，构建日志里不要开 `--info` 之外的调试输出，也不要把密码 echo 到终端。
