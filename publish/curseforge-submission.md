# CurseForge 上传材料

> 提交时逐项复制。**所有文本字段必须英文**（官方明确要求）。
> 规则来源：https://support.curseforge.com/en/support/solutions/articles/9000197241
> 与 https://blog.curseforge.com/how-to-pass-moderation-review-on-curseforge-2

---

## 1. Select Game

```
Minecraft
```

## 2. Project Name

```
Transmutator Automator
```

> ⚠️ **不要用中文名**。Name 栏要求英文，且官方建议避开版本号、加载器名、
> 类别名（"Mod"）等元信息。中文名「嬗变台：大变」放在 Description 里介绍。

## 3. Summary ★

```
Auto-transmutes the Alex's Mobs Transmutation Table; stops the moment a marked item appears.
```

- 91/200 字符
- 一句话英文，说清"做什么"而非"谁做的"
- 无第一人称（官方会以此为由拒稿）

## 4. Class

```
Mods
```

## 5. Main Category

```
Utility
```

> 嬗变台是功能性设备，不是装饰/科技/冒险。
> 若列表里没有更贴切的，Utility 最合适。

## 6. Additional Categories（最多 4 个，可留空）

```
Player Automation
```

> 若 CurseForge 没有这一项就留空 —— **不要为了填满而加无关分类**，官方会退回。

## 7. Allow Comments

```
✅ 开启
```

## 8. Experimental

```
❌ 不勾选
```

> 勾了就不进 CurseForge App 生态，不上架等于白做。
> 除非你明确知道自己在做什么，否则保持关闭。

## 9. License

```
MIT
```

## 10. Logo Image

```
publish/icon-512x512.png
```

- **512×512**（1:1），满足 ≥400×400
- 不是纯色、不是通用素材
- 已在图标上加了「大变」二字，辨识度够

---

## 11. Description（Markdown 或 WYSIWYG）

复制 `publish/curseforge-description.md` 的内容。

要点（审核最看重的部分）：
- 开头就说清**核心机制**（一直嬗变直到出现目标）
- 解释**为什么不只是"只点目标"**（权重公式），这是与其他自动化模组的区别
- 有**功能特性列表**
- 有**前置要求**
- 有**详细用法步骤**
- 注明**客户端模组**，需要哪些依赖

## 12. Additional Images（Mod 不是必需，但建议传）

```
publish/cover-240x150.png   → 页面预览图
```

> 官方文档说 texture pack / sims 必传，mod 非必需。
> 但传一张预览图能明显提升点击率。

## 13. Files 页面上传

| 字段 | 值 |
|---|---|
| Upload file | `transmutator_automator-0.1.0.jar` |
| Display Name | `Transmutator Automator 0.1.0` |
| Release Type | **Release**（会同步到 CurseForge App） |
| Release Options | 审核通过后立即公开 |

> ⚠️ 上传 jar 会**立即送审**，之后改 Summary/Description 不需要重新审，
> 但改文件名/版本号会当作新文件再次送审。**先确认 Summary 和图标再传。**

---

## 审核要点自查

- [ ] Summary 是英文、一句、不含第一人称
- [ ] Description 是英文、语法正确、充分说明功能
- [ ] Logo 是 1:1 且 ≥400×400
- [ ] 未勾选 Experimental
- [ ] 分类贴切（Utility）
- [ ] 描述里没有夸大或无法证明的功能
- [ ] 没有"服务器不会崩"这类绝对化承诺

---

## 常见退稿原因（对照官方 Moderation Policies）

| 原因 | 本项目是否涉及 |
|---|---|
| Summary 含第一人称或玩笑 | ✅ 已避免 |
| Description 语法错误 / 描述不足 | ✅ 用完整英文 |
| 分类不相关 | ✅ Utility |
| Logo 用他人素材 | ⚠️ **图标基于 Alex's Mobs 的孖变台渲染图改的** |
| 功能夸大 | ✅ 只写已实现的行为 |
| 未声明依赖 | ✅ 明确列出 Alex's Mobs |

### ⚠️ 唯一风险点：图标版权

图标是**从 Alex's Mobs 的物品渲染图改的**（加了「大变」二字），
官方明确写了 "Do not use someone else's assets without permission"。

Alex's Mobs 的授权是 **MIT**（其 GitHub 仓库 LICENSE），
MIT 允许使用与修改，但需保留版权声明。

**建议做法**：
1. 在 Description 里加一行致谢与许可声明
2. 更稳妥的做法：**自己画一个不复用原图素材的图标**，
   或者只用文字排版、不含 Alex's Mobs 的渲染图

我已在 Description 草稿里加了这行：
`The icon is derived from Alex's Mobs' transmutation table render, used under MIT.`
