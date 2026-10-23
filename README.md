# GTNH Easy Touch

给 GTNH 2.9 / Minecraft 1.7.10 Forge 提供客户端触屏操作优化，主要用于 UU 远程。无需服务端安装。

**0.3.0 使用独立 matcher 实现目标白名单：只有准星命中名单中的对象才交换左右键；其他目标、空气和未识别对象均保持左键攻击/挖掘、右键使用/放置。手持物品本身不触发交换。**

## 安装与使用

1. 将 `build/libs/gtnh-easy-touch-0.3.0.jar` 放入 GTNH 客户端的 `mods/`，并移除旧版 Easy Touch jar。不要安装 `-sources.jar` 或 `-dev.jar`。
2. 保持 Minecraft 的“攻击/破坏”为鼠标左键、“使用物品/放置”为鼠标右键。UU 的两个按钮分别发送鼠标左键和右键；长按需持续保持按下状态，不要配置成连点宏。
3. 在“选项 → 控制 → GTNH 触屏优化”中，为“切换自适应左右键”设置一个空闲按键，再将 UU 的第三个按钮映射为该键。默认不占用快捷键。
4. 如需一键完成潜行交互，为同一分类中的“潜行＋原生右键（按住）”绑定另一个空闲键，让 UU 的按钮只发送这个键。点按执行一次潜行右键，按住则保持潜行和使用；松开后结束。该快捷键默认未绑定，也可在关闭智能交换时使用。

智能模式默认开启。切换状态保存到 `config/gtnheasytouch.cfg`；关闭后恢复原版操作。背包、机器、NEI、聊天等 GUI 内始终保持正常鼠标语义。修改攻击/使用的鼠标绑定后，模组停止接管。

玩家处于潜行状态（默认按住 Shift）时，新开始的鼠标按压使用原版映射：左键攻击/挖掘，右键使用/放置，即使目标在白名单中也不交换；准星旁的“交互”提示同时隐藏。统一依据 `player.isSneaking()` 判断。已经开始的长按仍保持原映射，需左右键全部松开后才按新的潜行状态判断。

组合快捷键在 MC 内先按住潜行，等玩家完成一次正常的潜行更新和同步，下一 tick 才触发原版“使用物品/放置”，通常增加约 50 ms 的准备时间。它绕过白名单交换，支持短按和长按，不需要 UU 同时发送 Shift 和右键。释放时保留用户实际按住的潜行键及原有使用操作；打开 GUI、失焦、死亡、切换世界或智能模式会清理组合状态，再次使用前需松开快捷键。快捷键通过 Minecraft 控制菜单保存，无额外配置文件项。

## 默认白名单

| 分类 | 默认交换的对象 |
| --- | --- |
| 原版容器 | 箱子、陷阱箱、末影箱、漏斗、发射器、投掷器 |
| 原版功能方块 | 工作台、熔炉及燃烧中的熔炉、酿造台、附魔台、铁砧、信标、床、唱片机 |
| 开关与机关 | 木门、活板门、栅栏门、按钮、拉杆、中继器和比较器（包括通电状态） |
| 生物 | 村民、牛、蘑菇牛、羊、猪、鸡、马/驴/骡；狼和猫仅在已驯服时交换 |
| 载具及装饰 | 船、普通矿车、运输矿车、漏斗矿车、物品展示框 |
| Iron Chests | `IronChest:BlockIronChest` 的箱子变体 |
| 匠魂 | 合成站/半砖合成站、工具装配站/装配台、零件加工台、模具桌；工作台按元数据区分 |
| GT | 基础加工机（含蒸汽系列）、基础发电机、蒸汽锅炉、多方块控制器、输入输出仓/舱、电池缓冲箱、数字储罐及其量子储罐子类、量子箱家族 |
| AE2 | 存储/合成/样板/接口/合成诊断终端、面板型接口；ME 箱子、陨石箱、驱动器、方块型接口、存储元件工作台、IO 端口、物质聚合器、磨具、压印器/高级压印器、安全终端、空间 IO 端口 |
| 神秘时代 4 | 研究台、奥术工作台（按 TileEntity 的精确类名识别，不包含整个桌子方块） |

完整注册名、元数据和匹配逻辑按类别放在 [whitelist/](src/main/java/io/github/wdy/easytouch/whitelist/) 下的独立 class 中，review 入口见下方。这些是明确列出的默认项，不会根据“具有背包”“能响应右键”等特征自动扩展。

