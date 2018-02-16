package com.jaoafa.AntiAlts2.Event;

import java.net.InetAddress;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent.Result;
import org.bukkit.plugin.java.JavaPlugin;

import com.google.common.net.InternetDomainName;
import com.jaoafa.AntiAlts2.AntiAlts2;
import com.jaoafa.AntiAlts2.MySQL;
import com.jaoafa.AntiAlts2.PermissionsManager;

public class Event_AsyncPreLogin implements Listener {
	JavaPlugin plugin;
	public Event_AsyncPreLogin(JavaPlugin plugin) {
		this.plugin = plugin;
	}
	/*
	 * ログイン試行
	 * ↓
	 * プレイヤーデータがデータベースにあるかどうか、あったらUSERID取得
	 * ↓
	 * MinecraftIDの更新の必要があれば更新
	 * ↓
	 * プレイヤーデータのラストログインの更新
	 * ↓
	 * statusを確認し、falseの場合はログインOK
	 * ↓
	 * 同一USERIDをリスト化し、1番目のUUIDに合うかどうか(→合わなければNG)
	 * ↓
	 * 同一IPアドレスで非同一UUIDがあるかどうか(SQLで判定せず同一IPアドレスアカウントリスト化してそこから調べる | →あればログインNG, 同一UUIDがなければINSERT)
	 * ↓
	 * ログイン許可？
	 * ↓
	 * 同一ドメインの非同一UUIDで、48h以内にラストログインしたプレイヤーをリスト化。管理部・モデレーターに出力
	 */
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onAsyncPreLogin(AsyncPlayerPreLoginEvent event){
		String name = event.getName();
		InetAddress address = event.getAddress();
		String ip = address.getHostAddress();
		String host = address.getHostName();
		String domain = InternetDomainName.from(host).topPrivateDomain().toString();

		plugin.getLogger().info("Name: " + name);
		plugin.getLogger().info("IP: " + ip);
		plugin.getLogger().info("HOST: " + host);
		plugin.getLogger().info("DOMAIN: " + domain);

		Statement statement = getNewStatement();
		if(statement == null) return;
		Statement statement2 = getNewStatement();
		if(statement2 == null) return;
		Statement statement3 = getNewStatement();
		if(statement3 == null) return;

		UUID uuid = AntiAlts2.getUUID(name);
		try {
			ResultSet res = statement.executeQuery("SELECT * FROM antialts WHERE uuid = '" + uuid + "'");
			if(res.next()){
				int id = res.getInt("id");
				if(!res.getString("player").equals(name)){
					// MinecraftIDが違ったら更新
					statement2.executeUpdate("UPDATE antialts SET player = '" + name + "' WHERE id = " + id);
				}
				statement2.executeUpdate("UPDATE antialts SET lastlogin = CURRENT_TIMESTAMP WHERE id = " + id);
			}






			ResultSet res = statement.executeQuery("SELECT * FROM antialts WHERE ip = '" + ip + "'");
			while(res.next()){
				// サブアカウント有り
				String MainAltID = res.getString("player");
				String MainAltUUID = res.getString("uuid");
				if(uuid.toString().equalsIgnoreCase(MainAltUUID)){
					continue;
				}
				String message = ChatColor.RED + "----- ANTI ALTS -----\n"
						+ ChatColor.RESET + ChatColor.WHITE + "あなたは以下のアカウントで既にログインをされたことがあるようです。\n"
						+ ChatColor.RESET + ChatColor.AQUA + MainAltID + " (" + MainAltUUID + ")\n"
						+ ChatColor.RESET + ChatColor.WHITE + "もしこの判定が誤判定と思われる場合は、公式Discord#supportでお問い合わせをお願い致します。";
				event.disallow(Result.KICK_BANNED, message);

				for(Player p: Bukkit.getServer().getOnlinePlayers()) {
					String group = PermissionsManager.getPermissionMainGroup(p);
					if(group.equalsIgnoreCase("Admin") || group.equalsIgnoreCase("Moderator") || group.equalsIgnoreCase("Regular") || group.equalsIgnoreCase("Default")) {
						p.sendMessage("[AntiAlts2] " + ChatColor.GREEN + name + " -> " + MainAltID);
					}
				}
			}
			ResultSet res_exists = statement2.executeQuery("SELECT COUNT(*) FROM antialts WHERE ip = '" + ip + "'");
			res_exists.next();
			int size = res_exists.getInt("COUNT(*)");
			int userid = 1;
			if(size == 0){
				ResultSet res2 = statement3.executeQuery("SELECT * FROM antialts ORDER BY userid DESC");
				if(res2.next()){
					userid = res2.getInt("userid") + 1;
				}
				statement3.executeUpdate("INSERT INTO antialts (player, uuid, userid, ip, host, domain, firstlogin, lastlogin) VALUES ('" + name + "', '" + uuid + "', '" + userid + "', '" + ip + "', '" + host + "', '" + domain + "', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);");
			}
		} catch (SQLException e) {
			AntiAlts2.report(e);
		}
	}
	private Statement getNewStatement(){
		Statement statement;
		try {
			statement = AntiAlts2.c.createStatement();
		} catch (NullPointerException e) {
			MySQL MySQL = new MySQL("jaoafa.com", "3306", "jaoafa", AntiAlts2.sqluser, AntiAlts2.sqlpassword);
			try {
				AntiAlts2.c = MySQL.openConnection();
				statement = AntiAlts2.c.createStatement();
			} catch (ClassNotFoundException | SQLException e1) {
				// TODO 自動生成された catch ブロック
				e1.printStackTrace();
				return null;
			}
		} catch (SQLException e) {
			// TODO 自動生成された catch ブロック
			e.printStackTrace();
			return null;
		}

		statement = MySQL.check(statement);
		return statement;
	}
}