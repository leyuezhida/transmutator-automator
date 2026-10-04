# Transmutator Automator（嬗变台自动助手）

**纯客户端** Forge 模组（1.20.1），为 [Alex's Mobs](https://modrinth.com/mod/alexs-mobs) 的**嬗变台**提供自动化。

女仆挖矿是另一个独立模组，见 `D:\biancheng\minecraft\forge-1.20.1-47.4.22-mdk`，两者互不相关。

## 它做什么

打开嬗变台界面后，模组会自动检查界面上出现的三个候选物品：
**一旦出现你标记的物品就立刻选它**，直到刷够目标数量为止。

- 目标物品可任意指定，支持注册名（`minecraft:diamond`）或游戏内中文名（`钻石`）
- 每次嬗变的间隔可配置（默认 4 tick）
- 经验不足时自动停止，不会把你卡在界面里
- 目标数量默认 64 个

## 怎么用

1. 装好本模组与 Alex's Mobs（客户端即可，**服务器不需要装**）
2. 在嬗变台里**放 2 个**你想刷的物品（提权重用 2 个最划算，见下）
3. 用命令标记目标：
   ```
   /ta mark 钻石          # 也接受 minecraft:diamond
   /ta list               # 查看当前标记
   /ta count 128          # 想刷 128 个就改数量
   /ta status             # 看当前状态
   ```
4. 打开嬗变台，模组自动接管

> 注意：第 2 步的放物品需要你自己做。**纯客户端模组无法自动把物品放进嬗变台**——
> 那是服务端容器的状态变更，客户端只能通过"点击"请求，没有权限直接改。

## 为什么纯客户端就够

嬗变台的点击**不是原版容器点击**，而是一个自定义网络包：

```java
MessageTransmuteFromMenu(int playerId, int choice)
```

`choice` 就是候选下标（0/1/2），对应界面上三个按钮。这个包没有坐标校验、没有权限校验，
纯客户端模组可以自己发 —— 效果与按按钮完全一致。

随机结果、经验扣除、物品入包全在服务端，模组**不碰**这些，
所以它不会凭空造物，也不绕过任何服务端校验。

## 权重规律（影响你怎么摆物品）

来自 [MC百科 item/635753](https://www.mcmod.cn/item/635753.html)：

| 操作 | 权重变化 |
|------|----------|
| 放入 N 个 | + log₁₀(N)³ |
| 换出 N 个 | − log₁₀(N)⁴ |

- **提权重时放 2 个**（配置项 `advice.weightGainCount`）——
  wiki 说 3 个最佳，但 2 个材料省一半、增益已可观
- **只想刷新候选列表时放 1 个**（`advice.rerollCount`）—— 不产生任何权重变化

放进去的物品如果不在默认池里（如下界合金），会从权重 0 开始累积，
**权重降到 0 就再也刷不到了**。想稳一点就先放够数量。

## 配置

客户端配置：`config/transmutator_automator.toml`

| 键 | 默认 | 说明 |
|----|------|------|
| `general.enabled` | false | 总开关。**首次使用请先开这个** |
| `general.intervalTicks` | 4 | 两次嬗变的间隔（tick） |
| `general.stopOnLowExp` | true | 经验不足时自动停止 |
| `target.targetCount` | 64 | 刷到多少个就停 |
| `target.requireAllThree` | false | true = 三个候选全是目标才点（更保守） |
| `target.markerItems` | 空 | 标记物品列表，建议用 `/ta mark` 填 |
| `advice.weightGainCount` | 2 | 提权重时建议放几个（仅提示，不自动执行） |
| `advice.rerollCount` | 1 | 刷新列表时建议放几个 |

## 实现要点

候选物品在客户端**没有任何公开 API 能拿到**：

- `GUITransmutationTable`（客户端侧）只有三个按钮字段，不持有候选
- `TileEntityTransmutationTable` 是服务端对象
- 消息的 `Handler.handle` 是静态逻辑，无从订阅

所以用 Mixin 在 `MessageUpdateTransmutablesToDisplay.Handler#handle` 的 HEAD 处
把 `stack1/2/3` 抄到本地快照（见 `CandidateSnapshot`）。
`playerId` 是 private，用 `@Accessor` 读取。

## 构建

```bash
./gradlew jar
```

`Alex's Mobs` 的 jar 放在 `mcmodsrepo/`（已 gitignore，不对外分发），
由 `.pom` 提供本地 maven 入口供 `fg.deobf` 重映射。

## 已知限制

- 放物品需手动（原因见上）
- 模组无法"重掷"候选 —— 嬗变台每次只能刷一次才换候选，想拿目标只能等它自然出现
- 依赖 Alex's Mobs 的内部类结构，该模组大版本更新时可能失效
