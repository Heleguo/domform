package com.geji.domform;

import cn.lunadeer.dominion.api.DominionAPI;
import cn.lunadeer.dominion.api.dtos.DominionDTO;
import cn.lunadeer.dominion.api.dtos.MemberDTO;
import cn.lunadeer.dominion.api.dtos.PlayerDTO;
import cn.lunadeer.dominion.api.dtos.flag.PriFlag;
import cn.lunadeer.dominion.events.dominion.modify.DominionReSizeEvent;
import cn.lunadeer.dominion.events.dominion.modify.DominionSetMessageEvent;
import cn.lunadeer.dominion.providers.DominionProvider;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.form.ModalForm;
import org.geysermc.cumulus.util.FormImage;
import org.geysermc.floodgate.api.FloodgateApi;

import java.sql.SQLException;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.HashMap;
import java.util.Map;

public class FormManager {
    
    public static void openMainMenu(Player player, JavaPlugin plugin) {
        try {
            SimpleForm.Builder form = SimpleForm.builder()
                    .title("Dominion领地系统")
                    .content("欢迎使用Dominion领地系统，请选择操作：")
                    .button("创建新领地", FormImage.Type.PATH, "textures/ui/worldsIcon.png")
                    .button("我的领地列表", FormImage.Type.PATH, "textures/ui/servers.png")
                    .button("当前位置领地", FormImage.Type.PATH, "textures/ui/share_apple.png")
                    .button("领地访客权限设置", FormImage.Type.PATH, "textures/ui/icon_lock.png")
                    .button("删除领地", FormImage.Type.PATH, "textures/ui/trash.png");
            
            form.validResultHandler((response) -> {
                switch (response.clickedButtonId()) {
                    case 0:
                        openCreateLandForm(player, plugin);
                        break;
                    case 1:
                        openMyLandsForm(player, plugin);
                        break;
                    case 2:
                        openCurrentLandForm(player, plugin);
                        break;
                    case 3:
                        openPermissionForm(player, plugin);
                        break;
                    case 4:
                        openDeleteLandForm(player, plugin);
                        break;
                }
            });
            
            FloodgateApi.getInstance().sendForm(player.getUniqueId(), form.build());
        } catch (Exception e) {
            player.sendMessage("§c无法打开表单界面！请确保您是基岩版玩家且服务器配置正确。");
            plugin.getLogger().warning("表单发送失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void openCreateLandForm(Player player, JavaPlugin plugin) {
        try {
            // 先检查玩家是否达到领地上限
            List<DominionDTO> playerDominions = DominionManager.getPlayerDominions(player);
            
            CustomForm.Builder form = CustomForm.builder()
                    .title("创建新领地");

            // 在表单中添加说明文本
            form.label("您当前拥有 " + playerDominions.size() + " 块领地");
            form.input("领地名称", "例如：myland");
            
            form.validResultHandler((response) -> {
                // 跳过label，从input开始获取值
                String landName = response.asInput(1);
                
                // 调用Dominion插件API来创建领地
                player.sendMessage("§a正在尝试创建名为 " + landName + " 的领地...");
                
                CompletableFuture<DominionDTO> future = DominionManager.createDominion(player, landName, "");
                future.thenAccept(dominion -> {
                    if (dominion != null) {
                        player.sendMessage("§a成功创建领地：" + landName);
                    } else {
                        player.sendMessage("§c创建领地失败，请重试。");
                    }
                }).exceptionally(throwable -> {
                    player.sendMessage("§c创建领地时发生错误：" + throwable.getMessage());
                    return null;
                });
                
                // 返回主菜单
                openMainMenu(player, plugin);
            });
            
            FloodgateApi.getInstance().sendForm(player.getUniqueId(), form.build());
        } catch (Exception e) {
            player.sendMessage("§c无法打开表单界面！");
            plugin.getLogger().warning("表单发送失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void openMyLandsForm(Player player, JavaPlugin plugin) {
        try {
            SimpleForm.Builder form = SimpleForm.builder()
                    .title("我的领地")
                    .content("以下是您拥有的所有领地：");
                    
            // 从Dominion插件获取玩家的领地列表
            List<DominionDTO> dominions = DominionManager.getPlayerDominions(player);
            
            if (dominions.isEmpty()) {
                form.content("您目前没有任何领地。");
            } else {
                for (DominionDTO dominion : dominions) {
                    form.button(dominion.getName(), FormImage.Type.PATH, "textures/ui/store_home_icon.png");
                }
            }
            
            form.button("§a<< 返回主菜单", FormImage.Type.PATH, "textures/ui/worldsIcon.png");
            
            form.validResultHandler((response) -> {
                int buttonId = response.clickedButtonId();
                
                // 如果点击的是返回按钮
                if (buttonId == dominions.size()) {
                    openMainMenu(player, plugin);
                    return;
                }
                
                // 处理领地选择
                DominionDTO selectedDominion = dominions.get(buttonId);
                player.sendMessage("§a选择了领地：" + selectedDominion.getName());
                openLandManagementForm(player, selectedDominion, plugin);
            });
            
            FloodgateApi.getInstance().sendForm(player.getUniqueId(), form.build());
        } catch (Exception e) {
            player.sendMessage("§c无法打开表单界面！");
            plugin.getLogger().warning("表单发送失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void openCurrentLandForm(Player player, JavaPlugin plugin) {
        DominionDTO currentDominion = DominionManager.getPlayerCurrentDominion(player);
        
        if (currentDominion == null) {
            try {
                SimpleForm.Builder form = SimpleForm.builder()
                        .title("当前位置")
                        .content("您当前不在任何领地内。")
                        .button("§a<< 返回主菜单", FormImage.Type.PATH, "textures/ui/worldsIcon.png");
                
                form.validResultHandler((response) -> {
                    openMainMenu(player, plugin);
                });
                
                FloodgateApi.getInstance().sendForm(player.getUniqueId(), form.build());
            } catch (Exception e) {
                player.sendMessage("§c无法打开表单界面！");
                plugin.getLogger().warning("表单发送失败: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            // 检查玩家是否为领地主人
            if (!isPlayerDominionOwner(player, currentDominion)) {
                // 如果不是领地主人，显示只读信息
                try {
                    SimpleForm.Builder form = SimpleForm.builder()
                            .title("当前位置领地")
                            .content("领地名称: " + currentDominion.getName() + "\n您不是该领地的主人，无法进行管理操作。")
                            .button("§a<< 返回主菜单", FormImage.Type.PATH, "textures/ui/worldsIcon.png");
                    
                    form.validResultHandler((response) -> {
                        openMainMenu(player, plugin);
                    });
                    
                    FloodgateApi.getInstance().sendForm(player.getUniqueId(), form.build());
                    return;
                } catch (Exception e) {
                    player.sendMessage("§c无法打开表单界面！");
                    plugin.getLogger().warning("表单发送失败: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            openLandManagementForm(player, currentDominion, plugin);
        }
    }
    
    private static void openLandManagementForm(Player player, DominionDTO dominion, JavaPlugin plugin) {
        try {
            SimpleForm.Builder form = SimpleForm.builder().title("管理领地：" + dominion.getName()).content("领地名称：" + dominion.getName());
            form.button("编辑成员权限", FormImage.Type.PATH, "textures/ui/structure_block.png");
            form.button("添加成员", FormImage.Type.PATH, "textures/items/name_tag.png");
            form.button("移除成员", FormImage.Type.PATH, "textures/ui/send_icon.png");
            form.button("设置进入/离开消息", FormImage.Type.PATH, "textures/ui/store_home_icon.png");
            form.button("传送", FormImage.Type.PATH, "textures/items/ender_pearl.png");
            form.button("转让领地", FormImage.Type.PATH, "textures/ui/share_microsoft.png");
            form.button("调整领地大小", FormImage.Type.PATH, "textures/ui/share_google.png");
            form.button("§a<< 返回主菜单", FormImage.Type.PATH, "textures/ui/worldsIcon.png");
            form.validResultHandler((response) -> {
                switch (response.clickedButtonId()) {
                    case 0:
                        openMemberPermissionForm(player, dominion, plugin);
                        break;
                    case 1:
                        openAddMemberForm(player, dominion, plugin);
                        break;
                    case 2:
                        openRemoveMemberForm(player, dominion, plugin);
                        break;
                    case 3:
                        openSetMessageForm(player, dominion, plugin);
                        break;
                    case 4:
                        openTeleportMenu(player, dominion, plugin);
                        break;
                    case 5:
                        transferDominion(player,dominion);
                        break;
                    case 6:
                        setDominionSize(player,dominion);
                        break;
                    case 7:
                        openMainMenu(player, plugin);
                        break;
                }
            });
            
            FloodgateApi.getInstance().sendForm(player.getUniqueId(), form.build());
        } catch (Exception e) {
            player.sendMessage("§c无法打开表单界面！");
            plugin.getLogger().warning("表单发送失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 打开成员权限管理表单
     * @param player 玩家
     * @param dominion 领地
     * @param plugin 插件实例
     */
    private static void openMemberPermissionForm(Player player, DominionDTO dominion, JavaPlugin plugin) {
        try {
            List<MemberDTO> members = DominionManager.getMembers(dominion);
            
            if (members.isEmpty()) {
                player.sendMessage("§c该领地没有任何成员可以管理权限。");
                openLandManagementForm(player, dominion, plugin);
                return;
            }
            
            SimpleForm.Builder form = SimpleForm.builder()
                    .title("成员权限管理：" + dominion.getName())
                    .content("请选择要管理权限的成员：");
            
            // 添加成员列表
            for (int i = 0; i < members.size(); i++) {
                MemberDTO member = members.get(i);
                form.button("成员 " + (i + 1), FormImage.Type.PATH, "textures/ui/icon_steve.png");
            }
            
            form.button("§a<< 返回上级菜单", FormImage.Type.PATH, "textures/ui/worldsIcon.png");
            
            form.validResultHandler((response) -> {
                int buttonId = response.clickedButtonId();
                
                // 如果点击的是返回按钮
                if (buttonId == members.size()) {
                    openLandManagementForm(player, dominion, plugin);
                    return;
                }
                
                // 处理成员选择
                MemberDTO selectedMember = members.get(buttonId);
                openSpecificMemberPermissionForm(player, dominion, selectedMember, plugin);
            });
            
            FloodgateApi.getInstance().sendForm(player.getUniqueId(), form.build());
        } catch (Exception e) {
            player.sendMessage("§c无法打开成员权限管理界面！");
            plugin.getLogger().warning("成员权限管理界面发送失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 打开特定成员的权限管理表单
     * @param player 玩家
     * @param dominion 领地
     * @param member 成员
     * @param plugin 插件实例
     */
    private static void openSpecificMemberPermissionForm(Player player, DominionDTO dominion, MemberDTO member, JavaPlugin plugin) {
        try {
            // 获取所有可用的权限标志
            List<PriFlag> allFlags = DominionManager.getAllPriFlags();
            
            CustomForm.Builder form = CustomForm.builder()
                    .title("管理成员权限：" + dominion.getName())
                    .label("成员: " + member.getPlayer().getLastKnownName());
            
            // 存储当前权限值以便比较
            boolean[] currentValues = new boolean[allFlags.size()];
            
            // 为每个权限添加切换开关，并获取当前值
            for (int i = 0; i < allFlags.size(); i++) {
                PriFlag flag = allFlags.get(i);
                // 获取成员当前的权限状态
                boolean currentValue = member.getFlagValue(flag);
                currentValues[i] = currentValue;
                form.toggle(flag.getDisplayName(), currentValue);
            }
            
            form.validResultHandler((response) -> {
                // 处理权限设置
                List<CompletableFuture<Void>> futures = new ArrayList<>();
                
                for (int i = 0; i < allFlags.size(); i++) {
                    PriFlag flag = allFlags.get(i);
                    boolean oldValue = currentValues[i];
                    boolean newValue = response.asToggle(i + 1); // +1 because of the label
                    
                    // 只有当权限值发生变化时才更新
                    if (oldValue != newValue) {
                        // 调用API设置成员权限
                        CompletableFuture<MemberDTO> future = DominionManager.setMemberFlag(dominion, member, flag, newValue);
                        futures.add(future.thenRun(() -> {
                            // 空操作，只是等待完成
                        }));
                    }
                }
                
                // 如果没有任何更改，则直接返回
                if (futures.isEmpty()) {
                    player.sendMessage("§a没有权限需要更新");
                    openMainMenu(player, plugin);
                    return;
                }
                
                // 等待所有权限更新完成
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .thenRun(() -> {
                        player.sendMessage("§a已更新成员的权限设置");
                        openMemberPermissionForm(player, dominion, plugin);
                    })
                    .exceptionally(throwable -> {
                        player.sendMessage("§c更新成员权限时发生错误：" + throwable.getMessage());
                        openMemberPermissionForm(player, dominion, plugin);
                        return null;
                    });
            });
            
            FloodgateApi.getInstance().sendForm(player.getUniqueId(), form.build());
        } catch (Exception e) {
            player.sendMessage("§c无法打开成员权限设置界面！");
            plugin.getLogger().warning("成员权限设置界面发送失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void transferDominion(Player player,DominionDTO dominionDTO){
        CustomForm.Builder form = CustomForm.builder().title("转让领地");
        form.input("§c这是一个十分危险的操作！\n§f玩家名:");
        form.validResultHandler(r->{
            String name = r.asInput(0);
            PlayerDTO target = DominionManager.getDominionAPI().getPlayer(name);
            if (target != null) {
                DominionProvider.getInstance().transferDominion(player,dominionDTO,target);
            } else {
                player.sendMessage("§c玩家不存在！");
            }
        });
        FloodgateApi.getInstance().sendForm(player.getUniqueId(), form.build());
    }

    private static void setDominionSize(Player player, DominionDTO dominionDTO) {
        CustomForm.Builder form = CustomForm.builder().title("调整领地大小");
        form.label("§f请输入领地各方向的新大小:");
        form.input("§6北(z-)的大小", "请输入新的大小", "");
        form.input("§6南(z+)的大小", "请输入新的大小", "");
        form.input("§6西(x-)的大小", "请输入新的大小", "");
        form.input("§6东(x+)的大小", "请输入新的大小", "");
        form.input("§6上(y+)的大小", "请输入新的大小", "");
        form.input("§6下(y-)的大小", "请输入新的大小", "");
        form.validResultHandler(r -> {
            try {
                // 解析输入值，如果为空则使用默认值0
                int northSize = parseOrDefault(r.asInput(1), 0);
                int southSize = parseOrDefault(r.asInput(2), 0);
                int westSize = parseOrDefault(r.asInput(3), 0);
                int eastSize = parseOrDefault(r.asInput(4), 0);
                int upSize = parseOrDefault(r.asInput(5), 0);
                int downSize = parseOrDefault(r.asInput(6), 0);
                
                // 根据输入值的正负确定扩展或收缩类型
                CompletableFuture<DominionDTO> future = resizeDominionDirection(player, dominionDTO, 
                    northSize, DominionReSizeEvent.DIRECTION.NORTH);
                future
                        .thenCompose(resizedDominion ->
                                // 继续调整南方
                                resizeDominionDirection(player, resizedDominion, southSize, DominionReSizeEvent.DIRECTION.SOUTH)
                        )
                        .thenCompose(resizedDominion ->
                                // 继续调整西方
                                resizeDominionDirection(player, resizedDominion, westSize, DominionReSizeEvent.DIRECTION.WEST)
                        )
                        .thenCompose(resizedDominion ->
                                // 继续调整东方
                                resizeDominionDirection(player, resizedDominion, eastSize, DominionReSizeEvent.DIRECTION.EAST)
                        )
                        .thenCompose(resizedDominion ->
                                // 继续调整上方
                                resizeDominionDirection(player, resizedDominion, upSize, DominionReSizeEvent.DIRECTION.UP)
                        )
                        .thenCompose(resizedDominion ->
                                // 继续调整下方
                                resizeDominionDirection(player, resizedDominion, downSize, DominionReSizeEvent.DIRECTION.DOWN)
                        )
                        .thenAccept(resizedDominion -> {
                            player.sendMessage("领地大小已成功更新！");
                        })
                        .exceptionally(ex -> {
                            player.sendMessage("调整领地大小时发生错误: " + ex.getMessage());
                            ex.printStackTrace();
                            return null;
                        });
            } catch (NumberFormatException e) {
                player.sendMessage("§c输入格式错误，请输入有效的数字！");
            }
        });

        // 发送表单到玩家的游戏客户端
        FloodgateApi.getInstance().sendForm(player.getUniqueId(), form.build());
    }
    
    /**
     * 根据指定方向和大小调整领地
     * @param player 玩家
     * @param dominion 领地
     * @param size 调整大小（正数表示扩展，负数表示收缩）
     * @param direction 调整方向
     * @return 调整后的领地
     */
    private static CompletableFuture<DominionDTO> resizeDominionDirection(Player player, DominionDTO dominion, 
            int size, DominionReSizeEvent.DIRECTION direction) {
        if (size == 0) {
            // 如果大小为0，则不进行调整
            return CompletableFuture.completedFuture(dominion);
        }
        
        // Dominion API只支持EXPAND类型，负数大小表示收缩
        return DominionAPI.getDominionProvider().resizeDominion(player, dominion, DominionReSizeEvent.TYPE.EXPAND, direction, size);
    }
    
    /**
     * 解析字符串为整数，如果解析失败则返回默认值
     * @param input 输入字符串
     * @param defaultValue 默认值
     * @return 解析后的整数或默认值
     */
    private static int parseOrDefault(String input, int defaultValue) {
        if (input == null || input.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(input.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    private static void openPermissionForm(Player player, JavaPlugin plugin) {
        try {
            List<DominionDTO> playerDominions = DominionManager.getPlayerDominions(player);
            
            if (playerDominions.isEmpty()) {
                player.sendMessage("§c您没有任何领地可以设置权限。");
                openMainMenu(player, plugin);
                return;
            }
            
            SimpleForm.Builder form = SimpleForm.builder()
                    .title("权限设置")
                    .content("请选择要设置权限的领地：");
            
            for (DominionDTO dominion : playerDominions) {
                form.button(dominion.getName(), FormImage.Type.PATH, "textures/ui/icon_lock.png");
            }
            
            form.button("§a<< 返回主菜单", FormImage.Type.PATH, "textures/ui/worldsIcon.png");
            
            form.validResultHandler((response) -> {
                int buttonId = response.clickedButtonId();
                
                // 如果点击的是返回按钮
                if (buttonId == playerDominions.size()) {
                    openMainMenu(player, plugin);
                    return;
                }
                
                // 处理领地选择
                DominionDTO selectedDominion = playerDominions.get(buttonId);
                openLandPermissionForm(player, selectedDominion, plugin);
            });
            
            FloodgateApi.getInstance().sendForm(player.getUniqueId(), form.build());
        } catch (Exception e) {
            player.sendMessage("§c无法打开表单界面！");
            plugin.getLogger().warning("表单发送失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void openLandPermissionForm(Player player, DominionDTO dominion, JavaPlugin plugin) {
        // 检查玩家是否为领地主人
        if (!isPlayerDominionOwner(player, dominion)) {
            // 如果不是领地主人，显示只读界面
            showReadOnlyGuestPermissionForm(player, dominion, plugin);
            return;
        }
        
        try {
            // 获取所有可用的权限标志
            List<PriFlag> allFlags = DominionManager.getAllPriFlags();
            
            CustomForm.Builder form = CustomForm.builder()
                    .title("访客权限设置：" + dominion.getName());
            
            // 存储当前权限值以便比较
            boolean[] currentValues = new boolean[allFlags.size()];
            
            // 为每个权限添加切换开关，并获取当前值
            for (int i = 0; i < allFlags.size(); i++) {
                PriFlag flag = allFlags.get(i);
                // 获取访客当前的权限状态
                boolean currentValue = dominion.getGuestFlagValue(flag);
                currentValues[i] = currentValue;
                form.toggle(flag.getDisplayName(), currentValue);
            }
            
            form.validResultHandler((response) -> {
                // 处理权限设置
                List<CompletableFuture<Void>> futures = new ArrayList<>();
                
                for (int i = 0; i < allFlags.size(); i++) {
                    PriFlag flag = allFlags.get(i);
                    boolean oldValue = currentValues[i];
                    boolean newValue = response.asToggle(i);
                    
                    // 只有当权限值发生变化时才更新
                    if (oldValue != newValue) {
                        CompletableFuture<DominionDTO> future = DominionManager.setDominionGuestFlag(dominion, flag, newValue);
                        futures.add(future.thenRun(() -> {
                            // 空操作，只是等待完成
                        }));
                    }
                }
                
                // 如果没有任何更改，则直接返回
                if (futures.isEmpty()) {
                    player.sendMessage("§a没有权限需要更新");
                    openMainMenu(player, plugin);
                    return;
                }
                
                // 等待所有权限更新完成
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .thenRun(() -> {
                        player.sendMessage("§a已更新领地 " + dominion.getName() + " 的访客权限设置");
                        openMainMenu(player, plugin);
                    })
                    .exceptionally(throwable -> {
                        player.sendMessage("§c更新访客权限时发生错误：" + throwable.getMessage());
                        openMainMenu(player, plugin);
                        return null;
                    });
            });
            
            FloodgateApi.getInstance().sendForm(player.getUniqueId(), form.build());
        } catch (Exception e) {
            player.sendMessage("§c无法打开访客权限设置界面！");
            plugin.getLogger().warning("访客权限设置界面发送失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 检查玩家是否为领地的主人
     * 
     * @param player 玩家
     * @param dominion 领地
     * @return 是否为领地主人
     */
    private static boolean isPlayerDominionOwner(Player player, DominionDTO dominion) {
        // 获取玩家拥有的领地
        List<DominionDTO> playerDominions = DominionManager.getPlayerDominions(player);
        
        // 检查当前领地是否在玩家拥有的领地列表中
        for (DominionDTO playerDominion : playerDominions) {
            if (playerDominion.getName().equals(dominion.getName())) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 显示只读访客权限界面（供非领地主人查看）
     * 
     * @param player 玩家
     * @param dominion 领地
     * @param plugin 插件实例
     */
    private static void showReadOnlyGuestPermissionForm(Player player, DominionDTO dominion, JavaPlugin plugin) {
        try {
            // 获取所有可用的权限标志
            List<PriFlag> allFlags = DominionManager.getAllPriFlags();
            
            CustomForm.Builder form = CustomForm.builder()
                    .title("访客权限查看：" + dominion.getName() + " (只读模式)");
            
            // 添加说明文本
            form.label("您不是该领地的主人，只能查看访客权限设置");
            
            // 为每个权限添加只读标签（显示当前值）
            for (PriFlag flag : allFlags) {
                boolean currentValue = dominion.getGuestFlagValue(flag);
                form.label(flag.getDisplayName() + ": " + (currentValue ? "开启" : "关闭"));
            }
            
            form.validResultHandler((response) -> {
                // 返回到主菜单
                openMainMenu(player, plugin);
            });
            
            FloodgateApi.getInstance().sendForm(player.getUniqueId(), form.build());
        } catch (Exception e) {
            player.sendMessage("§c无法打开访客权限查看界面！");
            plugin.getLogger().warning("访客权限查看界面发送失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void openAddMemberForm(Player player, DominionDTO dominion, JavaPlugin plugin) {
        try {
            CustomForm.Builder form = CustomForm.builder()
                    .title("添加成员：" + dominion.getName())
                    .input("玩家名称", "输入玩家ID");
            
            form.validResultHandler((response) -> {
                String playerName = response.asInput(0);
                
                // 添加成员到领地
                CompletableFuture<MemberDTO> future = DominionManager.addMember(dominion, null, playerName);
                future.thenAccept(member -> {
                    if (member != null) {
                        player.sendMessage("§a已将玩家 " + playerName + " 添加到领地 " + dominion.getName());
                    } else {
                        player.sendMessage("§c添加玩家 " + playerName + " 到领地 " + dominion.getName() + " 失败");
                    }
                }).exceptionally(throwable -> {
                    player.sendMessage("§c添加成员时发生错误：" + throwable.getMessage());
                    return null;
                });
                
                openLandManagementForm(player, dominion, plugin);
            });
            
            FloodgateApi.getInstance().sendForm(player.getUniqueId(), form.build());
        } catch (Exception e) {
            player.sendMessage("§c无法打开表单界面！");
            plugin.getLogger().warning("表单发送失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void openRemoveMemberForm(Player player, DominionDTO dominion, JavaPlugin plugin) {
        try {
            CustomForm.Builder form = CustomForm.builder()
                    .title("移除成员：" + dominion.getName())
                    .dropdown("选择成员", getMemberNames(dominion));
            
            form.validResultHandler((response) -> {
                int memberIndex = response.asDropdown(0);
                List<MemberDTO> members = DominionManager.getMembers(dominion);
                
                if (memberIndex < members.size()) {
                    MemberDTO member = members.get(memberIndex);
                    
                    // 从领地移除成员
                    CompletableFuture<MemberDTO> future = DominionManager.removeMember(dominion, member);
                    future.thenAccept(removedMember -> {
                        if (removedMember != null) {
                            player.sendMessage("§a已将玩家从领地 " + dominion.getName() + " 移除");
                        } else {
                            player.sendMessage("§c从领地 " + dominion.getName() + " 移除玩家失败");
                        }
                    }).exceptionally(throwable -> {
                        player.sendMessage("§c移除成员时发生错误：" + throwable.getMessage());
                        return null;
                    });
                }
                
                openLandManagementForm(player, dominion, plugin);
            });
            
            FloodgateApi.getInstance().sendForm(player.getUniqueId(), form.build());
        } catch (Exception e) {
            player.sendMessage("§c无法打开表单界面！");
            plugin.getLogger().warning("表单发送失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void openSetMessageForm(Player player, DominionDTO dominion, JavaPlugin plugin) {
        try {
            CustomForm.Builder form = CustomForm.builder()
                    .title("设置消息：" + dominion.getName())
                    .input("进入消息", "例如：欢迎来到我的领地")
                    .input("离开消息", "例如：感谢访问，再见");
            
            form.validResultHandler((response) -> {
                String enterMessage = response.asInput(0);
                String leaveMessage = response.asInput(1);
                
                // 调用API设置进入消息
                CompletableFuture<DominionDTO> enterFuture = DominionManager.setDominionMessage(dominion, DominionSetMessageEvent.TYPE.ENTER, enterMessage);
                enterFuture.thenAccept(result -> {
                    if (result == null) {
                        player.sendMessage("§c设置进入消息失败");
                    }
                }).exceptionally(throwable -> {
                    player.sendMessage("§c设置进入消息时发生错误：" + throwable.getMessage());
                    return null;
                });
                
                // 调用API设置离开消息
                CompletableFuture<DominionDTO> leaveFuture = DominionManager.setDominionMessage(dominion, DominionSetMessageEvent.TYPE.LEAVE, leaveMessage);
                leaveFuture.thenAccept(result -> {
                    if (result == null) {
                        player.sendMessage("§c设置离开消息失败");
                    }
                }).exceptionally(throwable -> {
                    player.sendMessage("§c设置离开消息时发生错误：" + throwable.getMessage());
                    return null;
                });
                
                player.sendMessage("§a已设置领地消息：");
                player.sendMessage("§a进入消息：" + enterMessage);
                player.sendMessage("§a离开消息：" + leaveMessage);
                
                openLandManagementForm(player, dominion, plugin);
            });
            
            FloodgateApi.getInstance().sendForm(player.getUniqueId(), form.build());
        } catch (Exception e) {
            player.sendMessage("§c无法打开表单界面！");
            plugin.getLogger().warning("表单发送失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 打开传送菜单
     * @param player 玩家
     * @param dominion 领地
     * @param plugin 插件实例
     */
    private static void openTeleportMenu(Player player, DominionDTO dominion, JavaPlugin plugin) {
        try {
            SimpleForm.Builder form = SimpleForm.builder()
                    .title("传送设置：" + dominion.getName())
                    .content("请选择传送操作：")
                    .button("传送到此领地")
                    .button("在当前位置设置传送点");
            
            form.validResultHandler((response) -> {
                switch (response.clickedButtonId()) {
                    case 0:
                        // 直接传送玩家到领地
                        if (dominion.getTpLocation() != null) {
                            player.teleport(dominion.getTpLocation());
                            player.sendMessage("§a已传送到领地 " + dominion.getName());
                        } else {
                            player.sendMessage("§c该领地尚未设置传送点！");
                        }
                        openLandManagementForm(player, dominion, plugin);
                        break;
                    case 1:
                        // 在当前位置设置传送点
                        // 检查玩家是否在领地内
                        DominionDTO currentDominion = DominionManager.getPlayerCurrentDominion(player);
                        if (currentDominion == null || !currentDominion.getName().equals(dominion.getName())) {
                            player.sendMessage("§c设置失败：您必须在领地内才能设置传送点！");
                        } else {
                            try {
                                dominion.setTpLocation(player.getLocation());
                                player.sendMessage("§a传送点设置成功！");
                            } catch (SQLException e) {
                                player.sendMessage("§c传送点设置失败，错误信息: " + e.getMessage());
                                e.printStackTrace();
                            }
                        }
                        openLandManagementForm(player, dominion, plugin);
                        break;
                }
            });
            
            FloodgateApi.getInstance().sendForm(player.getUniqueId(), form.build());
        } catch (Exception e) {
            player.sendMessage("§c无法打开传送菜单！");
            plugin.getLogger().warning("传送菜单发送失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static String[] getMemberNames(DominionDTO dominion) {
        List<MemberDTO> members = DominionManager.getMembers(dominion);
        String[] memberNames = new String[members.size()];
        for (int i = 0; i < members.size(); i++) {
            // 由于API限制，使用占位符名称
            memberNames[i] = "成员" + (i + 1);
        }
        return memberNames;
    }
    
    private static String[] getDominionNames(Player player) {
        List<DominionDTO> dominions = DominionManager.getPlayerDominions(player);
        String[] dominionNames = new String[dominions.size()];
        for (int i = 0; i < dominions.size(); i++) {
            dominionNames[i] = dominions.get(i).getName();
        }
        return dominionNames;
    }
    
    private static void openDeleteLandForm(Player player, JavaPlugin plugin) {
        try {
            CustomForm.Builder form = CustomForm.builder()
                    .title("删除领地")
                    .dropdown("选择领地", getDominionNames(player));
                
            form.validResultHandler((response) -> {
                String[] dominionNames = getDominionNames(player);
                if (dominionNames.length == 0) {
                    player.sendMessage("§c您没有任何领地可以删除。");
                    openMainMenu(player, plugin);
                    return;
                }
                
                int dominionIndex = response.asDropdown(0);
                List<DominionDTO> dominions = DominionManager.getPlayerDominions(player);
                DominionDTO dominion = dominions.get(dominionIndex);
                
                try {
                    ModalForm.Builder confirmForm = ModalForm.builder()
                            .title("确认删除")
                            .content("您确定要删除领地 " + dominion.getName() + " 吗？此操作无法撤销！")
                            .button1("确认删除")
                            .button2("取消");
                    
                    confirmForm.validResultHandler((confirmResponse) -> {
                        if (confirmResponse.clickedButtonId() == 0) {
                            CompletableFuture<DominionDTO> future = DominionManager.deleteDominion(dominion);
                            future.thenAccept(deletedDominion -> {
                                if (deletedDominion != null) {
                                    player.sendMessage("§a领地 " + dominion.getName() + " 已被删除");
                                } else {
                                    player.sendMessage("§c删除领地失败");
                                }
                            }).exceptionally(throwable -> {
                                player.sendMessage("§c删除领地时发生错误：" + throwable.getMessage());
                                return null;
                            });
                        } else {
                            player.sendMessage("§e操作已取消");
                        }
                        openMainMenu(player, plugin);
                    });
                    
                    FloodgateApi.getInstance().sendForm(player.getUniqueId(), confirmForm.build());
                } catch (Exception e) {
                    player.sendMessage("§c无法打开确认表单界面！");
                    plugin.getLogger().warning("表单发送失败: " + e.getMessage());
                    e.printStackTrace();
                }
            });
            
            FloodgateApi.getInstance().sendForm(player.getUniqueId(), form.build());
        } catch (Exception e) {
            player.sendMessage("§c无法打开表单界面！");
            plugin.getLogger().warning("表单发送失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}