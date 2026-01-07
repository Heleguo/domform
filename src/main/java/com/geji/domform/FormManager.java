package com.geji.domform;

import cn.lunadeer.dominion.api.DominionAPI;
import cn.lunadeer.dominion.api.dtos.DominionDTO;
import cn.lunadeer.dominion.api.dtos.MemberDTO;
import cn.lunadeer.dominion.api.dtos.PlayerDTO;
import cn.lunadeer.dominion.api.dtos.flag.PriFlag;
import cn.lunadeer.dominion.events.dominion.modify.DominionReSizeEvent;
import cn.lunadeer.dominion.events.dominion.modify.DominionSetMessageEvent;
import cn.lunadeer.dominion.providers.DominionProvider;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.form.ModalForm;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.util.FormImage;
import org.geysermc.floodgate.api.FloodgateApi;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class FormManager {

    /* ---------------------------
     * 线程工具：保证主线程执行
     * --------------------------- */

    private static void runSync(JavaPlugin plugin, Runnable runnable) {
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }

    private static <T> CompletableFuture<T> supplySync(JavaPlugin plugin, java.util.concurrent.Callable<CompletableFuture<T>> supplier) {
        CompletableFuture<T> out = new CompletableFuture<>();
        runSync(plugin, () -> {
            try {
                CompletableFuture<T> inner = supplier.call();
                inner.whenComplete((res, ex) -> {
                    if (ex != null) out.completeExceptionally(ex);
                    else out.complete(res);
                });
            } catch (Throwable t) {
                out.completeExceptionally(t);
            }
        });
        return out;
    }

    /* ---------------------------
     * 主菜单
     * --------------------------- */

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
                    case 0 -> openCreateLandForm(player, plugin);
                    case 1 -> openMyLandsForm(player, plugin);
                    case 2 -> openCurrentLandForm(player, plugin);
                    case 3 -> openPermissionForm(player, plugin);
                    case 4 -> openDeleteLandForm(player, plugin);
                }
            });

            FloodgateApi.getInstance().sendForm(player.getUniqueId(), form.build());
        } catch (Exception e) {
            player.sendMessage("§c无法打开表单界面！请确保您是基岩版玩家且服务器配置正确。");
            plugin.getLogger().warning("表单发送失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /* ---------------------------
     * 创建领地
     * --------------------------- */

    private static void openCreateLandForm(Player player, JavaPlugin plugin) {
        try {
            List<DominionDTO> playerDominions = DominionManager.getPlayerDominions(player);

            CustomForm.Builder form = CustomForm.builder()
                    .title("创建新领地");

            form.label("您当前拥有 " + playerDominions.size() + " 块领地");
            form.input("领地名称", "例如：myland");

            form.validResultHandler((response) -> {
                String landName = response.asInput(1);

                runSync(plugin, () -> player.sendMessage("§a正在尝试创建名为 " + landName + " 的领地..."));

                CompletableFuture<DominionDTO> future = DominionManager.createDominion(player, landName, "");
                future.thenAccept(dominion -> runSync(plugin, () -> {
                            if (dominion != null) player.sendMessage("§a成功创建领地：" + landName);
                            else player.sendMessage("§c创建领地失败，请重试。");
                        }))
                        .exceptionally(throwable -> {
                            runSync(plugin, () -> player.sendMessage("§c创建领地时发生错误：" + throwable.getMessage()));
                            return null;
                        });

                openMainMenu(player, plugin);
            });

            FloodgateApi.getInstance().sendForm(player.getUniqueId(), form.build());
        } catch (Exception e) {
            player.sendMessage("§c无法打开表单界面！");
            plugin.getLogger().warning("表单发送失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /* ---------------------------
     * 我的领地列表
     * --------------------------- */

    private static void openMyLandsForm(Player player, JavaPlugin plugin) {
        try {
            SimpleForm.Builder form = SimpleForm.builder()
                    .title("我的领地")
                    .content("以下是您拥有的所有领地：");

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

                if (buttonId == dominions.size()) {
                    openMainMenu(player, plugin);
                    return;
                }

                DominionDTO selectedDominion = dominions.get(buttonId);
                runSync(plugin, () -> player.sendMessage("§a选择了领地：" + selectedDominion.getName()));
                openLandManagementForm(player, selectedDominion, plugin);
            });

            FloodgateApi.getInstance().sendForm(player.getUniqueId(), form.build());
        } catch (Exception e) {
            player.sendMessage("§c无法打开表单界面！");
            plugin.getLogger().warning("表单发送失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /* ---------------------------
     * 当前位置领地
     * --------------------------- */

    private static void openCurrentLandForm(Player player, JavaPlugin plugin) {
        DominionDTO currentDominion = DominionManager.getPlayerCurrentDominion(player);

        if (currentDominion == null) {
            try {
                SimpleForm.Builder form = SimpleForm.builder()
                        .title("当前位置")
                        .content("您当前不在任何领地内。")
                        .button("§a<< 返回主菜单", FormImage.Type.PATH, "textures/ui/worldsIcon.png");

                form.validResultHandler((response) -> openMainMenu(player, plugin));

                FloodgateApi.getInstance().sendForm(player.getUniqueId(), form.build());
            } catch (Exception e) {
                player.sendMessage("§c无法打开表单界面！");
                plugin.getLogger().warning("表单发送失败: " + e.getMessage());
                e.printStackTrace();
            }
            return;
        }

        if (!isPlayerDominionOwner(player, currentDominion)) {
            try {
                SimpleForm.Builder form = SimpleForm.builder()
                        .title("当前位置领地")
                        .content("领地名称: " + currentDominion.getName() + "\n您不是该领地的主人，无法进行管理操作。")
                        .button("§a<< 返回主菜单", FormImage.Type.PATH, "textures/ui/worldsIcon.png");

                form.validResultHandler((response) -> openMainMenu(player, plugin));

                FloodgateApi.getInstance().sendForm(player.getUniqueId(), form.build());
                return;
            } catch (Exception e) {
                player.sendMessage("§c无法打开表单界面！");
                plugin.getLogger().warning("表单发送失败: " + e.getMessage());
                e.printStackTrace();
                return;
            }
        }

        openLandManagementForm(player, currentDominion, plugin);
    }

    /* ---------------------------
     * 管理领地菜单
     * --------------------------- */

    private static void openLandManagementForm(Player player, DominionDTO dominion, JavaPlugin plugin) {
        try {
            SimpleForm.Builder form = SimpleForm.builder()
                    .title("管理领地：" + dominion.getName())
                    .content("领地名称：" + dominion.getName());

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
                    case 0 -> openMemberPermissionForm(player, dominion, plugin);
                    case 1 -> openAddMemberForm(player, dominion, plugin);
                    case 2 -> openRemoveMemberForm(player, dominion, plugin);
                    case 3 -> openSetMessageForm(player, dominion, plugin);
                    case 4 -> openTeleportMenu(player, dominion, plugin);
                    case 5 -> transferDominion(player, dominion, plugin);
                    case 6 -> setDominionSize(player, dominion, plugin);
                    case 7 -> openMainMenu(player, plugin);
                }
            });

            FloodgateApi.getInstance().sendForm(player.getUniqueId(), form.build());
        } catch (Exception e) {
            player.sendMessage("§c无法打开表单界面！");
            plugin.getLogger().warning("表单发送失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /* ---------------------------
     * 成员权限管理
     * --------------------------- */

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

            for (int i = 0; i < members.size(); i++) {
                form.button("成员 " + (i + 1), FormImage.Type.PATH, "textures/ui/icon_steve.png");
            }

            form.button("§a<< 返回上级菜单", FormImage.Type.PATH, "textures/ui/worldsIcon.png");

            form.validResultHandler((response) -> {
                int buttonId = response.clickedButtonId();

                if (buttonId == members.size()) {
                    openLandManagementForm(player, dominion, plugin);
                    return;
                }

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

    private static void openSpecificMemberPermissionForm(Player player, DominionDTO dominion, MemberDTO member, JavaPlugin plugin) {
        try {
            List<PriFlag> allFlags = DominionManager.getAllPriFlags();

            CustomForm.Builder form = CustomForm.builder()
                    .title("管理成员权限：" + dominion.getName())
                    .label("成员: " + member.getPlayer().getLastKnownName());

            boolean[] currentValues = new boolean[allFlags.size()];

            for (int i = 0; i < allFlags.size(); i++) {
                PriFlag flag = allFlags.get(i);
                boolean currentValue = member.getFlagValue(flag);
                currentValues[i] = currentValue;
                form.toggle(flag.getDisplayName(), currentValue);
            }

            form.validResultHandler((response) -> {
                List<CompletableFuture<Void>> futures = new ArrayList<>();

                for (int i = 0; i < allFlags.size(); i++) {
                    PriFlag flag = allFlags.get(i);
                    boolean oldValue = currentValues[i];
                    boolean newValue = response.asToggle(i + 1);

                    if (oldValue != newValue) {
                        CompletableFuture<MemberDTO> future = DominionManager.setMemberFlag(dominion, member, flag, newValue);
                        futures.add(future.thenRun(() -> {}));
                    }
                }

                if (futures.isEmpty()) {
                    runSync(plugin, () -> {
                        player.sendMessage("§a没有权限需要更新");
                        openMainMenu(player, plugin);
                    });
                    return;
                }

                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .thenRun(() -> runSync(plugin, () -> {
                            player.sendMessage("§a已更新成员的权限设置");
                            openMemberPermissionForm(player, dominion, plugin);
                        }))
                        .exceptionally(throwable -> {
                            runSync(plugin, () -> {
                                player.sendMessage("§c更新成员权限时发生错误：" + throwable.getMessage());
                                openMemberPermissionForm(player, dominion, plugin);
                            });
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

    /* ---------------------------
     * 转让领地（主线程）
     * --------------------------- */

    private static void transferDominion(Player player, DominionDTO dominionDTO, JavaPlugin plugin) {
        CustomForm.Builder form = CustomForm.builder().title("转让领地");
        form.input("§c这是一个十分危险的操作！\n§f玩家名:");

        form.validResultHandler(r -> {
            String name = r.asInput(0);

            runSync(plugin, () -> {
                PlayerDTO target = DominionManager.getDominionAPI().getPlayer(name);
                if (target != null) {
                    DominionProvider.getInstance().transferDominion(player, dominionDTO, target);
                } else {
                    player.sendMessage("§c玩家不存在！");
                }
            });
        });

        FloodgateApi.getInstance().sendForm(player.getUniqueId(), form.build());
    }

    /* ---------------------------
     * 调整领地大小（修复核心）
     * --------------------------- */

    private static void setDominionSize(Player player, DominionDTO dominionDTO, JavaPlugin plugin) {
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
                int northSize = parseOrDefault(r.asInput(1), 0);
                int southSize = parseOrDefault(r.asInput(2), 0);
                int westSize  = parseOrDefault(r.asInput(3), 0);
                int eastSize  = parseOrDefault(r.asInput(4), 0);
                int upSize    = parseOrDefault(r.asInput(5), 0);
                int downSize  = parseOrDefault(r.asInput(6), 0);

                CompletableFuture<DominionDTO> future =
                        resizeDominionDirectionSync(plugin, player, dominionDTO, northSize, DominionReSizeEvent.DIRECTION.NORTH);

                future
                        .thenCompose(resized -> resizeDominionDirectionSync(plugin, player, resized, southSize, DominionReSizeEvent.DIRECTION.SOUTH))
                        .thenCompose(resized -> resizeDominionDirectionSync(plugin, player, resized, westSize, DominionReSizeEvent.DIRECTION.WEST))
                        .thenCompose(resized -> resizeDominionDirectionSync(plugin, player, resized, eastSize, DominionReSizeEvent.DIRECTION.EAST))
                        .thenCompose(resized -> resizeDominionDirectionSync(plugin, player, resized, upSize, DominionReSizeEvent.DIRECTION.UP))
                        .thenCompose(resized -> resizeDominionDirectionSync(plugin, player, resized, downSize, DominionReSizeEvent.DIRECTION.DOWN))
                        .thenAccept(resized -> runSync(plugin, () -> player.sendMessage("§a领地大小已成功更新！")))
                        .exceptionally(ex -> {
                            runSync(plugin, () -> player.sendMessage("§c调整领地大小时发生错误: " + ex.getMessage()));
                            ex.printStackTrace();
                            return null;
                        });

            } catch (Exception e) {
                runSync(plugin, () -> player.sendMessage("§c输入格式错误，请输入有效的数字！"));
            }
        });

        FloodgateApi.getInstance().sendForm(player.getUniqueId(), form.build());
    }

    /**
     * 强制主线程调用 Dominion resize，并桥接回 CompletableFuture
     * 这是修复 DominionReSizeEvent 同步触发要求的关键
     */
    private static CompletableFuture<DominionDTO> resizeDominionDirectionSync(
            JavaPlugin plugin,
            Player player,
            DominionDTO dominion,
            int size,
            DominionReSizeEvent.DIRECTION direction
    ) {
        if (size == 0) return CompletableFuture.completedFuture(dominion);

        // 这里严格保证：调用 resizeDominion 的那一刻在主线程
        return supplySync(plugin, () ->
                DominionAPI.getDominionProvider()
                        .resizeDominion(player, dominion, DominionReSizeEvent.TYPE.EXPAND, direction, size)
        );
    }

    private static int parseOrDefault(String input, int defaultValue) {
        if (input == null || input.trim().isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(input.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /* ---------------------------
     * 权限设置
     * --------------------------- */

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

                if (buttonId == playerDominions.size()) {
                    openMainMenu(player, plugin);
                    return;
                }

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
        if (!isPlayerDominionOwner(player, dominion)) {
            showReadOnlyGuestPermissionForm(player, dominion, plugin);
            return;
        }

        try {
            List<PriFlag> allFlags = DominionManager.getAllPriFlags();

            CustomForm.Builder form = CustomForm.builder()
                    .title("访客权限设置：" + dominion.getName());

            boolean[] currentValues = new boolean[allFlags.size()];

            for (int i = 0; i < allFlags.size(); i++) {
                PriFlag flag = allFlags.get(i);
                boolean currentValue = dominion.getGuestFlagValue(flag);
                currentValues[i] = currentValue;
                form.toggle(flag.getDisplayName(), currentValue);
            }

            form.validResultHandler((response) -> {
                List<CompletableFuture<Void>> futures = new ArrayList<>();

                for (int i = 0; i < allFlags.size(); i++) {
                    PriFlag flag = allFlags.get(i);
                    boolean oldValue = currentValues[i];
                    boolean newValue = response.asToggle(i);

                    if (oldValue != newValue) {
                        CompletableFuture<DominionDTO> future = DominionManager.setDominionGuestFlag(dominion, flag, newValue);
                        futures.add(future.thenRun(() -> {}));
                    }
                }

                if (futures.isEmpty()) {
                    runSync(plugin, () -> {
                        player.sendMessage("§a没有权限需要更新");
                        openMainMenu(player, plugin);
                    });
                    return;
                }

                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .thenRun(() -> runSync(plugin, () -> {
                            player.sendMessage("§a已更新领地 " + dominion.getName() + " 的访客权限设置");
                            openMainMenu(player, plugin);
                        }))
                        .exceptionally(throwable -> {
                            runSync(plugin, () -> {
                                player.sendMessage("§c更新访客权限时发生错误：" + throwable.getMessage());
                                openMainMenu(player, plugin);
                            });
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

    private static boolean isPlayerDominionOwner(Player player, DominionDTO dominion) {
        List<DominionDTO> playerDominions = DominionManager.getPlayerDominions(player);
        for (DominionDTO playerDominion : playerDominions) {
            if (playerDominion.getName().equals(dominion.getName())) {
                return true;
            }
        }
        return false;
    }

    private static void showReadOnlyGuestPermissionForm(Player player, DominionDTO dominion, JavaPlugin plugin) {
        try {
            List<PriFlag> allFlags = DominionManager.getAllPriFlags();

            CustomForm.Builder form = CustomForm.builder()
                    .title("访客权限查看：" + dominion.getName() + " (只读模式)");

            form.label("您不是该领地的主人，只能查看访客权限设置");

            for (PriFlag flag : allFlags) {
                boolean currentValue = dominion.getGuestFlagValue(flag);
                form.label(flag.getDisplayName() + ": " + (currentValue ? "开启" : "关闭"));
            }

            form.validResultHandler((response) -> openMainMenu(player, plugin));

            FloodgateApi.getInstance().sendForm(player.getUniqueId(), form.build());
        } catch (Exception e) {
            player.sendMessage("§c无法打开访客权限查看界面！");
            plugin.getLogger().warning("访客权限查看界面发送失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /* ---------------------------
     * 添加/移除成员
     * --------------------------- */

    private static void openAddMemberForm(Player player, DominionDTO dominion, JavaPlugin plugin) {
        try {
            CustomForm.Builder form = CustomForm.builder()
                    .title("添加成员：" + dominion.getName())
                    .input("玩家名称", "输入玩家ID");

            form.validResultHandler((response) -> {
                String playerName = response.asInput(0);

                CompletableFuture<MemberDTO> future = DominionManager.addMember(dominion, null, playerName);
                future.thenAccept(member -> runSync(plugin, () -> {
                            if (member != null) {
                                player.sendMessage("§a已将玩家 " + playerName + " 添加到领地 " + dominion.getName());
                            } else {
                                player.sendMessage("§c添加玩家 " + playerName + " 到领地 " + dominion.getName() + " 失败");
                            }
                        }))
                        .exceptionally(throwable -> {
                            runSync(plugin, () -> player.sendMessage("§c添加成员时发生错误：" + throwable.getMessage()));
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

                    CompletableFuture<MemberDTO> future = DominionManager.removeMember(dominion, member);
                    future.thenAccept(removedMember -> runSync(plugin, () -> {
                                if (removedMember != null) {
                                    player.sendMessage("§a已将玩家从领地 " + dominion.getName() + " 移除");
                                } else {
                                    player.sendMessage("§c从领地 " + dominion.getName() + " 移除玩家失败");
                                }
                            }))
                            .exceptionally(throwable -> {
                                runSync(plugin, () -> player.sendMessage("§c移除成员时发生错误：" + throwable.getMessage()));
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

    /* ---------------------------
     * 设置进入/离开消息
     * --------------------------- */

    private static void openSetMessageForm(Player player, DominionDTO dominion, JavaPlugin plugin) {
        try {
            CustomForm.Builder form = CustomForm.builder()
                    .title("设置消息：" + dominion.getName())
                    .input("进入消息", "例如：欢迎来到我的领地")
                    .input("离开消息", "例如：感谢访问，再见");

            form.validResultHandler((response) -> {
                String enterMessage = response.asInput(0);
                String leaveMessage = response.asInput(1);

                CompletableFuture<DominionDTO> enterFuture =
                        DominionManager.setDominionMessage(dominion, DominionSetMessageEvent.TYPE.ENTER, enterMessage);
                enterFuture.thenAccept(result -> runSync(plugin, () -> {
                            if (result == null) player.sendMessage("§c设置进入消息失败");
                        }))
                        .exceptionally(throwable -> {
                            runSync(plugin, () -> player.sendMessage("§c设置进入消息时发生错误：" + throwable.getMessage()));
                            return null;
                        });

                CompletableFuture<DominionDTO> leaveFuture =
                        DominionManager.setDominionMessage(dominion, DominionSetMessageEvent.TYPE.LEAVE, leaveMessage);
                leaveFuture.thenAccept(result -> runSync(plugin, () -> {
                            if (result == null) player.sendMessage("§c设置离开消息失败");
                        }))
                        .exceptionally(throwable -> {
                            runSync(plugin, () -> player.sendMessage("§c设置离开消息时发生错误：" + throwable.getMessage()));
                            return null;
                        });

                runSync(plugin, () -> {
                    player.sendMessage("§a已设置领地消息：");
                    player.sendMessage("§a进入消息：" + enterMessage);
                    player.sendMessage("§a离开消息：" + leaveMessage);
                    openLandManagementForm(player, dominion, plugin);
                });
            });

            FloodgateApi.getInstance().sendForm(player.getUniqueId(), form.build());
        } catch (Exception e) {
            player.sendMessage("§c无法打开表单界面！");
            plugin.getLogger().warning("表单发送失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /* ---------------------------
     * 传送菜单
     * --------------------------- */

    private static void openTeleportMenu(Player player, DominionDTO dominion, JavaPlugin plugin) {
        try {
            SimpleForm.Builder form = SimpleForm.builder()
                    .title("传送设置：" + dominion.getName())
                    .content("请选择传送操作：")
                    .button("传送到此领地")
                    .button("在当前位置设置传送点");

            form.validResultHandler((response) -> {
                switch (response.clickedButtonId()) {
                    case 0 -> runSync(plugin, () -> {
                        if (dominion.getTpLocation() != null) {
                            player.teleport(dominion.getTpLocation());
                            player.sendMessage("§a已传送到领地 " + dominion.getName());
                        } else {
                            player.sendMessage("§c该领地尚未设置传送点！");
                        }
                        openLandManagementForm(player, dominion, plugin);
                    });
                    case 1 -> runSync(plugin, () -> {
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
                    });
                }
            });

            FloodgateApi.getInstance().sendForm(player.getUniqueId(), form.build());
        } catch (Exception e) {
            player.sendMessage("§c无法打开传送菜单！");
            plugin.getLogger().warning("传送菜单发送失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /* ---------------------------
     * 删除领地
     * --------------------------- */

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
                            future.thenAccept(deletedDominion -> runSync(plugin, () -> {
                                        if (deletedDominion != null) {
                                            player.sendMessage("§a领地 " + dominion.getName() + " 已被删除");
                                        } else {
                                            player.sendMessage("§c删除领地失败");
                                        }
                                    }))
                                    .exceptionally(throwable -> {
                                        runSync(plugin, () -> player.sendMessage("§c删除领地时发生错误：" + throwable.getMessage()));
                                        return null;
                                    });
                        } else {
                            runSync(plugin, () -> player.sendMessage("§e操作已取消"));
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

    /* ---------------------------
     * 工具方法
     * --------------------------- */

    private static String[] getMemberNames(DominionDTO dominion) {
        List<MemberDTO> members = DominionManager.getMembers(dominion);
        String[] memberNames = new String[members.size()];
        for (int i = 0; i < members.size(); i++) {
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
}