默认不包含抽屉、JABBA 储物桶、GT 管道/电缆/外壳、AE 线缆、普通建筑方块、作物、玩家、敌对生物和未知模组生物。食物、建筑方块、桶、弓、扳手和螺丝刀也不会因被拿在手中就触发交换。例如：拿着方块对石头仍是左键挖掘、右键放置；拿着扳手对名单中的 GT 机器则是左键使用、右键拆卸。

GT 只在 `BaseMetaTileEntity` 内检查白名单中的设备家族，排除管线宿主，并且不放行整个 `gregtech:gt.blockmachines`。AE2 使用原生 `IPartHost` 的部件选择接口，只匹配准星选中的部件，线缆或外观面板本身不触发交换。模组缺失、接口不兼容、设备未同步或部件未识别时保持原版；接口异常会在日志中记录一次。兼容接口只读取状态，不执行右键或打开 GUI 来探测目标。

一次按压开始时锁定映射，直到左右键**全部松开**才重新判断。挖掘过程中移向白名单目标不会突然交换；同时按两键仍保持相反操作。打开 GUI、失焦、死亡、切换世界或切换模式时清理输入，返回世界需先松开再按。挖掘、吃东西、放置和拉弓仍由游戏原有逻辑处理，本模组不直接发送攻击或破坏数据包。

## 配置 matcher

退出游戏后编辑 `config/gtnheasytouch.cfg`，重启生效。每个 matcher 有独立的配置分类 `matchers.<id>`，并可通过该分类中的 `B:enabled=false` 单独禁用。各 matcher 自己声明所需配置，核心代码没有固定的规则类型枚举。

| 配置分类 | 当前内置选项 |
| --- | --- |
| `matchers.vanilla_blocks` | `blocks`：方块注册名，可用 `@元数据` 限定变体 |
| `matchers.vanilla_entities` | `entities`：实体注册名；`tamedEntities`：必须已驯服的实体 |
| `matchers.iron_chests` | `blocks`：箱子注册名 |
| `matchers.tinkers_construct` | `blocks`：工作台注册名与元数据 |
| `matchers.gregtech` | `machines`：GT 内部设备的具体类名或允许的父类家族 |
| `matchers.applied_energistics` | `blocks`：方块注册名；`parts`：被准星选中的部件精确类名 |
| `matchers.thaumcraft` | `tileEntities`：方块实体的精确类名 |

首次加载会写入默认值，之后以保存的列表为准。删除条目不会被自动补回；空列表禁用对应匹配。内置列表不支持通配符。实体 matcher 自行保证 `tamedEntities` 的驯服要求优先于 `entities`。GT 内部设备编号不同于世界方块元数据，不要用 `gregtech:gt.blockmachines@设备编号` 区分机器。

例如，将原版方块 matcher 的列表缩小到箱子，同时关闭 Iron Chests matcher：

```text
matchers {
    vanilla_blocks {
        B:enabled=true
        S:blocks <
            minecraft:chest
        >
    }
    iron_chests {
        B:enabled=false
    }
}
```

未潜行且准星命中白名单目标时，下方仅显示“交互”；潜行或未命中时不显示提示。提示跟随当前目标，长按期间的实际按键映射仍按一次按压锁定。`general.showHud=false` 可以关闭该提示。

升级时保留全局开关和 HUD 设置。0.2.0 的 `whitelist` 列表会自动迁移一次：方块按注册名前缀分配到各 matcher，未知模组的自定义方块归入 `vanilla_blocks.blocks`；实体、宠物、方块实体、GT 设备与 AE2 部件列表分别迁入对应 matcher。空列表、自定义条目和元数据限制都保留，已存在的新配置优先。旧分类保留作参考，迁移后不再读取。0.1.0 的启发式 `rules` 仍不执行。

## 输入功能的代码划分

[ClientInput](src/main/java/io/github/wdy/easytouch/ClientInput.java) 只负责注册、事件转发和 GUI/失焦/世界切换等共同清理时机。具体功能分别放在 [input/](src/main/java/io/github/wdy/easytouch/input/)：

