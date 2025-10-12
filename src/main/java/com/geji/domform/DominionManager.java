package com.geji.domform;

import cn.lunadeer.dominion.api.DominionAPI;
import cn.lunadeer.dominion.api.dtos.DominionDTO;
import cn.lunadeer.dominion.api.dtos.MemberDTO;
import cn.lunadeer.dominion.api.dtos.PlayerDTO;
import cn.lunadeer.dominion.api.dtos.flag.PriFlag;
import cn.lunadeer.dominion.api.dtos.flag.Flags;
import cn.lunadeer.dominion.api.dtos.CuboidDTO;
import cn.lunadeer.dominion.events.dominion.modify.DominionSetMessageEvent;
import cn.lunadeer.dominion.providers.DominionProvider;
import cn.lunadeer.dominion.providers.MemberProvider;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.World;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class DominionManager {
    @Getter
    private static DominionAPI dominionAPI = null;
    @Getter
    private static boolean initialized = false;

    /**
     * 初始化DominionAPI
     *
     * @param plugin 插件实例
     * @return 是否初始化成功
     */
    public static boolean initialize(Plugin plugin) {
        if (initialized) {
            return true;
        }

        if (Bukkit.getPluginManager().isPluginEnabled("Dominion")) {
            try {
                dominionAPI = DominionAPI.getInstance();
                initialized = true;
                plugin.getLogger().info("成功连接到Dominion插件");
                return true;
            } catch (Exception e) {
                plugin.getLogger().severe("无法获取DominionAPI实例: " + e.getMessage());
                return false;
            }
        } else {
            plugin.getLogger().warning("未找到Dominion插件，相关功能将不可用");
            return false;
        }
    }

    /**
     * 获取玩家的所有领地
     *
     * @param player 玩家
     * @return 领地列表
     */
    public static List<DominionDTO> getPlayerDominions(Player player) {
        if (!initialized) return List.of();
        return dominionAPI.getPlayerOwnDominionDTOs(player.getUniqueId());
    }

    /**
     * 根据名称获取领地
     *
     * @param name 领地名称
     * @return 领地对象
     */
    public static DominionDTO getDominionByName(String name) {
        if (!initialized) return null;
        return dominionAPI.getDominion(name);
    }

    /**
     * 创建领地
     *
     * @param player 玩家
     * @param name 领地名称
     * @param description 领地描述
     * @return 创建的领地
     */
    public static CompletableFuture<DominionDTO> createDominion(Player player, String name, String description) {
        if (!initialized) return CompletableFuture.completedFuture(null);
        
        // 创建一个默认的立方体区域（以玩家为中心的较小区域）
        World world = player.getWorld();
        int x = player.getLocation().getBlockX();
        int y = player.getLocation().getBlockY();
        int z = player.getLocation().getBlockZ();
        
        // 创建一个以玩家为中心的立方体区域
        CuboidDTO cuboid = new CuboidDTO(
            x - 10, // x1
            Math.max(0, y - 10), // y1，确保不低于0
            z - 10, // z1
            x + 10, // x2
            Math.min(255, y + 10), // y2，确保不高于255
            z + 10  // z2
        );
        
        // 使用玩家作为操作者，并不跳过经济检查，让插件处理经济和上限等配置
        return DominionProvider.getInstance().createDominion(
            player, 
            name, 
            player.getUniqueId(), 
            world, 
            cuboid, 
            null,  // parent
            false  // 不跳过经济检查，让插件处理经济和上限等配置
        );
    }

    /**
     * 删除领地
     *
     * @param dominion 领地
     * @return 是否删除成功
     */
    public static CompletableFuture<DominionDTO> deleteDominion(DominionDTO dominion) {
        if (!initialized) return CompletableFuture.completedFuture(null);
        return DominionProvider.getInstance().deleteDominion(Bukkit.getConsoleSender(), dominion, true);
    }

    /**
     * 获取领地内的玩家当前位置的领地
     *
     * @param player 玩家
     * @return 当前位置的领地
     */
    public static DominionDTO getPlayerCurrentDominion(Player player) {
        if (!initialized) return null;
        return dominionAPI.getPlayerCurrentDominion(player);
    }

    /**
     * 获取领地成员列表
     *
     * @param dominion 领地
     * @return 成员列表
     */
    public static List<MemberDTO> getMembers(DominionDTO dominion) {
        if (!initialized) return List.of();
        return dominion.getMembers();
    }

    /**
     * 添加成员到领地
     *
     * @param dominion 领地
     * @param playerUuid 玩家UUID
     * @param playerName 玩家名称
     * @return 是否添加成功
     */
    public static CompletableFuture<MemberDTO> addMember(DominionDTO dominion, UUID playerUuid, String playerName) {
        if (!initialized) return CompletableFuture.completedFuture(null);
        
        PlayerDTO playerDTO = dominionAPI.getPlayer(playerName);
        if (playerDTO == null) {
            // 如果通过名称找不到，尝试通过UUID查找
            if (playerUuid != null) {
                playerDTO = dominionAPI.getPlayer(playerUuid);
            }
        }
        
        if (playerDTO == null) {
            return CompletableFuture.completedFuture(null);
        }
        
        return MemberProvider.getInstance().addMember(Bukkit.getConsoleSender(), dominion, playerDTO);
    }

    /**
     * 从领地移除成员
     *
     * @param dominion 领地
     * @param member 成员
     * @return 是否移除成功
     */
    public static CompletableFuture<MemberDTO> removeMember(DominionDTO dominion, MemberDTO member) {
        if (!initialized) return CompletableFuture.completedFuture(null);
        return MemberProvider.getInstance().removeMember(Bukkit.getConsoleSender(), dominion, member);
    }

    /**
     * 设置领地消息
     *
     * @param dominion 领地
     * @param type 消息类型 (ENTER/LEAVE)
     * @param message 消息内容
     * @return 更新后的领地
     */
    public static CompletableFuture<DominionDTO> setDominionMessage(DominionDTO dominion, DominionSetMessageEvent.TYPE type, String message) {
        if (!initialized) return CompletableFuture.completedFuture(null);
        // 使用玩家作为操作者
        return DominionProvider.getInstance().setDominionMessage(Bukkit.getConsoleSender(), dominion, type, message);
    }

    /**
     * 设置领地权限标志
     *
     * @param dominion 领地
     * @param flag 权限标志
     * @param value 标志值
     * @return 更新后的领地
     */
    public static CompletableFuture<DominionDTO> setDominionGuestFlag(DominionDTO dominion, PriFlag flag, boolean value) {
        if (!initialized) return CompletableFuture.completedFuture(null);
        // 使用玩家作为操作者
        return DominionProvider.getInstance().setDominionGuestFlag(Bukkit.getConsoleSender(), dominion, flag, value);
    }
    
    /**
     * 获取所有可用的权限标志
     *
     * @return 所有权限标志列表
     */
    public static List<PriFlag> getAllPriFlags() {
        if (!initialized) return List.of();
        return Flags.getAllPriFlagsEnable();
    }
}