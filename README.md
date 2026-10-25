# GTNH Easy Touch

给 GTNH 2.9 / Minecraft 1.7.10 Forge 提供客户端触屏操作优化，主要用于 UU 远程。无需服务端安装。

**0.4.12 默认将作物加入 smart，覆盖潘马斯全部田间作物、果实和可采收树干，CropsNH／IC2 作物架、Natura 作物和浆果，以及原版作物类型。已有的手动允许/禁用规则继续优先生效。适配 GTNH 2.9.0 RC2 的 NEI 2.8.155-GTNH。**

## 安装与使用

1. 将 `build/libs/gtnh-easy-touch-0.4.12.jar` 放入 GTNH 客户端的 `mods/`，并移除旧版 Easy Touch jar。不要安装 `-sources.jar` 或 `-dev.jar`。
2. 保持 Minecraft 的“攻击/破坏”为鼠标左键、“使用物品/放置”为鼠标右键。UU 的两个按钮分别发送鼠标左键和右键；长按需持续保持按下状态，不要配置成连点宏。
3. 在“选项 → 控制 → GTNH 触屏优化”中，为“切换自适应左右键”设置一个空闲按键，再将 UU 的第三个按钮映射为该键。默认不占用快捷键。
4. 如需单键执行 Shift+T，在同一分类为“打开 NEI 合成树（等效 Shift+T）”绑定一个空闲键盘键，也可将 UU 的按钮映射到它。默认未绑定，设置由 Minecraft 控制菜单保存。
5. 为“切换指向目标的 smart 匹配”绑定一个空闲键或鼠标侧键。在世界里对准方块或实体按一下，切换同类目标的匹配开关；默认未绑定。

智能模式默认开启。切换状态保存到 `config/gtnheasytouch.cfg`；关闭后恢复原版操作。背包、机器、NEI、聊天等 GUI 内始终保持正常鼠标语义。修改攻击/使用的鼠标绑定后，模组停止接管。

玩家处于潜行状态（默认按住 Shift）时，新开始的鼠标按压使用原版映射：左键攻击/挖掘，右键使用/放置，即使目标在白名单中也不交换；准星旁的“交互”提示同时隐藏。统一依据 `player.isSneaking()` 判断。已经开始的长按仍保持原映射，需左右键全部松开后才按新的潜行状态判断。

## 指向目标切换 smart 匹配

“切换指向目标的 smart 匹配”按当前匹配结果反转：原本会交换的目标变为不交换，原本不匹配的目标变为交换。聊天提示显示目标名称和新状态；键盘长按重复事件不会反复切换。在 GUI、失焦、死亡或未进入世界时不处理；对空气或无法识别的目标不改写规则。

作用范围为同类目标，保存于 `config/gtnheasytouch.cfg` 的 `target_overrides.allow` / `target_overrides.deny`，重启和切换存档后继续生效。手动规则优先于所有内置 matcher，不改写原来的白名单；即使床接口、GT/AE2 规则等仍然命中，手动禁用也能生效。手动编辑两份列表时如有冲突，禁用优先；退出游戏后从两份列表都删除对应条目可恢复默认规则。

方块优先按 Forge“选取方块”的物品注册名和变体识别，因此朝向、位置、物品数量、容器内容和自定义名称不改变规则。GT 设备使用其机器编号，AE2 使用指向的部件，终端不会连带切换线缆；无法返回选取物品的方块退回精确方块注册名和世界元数据。规则不按坐标保存，也不区分仅由 NBT 表示的外观差异。这里仅调用选取结果查询，不给玩家物品，也不执行方块交互。

实体按种类识别，玩家归入同一类，不按 UUID 或自定义名称区分。同种实体的手动规则也覆盖驯服等默认条件，例如关闭狼会同时影响野狼和宠物狼。没有实体注册名时使用精确实体类名。

切换匹配不改变 smart 总开关；总开关关闭时会保存规则并在提示中说明。潜行继续使用原版操作。已经开始的鼠标长按保持原映射，左右键全部松开后的下一次按压采用新规则。

## 配方树合成按钮

配置“打开 NEI 合成树（等效 Shift+T）”后，只按绑定的键即可执行对应动作：悬停物品时使用收藏配方建树；悬停 NEI 配方的爱心按钮时使用当前配方建树，即使尚未收藏该配方；悬停已有合成组标题时打开该组，不额外创建副本。按钮定位沿用当前配方组件及其滚动偏移，支持当前显示的配方变体。适用位置的操作提示同时列出新按键。

