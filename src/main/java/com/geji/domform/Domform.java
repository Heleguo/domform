package com.geji.domform;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.geysermc.floodgate.api.FloodgateApi;

public final class Domform extends JavaPlugin implements CommandExecutor, Listener {
    private boolean geyserAvailable = false;

    @Override
    public void onEnable() {
        // Plugin startup logic
        // 初始化插件
        
        // 保存默认配置文件
        saveDefaultConfig();
        
        // 检查依赖插件
        if (!Bukkit.getPluginManager().isPluginEnabled("floodgate")) {
            Bukkit.getLogger().severe("未找到Floodgate插件！插件无法正常工作。");
            return;
        }

        // 不再检查Geyser插件是否存在
        geyserAvailable = true;
        Bukkit.getLogger().info("插件已启用");

        // 初始化Dominion插件（如果存在）
        if (Bukkit.getPluginManager().isPluginEnabled("Dominion")) {
            if (DominionManager.initialize(this)) {
                Bukkit.getLogger().info("DominionManager 初始化成功");
            } else {
                Bukkit.getLogger().warning("DominionManager 初始化失败");
            }
        } else {
            Bukkit.getLogger().warning("未找到Dominion插件，相关功能将不可用");
        }

        // 注册命令
        this.getCommand("domform").setExecutor(this);
        
        Bukkit.getLogger().info("Domform 插件已准备就绪!");
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        Bukkit.getLogger().info("Domform 插件正在关闭...");
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("domform")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("该命令只能由玩家执行！");
                return true;
            }
            
            Player player = (Player) sender;
            
            // 检查是否为基岩版玩家
            if (!isBedrockPlayer(player)) {
                player.sendMessage("§c该命令仅适用于基岩版玩家！");
                return true;
            }
            
            // 检查Dominion是否可用
            if (!DominionManager.isInitialized()) {
                player.sendMessage("§cDominion插件当前不可用！");
                return true;
            }
            
            // 打开表单菜单
            FormManager.openMainMenu(player, this);
            return true;
        }
        return false;
    }
    
    private boolean isBedrockPlayer(Player player) {
        // 使用Floodgate API检查是否为基岩版玩家
        try {
            return FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId());
        } catch (Exception e) {
            this.getLogger().warning("无法检测玩家平台类型: " + e.getMessage());
            return false;
        }
    }
    
    public boolean isGeyserAvailable() {
        return geyserAvailable;
    }
}