| 文件 | 职责 |
| --- | --- |
| [SmartMouseInput.java](src/main/java/io/github/wdy/easytouch/input/SmartMouseInput.java) | smart 鼠标交换、模式快捷键和准星提示 |
| [MouseGesture.java](src/main/java/io/github/wdy/easytouch/input/MouseGesture.java) | 普通鼠标按压、双键重叠和映射锁定 |
| [SneakUseInput.java](src/main/java/io/github/wdy/easytouch/input/SneakUseInput.java) | 潜行＋原生右键的快捷键、输入读取和玩家同步时序 |
| [SneakUseGesture.java](src/main/java/io/github/wdy/easytouch/input/SneakUseGesture.java) | 组合键的等待、点按、长按和取消状态 |
| [KeyStateMerger.java](src/main/java/io/github/wdy/easytouch/input/KeyStateMerger.java) | 独立输入来源的按键合并、一次点击和队列清理 |
| [MinecraftInput.java](src/main/java/io/github/wdy/easytouch/input/MinecraftInput.java) | Minecraft 环境判断和底层按键读写 |

两套功能不引用对方的类或状态，各自通过独立的 `Source` 提交按键请求。smart 可以替换物理鼠标映射，组合快捷键只追加潜行和使用；共享层保证一个来源释放时，另一个来源或用户实际按住的按键仍然有效。切换 smart 模式时仍由入口统一清理正在进行的输入。

## 按 matcher review 和扩展

代码按规则和匹配机制分开：

- [whitelist/](src/main/java/io/github/wdy/easytouch/whitelist/) 仅包含具体规则类，各自声明默认名单、配置和条件，方便逐个 review。
- [matching/](src/main/java/io/github/wdy/easytouch/matching/) 包含接口、上下文、配置读取与迁移、注册、组合、合并索引和目标解析。

| 规则文件 | 匹配方式 |
| --- | --- |
| [VanillaBlockMatcher.java](src/main/java/io/github/wdy/easytouch/whitelist/VanillaBlockMatcher.java) | `SimpleMatcher`：原版方块注册名及元数据 |
| [VanillaEntityMatcher.java](src/main/java/io/github/wdy/easytouch/whitelist/VanillaEntityMatcher.java) | `AnyOfMatcher`：普通实体索引 + 宠物驯服条件；合并前先从普通索引排除需要驯服的实体 |
| [IronChestsMatcher.java](src/main/java/io/github/wdy/easytouch/whitelist/IronChestsMatcher.java) | `SimpleMatcher`：Iron Chests 箱子 |
| [TinkersConstructMatcher.java](src/main/java/io/github/wdy/easytouch/whitelist/TinkersConstructMatcher.java) | `SimpleMatcher`：匠魂工作台及允许的元数据 |
| [GregTechMatcher.java](src/main/java/io/github/wdy/easytouch/whitelist/GregTechMatcher.java) | GT 宿主、内部设备家族及接口读取 |
| [AppliedEnergisticsMatcher.java](src/main/java/io/github/wdy/easytouch/whitelist/AppliedEnergisticsMatcher.java) | `AnyOfMatcher`：AE2 方块索引 + 按命中坐标选择部件 |
| [ThaumcraftMatcher.java](src/main/java/io/github/wdy/easytouch/whitelist/ThaumcraftMatcher.java) | 方块实体的精确类型 |

只匹配方块或实体注册名时，继承 [SimpleMatcher](src/main/java/io/github/wdy/easytouch/matching/SimpleMatcher.java)，声明名单即可，不必编写 `matches()`：

```java
public final class ExampleMatcher extends SimpleMatcher {
    public ExampleMatcher(MatcherSettings settings) {
        super(builder()
            .blocks(settings.strings("blocks", "Allowed block IDs, optionally @metadata.",
                "example:chest", "example:table@2"))
            .entities(settings.strings("entities", "Allowed entity IDs.", "ExampleMob")));
    }
}
```

`blocks()`、`entities()` 均可省略、重复调用，也可直接接收注册名字符串。`SimpleMatcher.builder().blocks("example:chest").build()` 可用于组合规则。构造后名单不可变，修改 builder 不会影响已构造或注册的 matcher。

[MatcherRegistry](src/main/java/io/github/wdy/easytouch/matching/MatcherRegistry.java) 在注册时把所有启用的 `SimpleMatcher` 合并成方块、实体两组哈希集合。运行时只查询合并后的索引，最多两次方块集合查询（ID 和 ID@元数据）及一次实体集合查询，查询次数不随简单规则类的数量增长。各类仍保留独立的配置和开关；禁用规则不参与合并。无需改动现有配置文件。

需要同时声明简单规则和自定义判断时，继承 [AnyOfMatcher](src/main/java/io/github/wdy/easytouch/matching/AnyOfMatcher.java)，在构造函数中传入各个 matcher。它表示“任意一个命中即可”；注册器会递归展开组合，将简单部分纳入同一索引，仅保留复杂部分逐个判断。原版实体和 AE2 类提供了完整示例。