新按键独立于 NEI 的 T 绑定，在背包/容器和 NEI 界面内生效；原有 Shift+T 保留。输入在 NEI 末阶段处理，搜索框文字输入优先，按住不重复新建分组；仅打开配方树，不直接开始自动合成。请选择未被其他操作占用的键盘键。

悬停书签、右侧 NEI 物品列表、中间配方的产物/材料或背包槽位中的物品，按 **Shift+T**，即可使用该物品的收藏配方创建合成组并打开配方树。优先使用爱心手动收藏的配方，其次使用 NEI 自动收藏的配方；只有物品收藏标记、没有配方或原配方已失效时不新增合成组。匹配遵循 NEI 自己的物品、元数据、NBT 和流体规则。

此操作直接复用 NEI 原生配方收藏按钮的 Shift+T 流程：按收藏递归展开原料、遵循展开深度与候选材料设置，在当前书签页新增 TODO 合成组，然后打开它的配方树。每次按下新建一组，不复用或覆盖现有组，也不会直接执行合成。快捷键沿用 NEI 的“打开合成树”绑定（默认 T，加 Shift），无需新增按键设置；搜索框输入优先，长按键盘重复事件不新增分组，原有配方按钮和组标题上的操作继续由 NEI 处理。

鼠标悬停在配方树的目标产物（根节点）上，直接向上/向下滚轮即可增加/减少合成数量，无需配置或按 Ctrl；按住 Alt 时沿用 NEI 的调整倍率（优先使用数量框设置，否则使用物品堆叠上限，流体显示为 144）。调整遵循 NEI 原有的批次数量和上下限，树及材料统计立即刷新，合成按钮状态重新检查。悬停操作提示同步显示“滚轮”和“Alt + 滚轮”。

Ctrl + 滚轮仍由 NEI 原生处理；Shift + 滚轮保留原有的候选材料切换或横向滚动。非目标节点、画布空白处和统计面板维持原操作；开启 NEI 的 `Tree Zoom without Ctrl` 后，目标产物上的直接滚轮优先调整数量，移到空白处仍可缩放。此功能仅针对配方树，不改变外部书签面板。

从书签组打开配方树，左上角 Save Image 后依次为“合成并删除”、“仅合成”和“删除并退出”。前两个按钮需要先开启 NEI 自动合成，并从工作台或支持自动合成的容器打开配方树；“删除并退出”无需材料或开启自动合成。三个按钮始终显示像素图标：合成台加红色删除标记为“合成并删除”，合成台加蓝色书签为“仅合成”，红色垃圾桶加退出箭头为“删除并退出”。悬停会高亮并显示名称和操作说明，禁用时图标变灰。

两个合成按钮都会返回打开配方树时的容器，调用 NEI 执行当前树所属分组的合成链；数量和配方使用该分组的设置。合成数量沿用 NEI Shift+C 的补齐和批次计算，背包中已有足量成品时仍可继续合成。

“删除并退出”会在松开点击后返回打开配方树前的界面，删除当前组及其全部书签并保存，不执行合成。退出被其他模组拦截、点击松开前离开配方树或原分组被替换时，不删除书签。等待其他按钮操作、正在合成或光标持有物品时暂时禁用。

鼠标或触屏点击按钮，松开后开始处理。本模组不再注册配方树合成快捷键，NEI 自带快捷键仍由 NEI 处理。

返回容器后会先等待服务端同步槽位和鼠标持有的物品，再启动合成。等待期间手动点击容器、按键、关闭界面或失焦会取消本次启动；同步超时会提示重试。鼠标上已有物品时，请先放回背包再点击“合成”。

“合成并删除”在任务成功启动后，立即删除当前配方树对应的整个合成组书签，并保存到 NEI；“仅合成”始终保留书签。启动失败、取消或同步超时会保留书签。删除发生在任务启动时，后续因材料不足或关闭容器而中断不会自动恢复书签。任务使用独立的配方快照，删除书签不影响已启动的合成。

