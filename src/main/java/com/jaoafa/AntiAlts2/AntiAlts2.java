package com.jaoafa.AntiAlts2;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.jaoafa.AntiAlts2.Command.Cmd_Alts;
import com.jaoafa.AntiAlts2.Event.Event_AsyncPreLogin;

public class AntiAlts2 extends JavaPlugin {
	/**
	 * プラグインが起動したときに呼び出し
	 * @author mine_book000
	 * @since 2018/02/15
	 */
	@Override
	public void onEnable() {
		getCommand("alts").setExecutor(new Cmd_Alts(this));
		getServer().getPluginManager().registerEvents(new Event_AsyncPreLogin(this), this);

		Load_Config(); // Config Load
	}

	public static String sqluser;
	public static String sqlpassword;
	public static Connection c = null;
	public static FileConfiguration conf;
	/**
	 * コンフィグ読み込み
	 * @author mine_book000
	 */
	private void Load_Config(){
		conf = getConfig();

		if(conf.contains("discordtoken")){
			Discord.start(this, conf.getString("discordtoken"));
		}else{
			getLogger().info("Discordへの接続に失敗しました。 [conf NotFound]");
			getLogger().info("Disable AntiAlts2...");
			getServer().getPluginManager().disablePlugin(this);
		}
		if(conf.contains("sqluser") && conf.contains("sqlpassword")){
			AntiAlts2.sqluser = conf.getString("sqluser");
			AntiAlts2.sqlpassword = conf.getString("sqlpassword");
		}else{
			getLogger().info("MySQL Connect err. [conf NotFound]");
			getLogger().info("Disable AntiAlts2...");
			getServer().getPluginManager().disablePlugin(this);
			return;
		}

		MySQL MySQL;
		if(conf.contains("sqlserver")){
			MySQL = new MySQL((String) conf.get("sqlserver"), "3306", "jaoafa", sqluser, sqlpassword);
		}else{
			MySQL = new MySQL("jaoafa.com", "3306", "jaoafa", sqluser, sqlpassword);
		}

		try {
			c = MySQL.openConnection();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
			getLogger().info("MySQL Connect err. [ClassNotFoundException]");
			getLogger().info("Disable AntiAlts2...");
			getServer().getPluginManager().disablePlugin(this);
			return;
		} catch (SQLException e) {
			e.printStackTrace();
			getLogger().info("MySQL Connect err. [SQLException: " + e.getSQLState() + "]");
			getLogger().info("Disable AntiAlts2...");
			getServer().getPluginManager().disablePlugin(this);
			return;
		}
		getLogger().info("MySQL Connect successful.");
	}

	/**
	 * プラグインが停止したときに呼び出し
	 * @author mine_book000
	 * @since 2018/02/15
	 */
	@Override
	public void onDisable() {

	}


	public static void SendMessage(CommandSender sender, Command cmd, String text) {
		sender.sendMessage("[AntiAlts2] " + ChatColor.YELLOW + text);
	}

	public static void report(Throwable exception){
		exception.printStackTrace();
		for(Player p: Bukkit.getServer().getOnlinePlayers()) {
			String group = PermissionsManager.getPermissionMainGroup(p);
			if(group.equalsIgnoreCase("Admin") || group.equalsIgnoreCase("Moderator")) {
				p.sendMessage("[AntiAlts2] " + ChatColor.GREEN + "AntiAlts2のシステム障害が発生しました。");
				p.sendMessage("[AntiAlts2] " + ChatColor.GREEN + "エラー: " + exception.getMessage());
			}
		}
		StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        exception.printStackTrace(pw);
		Discord.send("293856671799967744", "AntiAlts2でエラーが発生しました。" + "\n"
					+ "```" + sw.toString() + "```\n"
					+ "Cause: `" + exception.getCause() + "`");
	}

	public static UUID getUUID(String name){
		// https://api.mojang.com/users/profiles/minecraft/
		JSONObject json = getHttpJson("https://api.mojang.com/users/profiles/minecraft/" + name);
		if(json == null){
			return null;
		}else if(json.containsKey("id")){
			String uuid_hyphenated = new StringBuilder((String) json.get("id"))
					.insert(8, "-")
					.insert(13, "-")
					.insert(18, "-")
					.insert(23, "-")
					.toString();
			UUID uuid = UUID.fromString(uuid_hyphenated);
			return uuid;
		}else{
			return null;
		}
	}

	private static JSONObject getHttpJson(String address){
		StringBuilder builder = new StringBuilder();
		try{
			URL url = new URL(address);

			HttpURLConnection connect = (HttpURLConnection)url.openConnection();
			connect.setRequestMethod("GET");
			connect.connect();

			if(connect.getResponseCode() != HttpURLConnection.HTTP_OK){
				InputStream in = connect.getErrorStream();

				BufferedReader reader = new BufferedReader(new InputStreamReader(in));
				String line;
				while ((line = reader.readLine()) != null) {
					builder.append(line);
				}
				in.close();
				connect.disconnect();

				System.out.println("[AntiAlts2] URLGetConnected(Error): " + address);
				System.out.println("[AntiAlts2] Response: " + connect.getResponseMessage());
				report(new IOException(builder.toString()));
				return null;
			}

			InputStream in = connect.getInputStream();

			BufferedReader reader = new BufferedReader(new InputStreamReader(in));
			String line;
			while ((line = reader.readLine()) != null) {
				builder.append(line);
			}
			in.close();
			connect.disconnect();
			System.out.println("[AntiAlts2] URLGetConnected: " + address);
			System.out.println("[AntiAlts2] Data: " + builder.toString());
			JSONParser parser = new JSONParser();
			Object obj = parser.parse(builder.toString());
			JSONObject json = (JSONObject) obj;
			return json;
		}catch(Exception e){
			report(e);
			return null;
		}
	}
}
