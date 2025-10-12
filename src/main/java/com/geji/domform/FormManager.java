package com.geji.domform;

import cn.lunadeer.dominion.api.dtos.DominionDTO;
import cn.lunadeer.dominion.api.dtos.MemberDTO;
import cn.lunadeer.dominion.api.dtos.flag.PriFlag;
import cn.lunadeer.dominion.events.dominion.modify.DominionSetMessageEvent;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.form.ModalForm;
import org.geysermc.cumulus.util.FormImage;
import org.geysermc.floodgate.api.FloodgateApi;

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
                    .button("领地权限设置", FormImage.Type.PATH, "textures/ui/icon_lock.png")
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
            SimpleForm.Builder form = SimpleForm.builder()
                    .title("管理领地：" + dominion.getName())
                    .content("领地名称：" + dominion.getName())
                    .button("编辑权限", FormImage.Type.PATH, "textures/items/name_tag.png")
                    .button("添加成员", FormImage.Type.PATH, "textures/items/name_tag.png")
                    .button("移除成员", FormImage.Type.PATH, "textures/items/name_tag.png")
                    .button("设置进入/离开消息", FormImage.Type.PATH, "textures/ui/store_home_icon.png")
                    .button("传送点设置", FormImage.Type.PATH, "textures/items/ender_pearl.png")
                    .button("§a<< 返回主菜单", FormImage.Type.PATH, "textures/ui/worldsIcon.png");
            
            form.validResultHandler((response) -> {
                switch (response.clickedButtonId()) {
                    case 0:
                        openPermissionForm(player, dominion, plugin);
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
                        player.sendMessage("§e传送点设置功能尚未实现");
                        openLandManagementForm(player, dominion, plugin);
                        break;
                    case 5:
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
    
    // 重载方法，用于从领地管理界面直接打开权限设置
    private static void openPermissionForm(Player player, DominionDTO dominion, JavaPlugin plugin) {
        // 直接跳转到领地权限设置界面
        openLandPermissionForm(player, dominion, plugin);
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
            showReadOnlyPermissionForm(player, dominion, plugin);
            return;
        }
        
        try {
            // 获取所有可用的权限标志
            List<PriFlag> allFlags = DominionManager.getAllPriFlags();
            
            CustomForm.Builder form = CustomForm.builder()
                    .title("权限设置：" + dominion.getName());
            
            // 为每个权限添加切换开关
            Map<String, Boolean> currentFlags = new HashMap<>();
            for (PriFlag flag : allFlags) {
                // 这里应该从API获取当前权限状态，暂时默认为false
                boolean currentValue = false;
                form.toggle(flag.getDisplayName(), currentValue);
                // 使用flag对象本身作为键，而不是getName()
                currentFlags.put(flag.getDisplayName(), currentValue);
            }
            
            form.validResultHandler((response) -> {
                // 处理权限设置
                List<CompletableFuture<Void>> futures = new ArrayList<>();
                
                for (int i = 0; i < allFlags.size(); i++) {
                    PriFlag flag = allFlags.get(i);
                    boolean newValue = response.asToggle(i);
                    
                    // 只有当权限值发生变化时才更新
                    // 使用flag对象的显示名称作为键
                    if (currentFlags.get(flag.getDisplayName()) != newValue) {
                        CompletableFuture<DominionDTO> future = DominionManager.setDominionGuestFlag(dominion, flag, newValue);
                        futures.add(future.thenRun(() -> {
                            // 空操作，只是等待完成
                        }));
                    }
                }
                
                // 等待所有权限更新完成
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .thenRun(() -> {
                        player.sendMessage("§a已更新领地 " + dominion.getName() + " 的权限设置");
                        openLandManagementForm(player, dominion, plugin);
                    })
                    .exceptionally(throwable -> {
                        player.sendMessage("§c更新权限时发生错误：" + throwable.getMessage());
                        openLandManagementForm(player, dominion, plugin);
                        return null;
                    });
            });
            
            FloodgateApi.getInstance().sendForm(player.getUniqueId(), form.build());
        } catch (Exception e) {
            player.sendMessage("§c无法打开权限设置界面！");
            plugin.getLogger().warning("权限设置界面发送失败: " + e.getMessage());
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
     * 显示只读权限界面（供非领地主人查看）
     * 
     * @param player 玩家
     * @param dominion 领地
     * @param plugin 插件实例
     */
    private static void showReadOnlyPermissionForm(Player player, DominionDTO dominion, JavaPlugin plugin) {
        try {
            // 获取所有可用的权限标志
            List<PriFlag> allFlags = DominionManager.getAllPriFlags();
            
            CustomForm.Builder form = CustomForm.builder()
                    .title("权限查看：" + dominion.getName() + " (只读模式)");
            https://dominion.lunadeer.cn/en/notes/api/operate/
            // 添加说明文本
            form.label("您不是该领地的主人，只能查看权限设置");
            
            // 为每个权限添加只读标签（显示默认值）
            for (PriFlag flag : allFlags) {
                form.label(flag.getDisplayName() + ": " + "关闭");
            }
            
            form.validResultHandler((response) -> {
                // 返回到领地管理界面
                openLandManagementForm(player, dominion, plugin);
            });
            
            FloodgateApi.getInstance().sendForm(player.getUniqueId(), form.build());
        } catch (Exception e) {
            player.sendMessage("§c无法打开权限查看界面！");
            plugin.getLogger().warning("权限查看界面发送失败: " + e.getMessage());
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