需要同时满足 ID 和其他条件的规则，直接实现 [TargetMatcher](src/main/java/io/github/wdy/easytouch/matching/TargetMatcher.java)。不要把其中的 ID 单独作为 `AnyOfMatcher` 的分支，否则单凭 ID 就会放行。接口只有一个必须实现的方法：

```java
public interface TargetMatcher {
    boolean matches(TargetContext context);
}
```

[TargetContext](src/main/java/io/github/wdy/easytouch/matching/TargetContext.java) 提供世界、玩家、手持物品、射线命中、方块、实体和 TileEntity，便于新增状态条件。[MatcherSettings](src/main/java/io/github/wdy/easytouch/matching/MatcherSettings.java) 提供 matcher 私有的列表、布尔、整数和文本选项。新规则不必使用名单，也可以自行判断方块状态、玩家潜行或命中面。

例如，新增“只允许从顶部操作指定方块”的 matcher：

```java
public final class TopFaceMatcher implements TargetMatcher {
    private final String blockId;

    public TopFaceMatcher(MatcherSettings settings) {
        blockId = settings.text("blockId", "example:machine", "Allowed block ID.");
    }

    @Override
    public boolean matches(TargetContext context) {
        return context.isBlock() && blockId.equals(context.blockId())
            && context.hit().sideHit == 1;
    }
}
```

在 [MatcherRegistry.builtIns()](src/main/java/io/github/wdy/easytouch/matching/MatcherRegistry.java) 的注册链中加一行：

```java
.register("top_face", TopFaceMatcher::new)
```

即可生成 `matchers.top_face` 配置并参与运行时匹配。无需修改 `TargetResolver`、`TouchConfig`、其他 matcher 或旧配置迁移代码。也可以在已有 registry 上调用 `register()` 注册规则。注册 id 必须唯一，支持小写字母、数字和下划线。

registry 先查合并后的简单索引，未命中时按注册顺序调用剩余的复杂 matcher，任意一个返回 `true` 就交换，全部返回 `false` 则保持原版。`false` 表示“不匹配”，不会否决其他 matcher；因此给一个已被内置 matcher 接受的目标增加限制时，应调整原 matcher 或禁用它。合并会改变求值顺序并提前结束查询，matcher 只能读取状态，不应依赖调用次数、执行交互、创建 GUI 或发送操作数据包。`SimpleMatcher` 和 `AnyOfMatcher` 的 `matches()` 均为 `final`，避免子类覆盖的条件在合并时丢失。

## 构建与验证

使用 Java 25 执行 `./gradlew build`（Windows：`gradlew.bat build`）。Gradle 自动获取编译所需的 Java 8；产物使用 Java 8 字节码，构建环境不改变游戏的 Java 要求。

`./gradlew test` 覆盖长按锁定、双键重叠、各 matcher 的目标判断、宠物驯服条件、旧配置迁移与空列表持久化、GT 宿主和 AE2 部件选择。扩展测试验证新增命中面/高度条件无需修改核心代码；合并测试验证 100 个简单 matcher 在命中和未命中时均只读取一次所需的目标字段，并覆盖嵌套组合、独立禁用、后续注册和合并后的驯服优先级。可选模组接口测试使用测试替身，不代表已在完整整合包中验证兼容性。

组合快捷键的状态机测试覆盖先潜行后使用、短按保留一次交互、长按与释放、潜行失效时暂停使用、取消后必须松键，以及无法进入潜行时超时清理。共享按键层另有交叉使用测试，验证两套功能的释放互不干扰、物理按键恢复、smart 映射与组合键并行，以及 GUI 边界清除待执行点击。

实际 GTNH / UU 环境仍需实测：

- 石头左键挖掘；箱子左键打开、右键拆除；按住过程中移向另一类目标不会交换。
- 对僵尸和玩家保持原版，对牛和村民交换；野狼不交换，已驯服的狼交换。
- GT 加工机、控制器和仓/舱交换，机器外壳和管线不交换。
- 同一格 AE 线缆上的终端面板交换，指向线缆本身不交换。
- 拿着食物、桶、建筑方块或弓对准空气/普通方块仍使用原版左右键。
- 背包/机器/NEI 的拿取、拆分、拖动，以及失焦、死亡、退出世界后的输入清理。
- 组合快捷键短按在容器旁潜行放置，长按连续使用，松开恢复；实际 Shift 或普通使用键仍按住时不被提前释放；打开 GUI 或失焦后不残留组合按键。