自动合成仍受 NEI 原有的材料、配方和容器支持范围限制；此按钮不负责启动 GT 机器加工或提交 AE2 网络合成任务。未开启自动合成、没有可执行步骤、材料不足以执行任何一步、容器不支持、光标持有物品、等待同步或正在合成时，两个按钮都会置灰，悬停显示原因。材料和容器预检共用每 250ms 刷新的结果，点击时及容器同步后重新检查。预检判断的是“至少一步可以执行”，不保证整条链能够一次完成。

接口按 [GTNH 2.9.0 RC2 官方清单](https://github.com/GTNewHorizons/DreamAssemblerXXL/blob/master/releases/manifests/2.9.0-RC-2.json) 中的版本编译。NEI 是可选依赖，无需另外安装其他辅助模组；接口不兼容时记录日志并停用这项功能。

## 默认白名单

| 分类 | 默认交换的对象 |
| --- | --- |
| 原版容器 | 箱子、陷阱箱、末影箱、漏斗、发射器、投掷器 |
| 原版功能方块 | 工作台、熔炉及燃烧中的熔炉、酿造台、附魔台、铁砧、信标、床、唱片机 |
| 床 | 原版床及其子类（包括 Et Futurum 彩色床）、Forge `isBed` 返回真的床（包括木匠床），覆盖床头/床尾和颜色变体 |
| 作物 | `BlockCrops` 及其子类（小麦、胡萝卜、马铃薯和潘马斯全部田间作物）、瓜茎、地狱疣、可可；潘马斯果实和可采收树干、CropsNH／IC2 作物架、Natura 大麦/棉花及主世界/下界浆果丛；覆盖所有生长阶段 |
| 开关与机关 | 木门、活板门、栅栏门、按钮、拉杆、中继器和比较器（包括通电状态） |
| 生物 | 村民、牛、蘑菇牛、羊、猪、鸡、马/驴/骡；狼和猫仅在已驯服时交换 |
| 载具及装饰 | 船、普通矿车、运输矿车、漏斗矿车、物品展示框 |
| Iron Chests | `IronChest:BlockIronChest` 的箱子变体 |
| 匠魂 | 合成站/半砖合成站、工具装配站/装配台、零件加工台、模具桌；工作台按元数据区分 |
| GT | 基础加工机（含蒸汽系列）、基础发电机、蒸汽锅炉、多方块控制器、输入输出仓/舱、电池缓冲箱、数字储罐及其量子储罐子类、量子箱家族 |
| AE2 | 存储/合成/样板/接口/合成诊断终端、面板型接口；ME 箱子、陨石箱、驱动器、方块型接口、存储元件工作台、IO 端口、物质聚合器、磨具、压印器/高级压印器、安全终端、空间 IO 端口 |
| 神秘时代 4 | 研究台、奥术工作台（按 TileEntity 的精确类名识别，不包含整个桌子方块） |

完整注册名、元数据和匹配逻辑按类别放在 [whitelist/](src/main/java/io/github/wdy/easytouch/whitelist/) 下的独立 class 中，review 入口见下方。这些是明确列出的默认项，不会根据“具有背包”“能响应右键”等特征自动扩展。

床在 smart 模式下左键交互、右键拆除；潜行时仍保留原版鼠标映射。新增床规则按 `BlockBed` 类型和 Forge 的 `isBed` 查询识别，不靠名称包含 `bed`，也不受颜色或注册名变化影响。已核对 RC2 所带的 [Et Futurum 2.6.60-GTNH 彩色床](https://github.com/GTNewHorizons/Et-Futurum-Requiem/blob/2.6.60-GTNH/src/main/java/ganymedes01/etfuturum/blocks/BlockDyedBed.java) 和 [Carpenter's Blocks 3.7.3-GTNH 木匠床](https://github.com/GTNewHorizons/CarpentersBlocks/blob/3.7.3-GTNH/src/main/java/com/carpentersblocks/block/BlockCarpentersBed.java)。只查询当前指向方块的床属性，不调用交互或睡眠逻辑来探测。

作物在 smart 下左键执行原来的右键交互，可直接收获原模组支持右键收获的成熟作物；右键执行原来的左键操作，潜行仍保留原版映射。幼苗、未成熟和刚收获后的作物也保持交换，避免成熟度改变下一次按压的含义。只读取方块类型，不探测交互、不修改成熟度或掉落，也不为原本没有右键收获的瓜茎、地狱疣等作物新增收获机制。

潘马斯类型已核对 RC2 的 [harvestcraft 1.3.15-GTNH 开发包](https://nexus.gtnewhorizons.com/repository/releases/com/github/GTNewHorizons/harvestcraft/1.3.15-GTNH/harvestcraft-1.3.15-GTNH-dev.jar)；其他作物类型核对了 [CropsNH 2.0.134](https://github.com/GTNewHorizons/CropsNH/blob/2.0.134/src/main/java/com/gtnewhorizon/cropsnh/blocks/BlockCropSticks.java)、[Natura 2.8.24](https://github.com/GTNewHorizons/Natura/blob/2.8.24/src/main/java/mods/natura/blocks/crops/CropBlock.java) 和 [IC2 2.2.828 开发包](https://maven.ic2.player.to/net/industrial-craft/industrialcraft-2/2.2.828-experimental/industrialcraft-2-2.2.828-experimental-dev.jar)。可选模组按已确认的类家族识别，不新增运行时依赖。

默认不包含抽屉、JABBA 储物桶、GT 管道/电缆/外壳、AE 线缆、普通建筑方块、玩家、敌对生物和未知模组生物。作物规则不泛化到所有 `IPlantable` / `IGrowable`：普通花草、树苗、甘蔗、仙人掌及瓜果方块仍按原操作处理，可用目标快捷键另行开启。食物、建筑方块、桶、弓、扳手和螺丝刀也不会因被拿在手中就触发交换。例如：拿着方块对石头仍是左键挖掘、右键放置；拿着扳手对名单中的 GT 机器则是左键使用、右键拆卸。

GT 只在 `BaseMetaTileEntity` 内检查白名单中的设备家族，排除管线宿主，并且不放行整个 `gregtech:gt.blockmachines`。AE2 使用原生 `IPartHost` 的部件选择接口，只匹配准星选中的部件，线缆或外观面板本身不触发交换。模组缺失、接口不兼容、设备未同步或部件未识别时保持原版；接口异常会在日志中记录一次。兼容接口只读取状态，不执行右键或打开 GUI 来探测目标。

一次按压开始时锁定映射，直到左右键**全部松开**才重新判断。挖掘过程中移向白名单目标不会突然交换；同时按两键仍保持相反操作。打开 GUI、失焦、死亡、切换世界或切换模式时清理输入，返回世界需先松开再按。挖掘、吃东西、放置和拉弓仍由游戏原有逻辑处理，本模组不直接发送攻击或破坏数据包。

## 配置 matcher

退出游戏后编辑 `config/gtnheasytouch.cfg`，重启生效。每个 matcher 有独立的配置分类 `matchers.<id>`，并可通过该分类中的 `B:enabled=false` 单独禁用。各 matcher 自己声明所需配置，核心代码没有固定的规则类型枚举。

| 配置分类 | 当前内置选项 |
| --- | --- |
| `matchers.vanilla_blocks` | `blocks`：方块注册名，可用 `@元数据` 限定变体 |
| `matchers.beds` | `enabled`：按床类型和 Forge 床接口识别，默认开启；无需逐个添加床注册名 |
| `matchers.crops` | `standardCrops`：匹配 `BlockCrops` 及子类、瓜茎、地狱疣和可可，默认开启；`classes`：额外作物方块类家族（包含子类），默认包含潘马斯果实/产物树干、CropsNH／IC2、Natura；`enabled=false` 关闭整个作物规则 |
| `matchers.vanilla_entities` | `entities`：实体注册名；`tamedEntities`：必须已驯服的实体 |
| `matchers.iron_chests` | `blocks`：箱子注册名 |
| `matchers.tinkers_construct` | `blocks`：工作台注册名与元数据 |
| `matchers.gregtech` | `machines`：GT 内部设备的具体类名或允许的父类家族 |
| `matchers.applied_energistics` | `blocks`：方块注册名；`parts`：被准星选中的部件精确类名 |
| `matchers.thaumcraft` | `tileEntities`：方块实体的精确类名 |

首次加载会写入默认值，之后以保存的列表为准。删除条目不会被自动补回；空列表禁用对应匹配。内置列表不支持通配符。实体 matcher 自行保证 `tamedEntities` 的驯服要求优先于 `entities`。GT 内部设备编号不同于世界方块元数据，不要用 `gregtech:gt.blockmachines@设备编号` 区分机器。

升级到 0.4.10 会自动添加默认开启的 `matchers.beds`，不改写已有名单；不需要删除旧配置。关闭该规则时，`vanilla_blocks.blocks` 中原有的 `minecraft:bed` 等显式条目仍会生效。

升级到 0.4.12 会自动添加默认开启的 `matchers.crops`，不需要清空配置。作物规则的类名单与原版类型开关分别控制；`classes` 留空仅清除额外类家族，关闭整个规则请设置 `enabled=false`。已有目标手动禁用仍优先，想恢复默认时可移除对应的手动规则。

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

未潜行且准星命中匹配目标（含手动规则）时，下方仅显示“交互”；潜行或未命中时不显示提示。提示跟随当前目标，长按期间的实际按键映射仍按一次按压锁定。`general.showHud=false` 可以关闭该提示。

升级时保留全局开关和 HUD 设置。0.2.0 的 `whitelist` 列表会自动迁移一次：方块按注册名前缀分配到各 matcher，未知模组的自定义方块归入 `vanilla_blocks.blocks`；实体、宠物、方块实体、GT 设备与 AE2 部件列表分别迁入对应 matcher。空列表、自定义条目和元数据限制都保留，已存在的新配置优先。旧分类保留作参考，迁移后不再读取。0.1.0 的启发式 `rules` 仍不执行。

## 输入功能的代码划分

[ClientInput](src/main/java/io/github/wdy/easytouch/ClientInput.java) 只负责注册、事件转发和 GUI/失焦/世界切换等共同清理时机。鼠标输入逻辑放在 [input/](src/main/java/io/github/wdy/easytouch/input/)：

| 文件 | 职责 |
| --- | --- |
| [SmartMouseInput.java](src/main/java/io/github/wdy/easytouch/input/SmartMouseInput.java) | smart 鼠标交换、模式/目标开关快捷键和准星提示 |
| [MouseGesture.java](src/main/java/io/github/wdy/easytouch/input/MouseGesture.java) | 普通鼠标按压、双键重叠和映射锁定 |
| [KeyStateMerger.java](src/main/java/io/github/wdy/easytouch/input/KeyStateMerger.java) | 攻击/使用按键的状态合并、一次点击和队列清理 |
| [MinecraftInput.java](src/main/java/io/github/wdy/easytouch/input/MinecraftInput.java) | Minecraft 环境判断和底层按键读写 |

smart 通过 `Source` 提交攻击和使用按键的映射与点击请求，由底层统一写入 Minecraft。结束接管时恢复当前物理按键状态，进入 GUI 或切换模式时由入口清理正在进行的输入和待执行点击。

[TargetIdentity](src/main/java/io/github/wdy/easytouch/matching/TargetIdentity.java) 生成稳定的目标种类标识；[TargetOverrides](src/main/java/io/github/wdy/easytouch/matching/TargetOverrides.java) 保存手动允许/禁用结果。[MatcherRegistry](src/main/java/io/github/wdy/easytouch/matching/MatcherRegistry.java) 在默认匹配之前应用覆盖规则，没有相关种类的覆盖时不调用额外的选取方块查询。GT/AE2 类型识别分别核对了 RC2 的 [BlockMachines.getDamageValue](https://github.com/GTNewHorizons/GT5-Unofficial/blob/5.09.54.205/src/main/java/gregtech/common/blocks/BlockMachines.java) 和 [BlockCableBus.getPickBlock](https://github.com/GTNewHorizons/Applied-Energistics-2-Unofficial/blob/rv3-beta-1080-GTNH/src/main/java/appeng/block/networking/BlockCableBus.java)。

[NeiCraftingTreeInput](src/main/java/io/github/wdy/easytouch/nei/NeiCraftingTreeInput.java) 仅在客户端加载 NEI 时注册，负责配方树按钮、滚轮事件、可合成状态检查和返回原容器启动 NEI 自动合成。[NeiTreeQuantityScroll](src/main/java/io/github/wdy/easytouch/nei/NeiTreeQuantityScroll.java) 复用原生画布命中、数量规则和刷新方法，使用实时鼠标坐标适配 GUI 缩放及画布缩放、平移，不模拟 Ctrl 按键。

[NeiFavoriteTreeShortcut](src/main/java/io/github/wdy/easytouch/nei/NeiFavoriteTreeShortcut.java) 在 NEI 的末阶段按键回调中读取悬停物品及收藏配方，调用原生 `GuiFavoriteButton.saveRecipeInBookmark()` 并打开新组。[NeiTreeShortcut](src/main/java/io/github/wdy/easytouch/nei/NeiTreeShortcut.java) 注册独立的单键入口，分别复用当前配方爱心按钮、已有组和物品收藏的动作；无需伪造全局 Shift/T 状态，不复制 NEI 的递归配方算法。

## 按 matcher review 和扩展

代码按规则和匹配机制分开：

- [whitelist/](src/main/java/io/github/wdy/easytouch/whitelist/) 仅包含具体规则类，各自声明默认名单、配置和条件，方便逐个 review。
- [matching/](src/main/java/io/github/wdy/easytouch/matching/) 包含接口、上下文、配置读取与迁移、注册、组合、合并索引和目标解析。

| 规则文件 | 匹配方式 |
| --- | --- |
| [VanillaBlockMatcher.java](src/main/java/io/github/wdy/easytouch/whitelist/VanillaBlockMatcher.java) | `SimpleMatcher`：原版方块注册名及元数据 |
| [BedMatcher.java](src/main/java/io/github/wdy/easytouch/whitelist/BedMatcher.java) | `BlockBed` 类型及 Forge `isBed` 查询，保留实际床头/床尾坐标；接口异常按类型记录一次并回退 |
| [CropMatcher.java](src/main/java/io/github/wdy/easytouch/whitelist/CropMatcher.java) | 原版作物类型及可配置的模组作物类家族；覆盖全部生长阶段，只检查类型，不执行交互或读取成熟度 |
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

registry 先检查手动目标规则；没有对应的强制允许/禁用时，再查合并后的简单索引，未命中时按注册顺序调用剩余的复杂 matcher。默认 matcher 中任意一个返回 `true` 就匹配，全部返回 `false` 则保持原版；单个 matcher 的 `false` 仍不能否决其他 matcher，手动禁用通过独立的优先覆盖层实现。合并会改变求值顺序并提前结束查询，matcher 只能读取状态，不应依赖调用次数、执行交互、创建 GUI 或发送操作数据包。`SimpleMatcher` 和 `AnyOfMatcher` 的 `matches()` 均为 `final`，避免子类覆盖的条件在合并时丢失。

## 构建与验证

使用 Java 25 执行 `./gradlew build`（Windows：`gradlew.bat build`）。Gradle 自动获取编译所需的 Java 8；产物使用 Java 8 字节码，构建环境不改变游戏的 Java 要求。

`./gradlew test` 覆盖长按锁定、双键重叠、各 matcher 的目标判断、宠物驯服条件、旧配置迁移与空列表持久化、GT 宿主和 AE2 部件选择。扩展测试验证新增命中面/高度条件无需修改核心代码；合并测试验证 100 个简单 matcher 在命中和未命中时均只读取一次所需的目标字段，并覆盖嵌套组合、独立禁用、后续注册和合并后的驯服优先级。可选模组接口测试使用测试替身，不代表已在完整整合包中验证兼容性。

按键层测试覆盖交换后不重复触发原生右键、退出接管时恢复当前物理按键，以及进入 GUI 时清除待执行点击和按住状态。

目标开关测试覆盖覆盖默认允许、启用未知目标、再次切换、重读配置、总开关独立、同方块不同机器/部件变体、忽略位置/朝向/数量/自定义 NBT、同类实体覆盖驯服条件、无法选取物品的回退、空命中不写入、手动列表冲突、按目标种类跳过无关查询和长按期间的映射锁定。GT/AE2 的测试使用等效选取结果，真实部件仍需整合包实测。

床规则测试覆盖原版类型和子类、各元数据及两半床、非原版基类的 Forge 床接口、名字含 bed 的非床目标、无命中/缺失方块、旧名单升级、独立禁用及异常回退。模组床使用等效接口测试替身，实际 Et Futurum 和木匠床仍需在完整客户端验证。

作物规则测试覆盖原版类型和模组子类、额外作物/果实/浆果家族、全部生长元数据、普通花草树苗及误导性注册名、空命中、旧配置升级、独立禁用、自定义及空类名单持久化、手动禁用优先级，以及匹配过程不读取成熟度或执行交互。可选模组使用类名测试替身，实际收获仍需在整合包中验证。

启动流程测试覆盖鼠标释放、同步延迟、容器替换、取消和超时，以及“仅合成”保留分组、“合成并删除”仅成功启动后删除分组、启动被拒绝或异常时保留分组；删除退出测试覆盖等待松开、单次执行、退出被拦截及取消时保留书签。Netty 管线测试验证槽位和光标同步包均已处理后才能继续，且不向服务端发送额外标记包。按钮绘制、材料预检及实际合成仍需在完整 RC2 客户端验证。

实际 GTNH / UU 环境仍需实测：

- 石头左键挖掘；箱子左键打开、右键拆除；按住过程中移向另一类目标不会交换。
- 绑定“切换指向目标的 smart 匹配”，对箱子、石头、GT 设备、AE2 面板/线缆及实体逐个切换，检查同类生效、不同变体独立、提示及重启持久化；长按键不反复切换，GUI/失焦不修改，鼠标长按松开后才采用新映射。
- 原版床、不同颜色 Et Futurum 床和不同外观木匠床的床头/床尾均显示交互并交换鼠标；潜行保留原操作，基岩及普通装饰方块不交换。
- 对小麦、不同潘马斯作物、树上果实/可采收树干、CropsNH 作物架、Natura 大麦/棉花/浆果左键执行原生右键收获；未成熟与收获后仍显示交互，右键执行原来的左键操作。检查潜行、目标手动禁用、原模组收获配置及关闭 `matchers.crops` 后的行为；普通花草树苗不交换。
- 对僵尸和玩家保持原版，对牛和村民交换；野狼不交换，已驯服的狼交换。
- GT 加工机、控制器和仓/舱交换，机器外壳和管线不交换。
- 同一格 AE 线缆上的终端面板交换，指向线缆本身不交换。
- 拿着食物、桶、建筑方块或弓对准空气/普通方块仍使用原版左右键。
- 背包/机器/NEI 的拿取、拆分、拖动，以及失焦、死亡、退出世界后的输入清理。
- 从工作台打开配方树，两个按钮均返回工作台并按分组数量合成，已有成品仍可再合成一批；控制设置中不再出现本模组的配方树合成快捷键。
- 分别悬停书签、右侧物品列表、配方产物/材料和背包槽位，Shift+T 按收藏配方新建并打开合成树；无配方、只收藏物品、失效配方不新建组。检查手动收藏优先、自动收藏回退、搜索框输入、长按不重复、改绑 NEI 打开合成树按键，以及原生收藏按钮/组标题不会额外创建第二组。
- 在控制菜单绑定“打开 NEI 合成树（等效 Shift+T）”并重启确认保存；无需 Shift 分别测试收藏物品、未收藏配方的爱心按钮、滚动后的爱心按钮、折叠/展开的合成组标题。组标题只打开原组，按住不重复新建，搜索框仍输入原字符，旧 Shift+T 仍有效。
- 在目标产物上直接滚轮增减数量，Alt 按倍率调整，Ctrl 原操作不重复执行；材料统计和按钮状态随之更新。检查多个根节点、数量下限、不同 GUI/画布缩放和平移，以及开启免 Ctrl 缩放后目标产物调整数量、空白处仍缩放。Shift 操作、统计面板滚动和外部书签面板保持原行为。
- 调整 GUI 缩放或窗口大小后，三个像素图标按钮仍依次紧接 Save Image，图标完整显示；未开启自动合成、材料完全不足或从不支持合成的容器进入时两个合成按钮置灰，补充材料后恢复。
- 工作台中按住“合成”按钮时仍留在树界面，松开后返回工作台；同步完成前不搬动物品。高延迟、点击后移动鼠标、光标已有物品及等待时关闭界面均需验证。
- 用“合成并删除”启动后，对应合成组及组内书签被删除，其他组保留；重进游戏后删除仍生效。“仅合成”、取消启动或同步超时时，原组仍保留。
- 材料不足或自动合成未开启时，“删除并退出”仍可删除当前组并返回上一界面，不启动合成；其他分组保留，重进游戏后删除仍生效。
