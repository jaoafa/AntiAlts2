package com.jaoafa.AntiAlts2.Event;

import java.net.InetAddress;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
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
import com.jaoafa.AntiAlts2.Discord;
import com.jaoafa.AntiAlts2.MySQL;
import com.jaoafa.AntiAlts2.PermissionsManager;

public class Event_AsyncPreLogin implements Listener {
	JavaPlugin plugin;

	public Event_AsyncPreLogin(JavaPlugin plugin) {
		this.plugin = plugin;
	}

	/*
	 * 1. ログイン試行
	 * ↓
	 * 2. プレイヤーデータがデータベースにあるかどうか、あったらUSERID取得
	 * ↓
	 * 3. MinecraftIDの更新の必要があれば更新
	 * ↓
	 * 4. プレイヤーデータのラストログインの更新
	 * ↓
	 * 5. statusを確認し、falseの場合はログインOK
	 * ↓
	 * 6. 同一USERIDをリスト化し、1番目のUUIDに合うかどうか(→合わなければNG)
	 * ↓
	 * 7. 同一IPアドレスで非同一UUIDがあるかどうか(SQLで判定せず同一IPアドレスアカウントリスト化してそこから調べる | →あればログインNG, 同一UUIDがなければINSERT)
	 * ↓
	 * 8. ログイン許可？
	 * ↓
	 * 9. 同一IPからログインしてきたプレイヤーリストを管理部・モデレーター・常連に表示(Discordにも。)
	 * ↓
	 * 10. 同一ドメインの非同一UUIDで、48h以内にラストログインしたプレイヤーをリスト化。管理部・モデレーターに出力
	 */
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onAsyncPreLogin(AsyncPlayerPreLoginEvent event) {
		// 1. ログイン試行

		String name = event.getName();
		InetAddress address = event.getAddress();
		String ip = address.getHostAddress();
		String host = address.getHostName();
		String domain = "null";
		if (!ip.equalsIgnoreCase(host)) {
			domain = InternetDomainName.from(host).topPrivateDomain().toString();
		}

		plugin.getLogger().info("Name: " + name);
		plugin.getLogger().info("IP: " + ip);
		plugin.getLogger().info("HOST: " + host);
		plugin.getLogger().info("DOMAIN: " + domain);

		/*
		Statement statement = getNewStatement();
		if(statement == null){
			event.disallow(Result.KICK_BANNED, "[AntiAlts2] Login Check System Error! DebugNo.1");
			Discord.send("618569153422426113", "__**[AntiAlts2]**__ " + name + ": Login Check System Error! DebugNo.1");
			return;
		}
		Statement statement2 = getNewStatement();
		if(statement2 == null){
			event.disallow(Result.KICK_BANNED, "[AntiAlts2] Login Check System Error! DebugNo.2");
			Discord.send("618569153422426113", "__**[AntiAlts2]**__ " + name + ": Login Check System Error! DebugNo.2");
			return;
		}
		Statement statement3 = getNewStatement();
		if(statement3 == null){
			event.disallow(Result.KICK_BANNED, "[AntiAlts2] Login Check System Error! DebugNo.3");
			Discord.send("618569153422426113", "__**[AntiAlts2]**__ " + name + ": Login Check System Error! DebugNo.3");
			return;
		}
		*/

		UUID uuid = AntiAlts2.getUUID(name);
		try {
			int userid = -1;
			String MainAltID = null;
			String MainAltUUID = null;

			// 2. プレイヤーデータがデータベースにあるかどうか、あったらUSERID取得
			PreparedStatement statement = MySQL.getNewPreparedStatement("SELECT * FROM antialts WHERE uuid = ?");
			statement.setString(1, uuid.toString());
			ResultSet res = statement.executeQuery();
			if (res.next()) {
				userid = res.getInt("userid");
				if (!res.getString("player").equals(name)) {
					// 3. MinecraftIDの更新の必要があれば更新
					String oldName = res.getString("player");

					PreparedStatement statement2 = MySQL
							.getNewPreparedStatement("UPDATE antialts SET player = ? WHERE uuid = ?");
					statement2.setString(1, name);
					statement2.setString(2, uuid.toString());
					statement2.executeUpdate();

					for (Player p : Bukkit.getServer().getOnlinePlayers()) {
						String group = PermissionsManager.getPermissionMainGroup(p);
						if (group.equalsIgnoreCase("Admin") || group.equalsIgnoreCase("Moderator")
								|| group.equalsIgnoreCase("Regular")) {
							p.sendMessage("[AntiAlts2] " + ChatColor.GREEN + "|-- " + name + " : - : プレイヤー名変更情報 --|");
							p.sendMessage("[AntiAlts2] " + ChatColor.GREEN + "このプレイヤーは、前回ログインからプレイヤー名を変更しています。(旧名: "
									+ oldName + ")");
						}
					}
					Discord.send("223582668132974594", "__**[AntiAlts2]**__ `" + name + "` : - : プレイヤー名変更情報\n"
							+ "このプレイヤーは、前回ログインからプレイヤー名を変更しています。(旧名: " + oldName + ")\n"
							+ "https://ja.namemc.com/profile/" + uuid.toString());
				}
				// 4. プレイヤーデータのラストログインの更新
				PreparedStatement statement3 = MySQL
						.getNewPreparedStatement("UPDATE antialts SET lastlogin = CURRENT_TIMESTAMP WHERE uuid = ?");
				statement3.setString(1, uuid.toString());
				statement3.executeUpdate();

				// 5. statusを確認し、falseの場合はログインOK
				if (!res.getBoolean("status")) {
					return;
				}

				// 6. 同一USERIDをリスト化し、1番目のUUIDに合うかどうか(→合わなければNG)
				PreparedStatement statement4 = MySQL.getNewPreparedStatement("SELECT * FROM antialts WHERE userid = ?");
				statement4.setInt(1, userid);
				ResultSet userid_res = statement4.executeQuery();

				if (userid_res.next() && !userid_res.getString("uuid").equalsIgnoreCase(uuid.toString())) {
					MainAltID = userid_res.getString("player");
					MainAltUUID = userid_res.getString("uuid");
					String message = ChatColor.RED + "----- ANTI ALTS -----\n"
							+ ChatColor.RESET + ChatColor.WHITE + "あなたは以下のアカウントで既にログインをされたことがあるようです。(1)\n"
							+ ChatColor.RESET + ChatColor.AQUA + MainAltID + " (" + MainAltUUID + ")\n"
							+ ChatColor.RESET + ChatColor.WHITE
							+ "もしこの判定が誤判定と思われる場合は、公式Discord#supportでお問い合わせをお願い致します。";
					event.disallow(Result.KICK_BANNED, message);
					for (Player p : Bukkit.getServer().getOnlinePlayers()) {
						String group = PermissionsManager.getPermissionMainGroup(p);
						if (group.equalsIgnoreCase("Admin") || group.equalsIgnoreCase("Moderator")) {
							p.sendMessage("[AntiAlts2] " + ChatColor.GREEN + name + ": サブアカウントログイン規制(1 - メイン: "
									+ MainAltID + ")");
						}
					}
					Discord.send("223582668132974594",
							"__**[AntiAlts2]**__ `" + name + "`: サブアカウントログイン規制(1 - メイン: " + MainAltID + ")");
					return;
				}
			}

			// 7. 同一IPアドレスで非同一UUIDがあるかどうか(SQLで判定せず同一IPアドレスアカウントリスト化してそこから調べる | →あればログインNG, 同一UUIDがなければINSERT
			PreparedStatement statement5 = MySQL
					.getNewPreparedStatement("SELECT COUNT(*) FROM antialts WHERE ip = ? AND uuid = ?");
			statement5.setString(1, ip);
			statement5.setString(2, uuid.toString());
			ResultSet ips_count = statement5.executeQuery();

			PreparedStatement statement6 = MySQL.getNewPreparedStatement("SELECT * FROM antialts WHERE ip = ?");
			statement6.setString(1, ip);
			ResultSet ips_res = statement6.executeQuery();
			boolean insertbool = true;
			int count = 0;
			if (ips_count.next()) {
				count = ips_count.getInt(1);
			}
			while (ips_res.next()) {
				String id = ips_res.getString("userid");
				String PlayerID = ips_res.getString("player");
				String PlayerUUID = ips_res.getString("uuid");
				if (uuid.toString().equalsIgnoreCase(PlayerUUID)) {
					insertbool = false;
					continue;
				}
				PreparedStatement statement7 = MySQL.getNewPreparedStatement("SELECT * FROM antialts WHERE userid = ?");
				statement7.setString(1, id);
				ResultSet userid_res = statement7.executeQuery();
				if (userid_res.next() && !userid_res.getString("uuid").equalsIgnoreCase(uuid.toString())) {
					// サブアカウント？
					String message = ChatColor.RED + "----- ANTI ALTS -----\n"
							+ ChatColor.RESET + ChatColor.WHITE + "あなたは以下のアカウントで既にログインをされたことがあるようです。(2)\n"
							+ ChatColor.RESET + ChatColor.AQUA + PlayerID + " (" + PlayerUUID + ")\n"
							+ ChatColor.RESET + ChatColor.WHITE
							+ "もしこの判定が誤判定と思われる場合は、公式Discord#supportでお問い合わせをお願い致します。";
					event.disallow(Result.KICK_BANNED, message);
					for (Player p : Bukkit.getServer().getOnlinePlayers()) {
						String group = PermissionsManager.getPermissionMainGroup(p);
						if (group.equalsIgnoreCase("Admin") || group.equalsIgnoreCase("Moderator")) {
							p.sendMessage("[AntiAlts2] " + ChatColor.GREEN + name + ": サブアカウントログイン規制(2 - メイン: "
									+ PlayerID + ")");
						}
					}
					Discord.send("223582668132974594",
							"__**[AntiAlts2]**__ `" + name + "`: サブアカウントログイン規制(2 - メイン: " + PlayerID + ")");
				}
				if (count == 0) {
					PreparedStatement statement8 = MySQL.getNewPreparedStatement(
							"INSERT INTO antialts (player, uuid, userid, ip, host, domain, firstlogin, lastlogin) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);");
					statement8.setString(1, name);
					statement8.setString(2, uuid.toString());
					statement8.setString(3, id);
					statement8.setString(4, ip);
					statement8.setString(5, host);
					statement8.setString(6, domain);
					statement8.executeUpdate();
				}
				return;
			}

			if (insertbool) {
				PreparedStatement statement9 = MySQL
						.getNewPreparedStatement("SELECT * FROM antialts ORDER BY userid DESC");
				ResultSet res2 = statement9.executeQuery();
				if (res2.next()) {
					userid = res2.getInt("userid") + 1;
				}
				PreparedStatement statement10 = MySQL.getNewPreparedStatement(
						"INSERT INTO antialts (player, uuid, userid, ip, host, domain, firstlogin, lastlogin) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);");
				statement10.setString(1, name);
				statement10.setString(2, uuid.toString());
				statement10.setInt(3, userid);
				statement10.setString(4, ip);
				statement10.setString(5, host);
				statement10.setString(6, domain);
				statement10.executeUpdate();
			}

			// 9. 同一ユーザIDのプレイヤーリストを管理部・モデレーター・常連に表示(Discordにも。)
			PreparedStatement statement11 = MySQL.getNewPreparedStatement("SELECT * FROM antialts WHERE userid = ?");
			statement11.setInt(1, userid);
			ResultSet ips_res_2 = statement11.executeQuery();
			Set<String> equal_ips = new HashSet<>();
			while (ips_res_2.next()) {
				String PlayerID = ips_res_2.getString("player");
				String PlayerUUID = ips_res_2.getString("uuid");
				if (uuid.toString().equalsIgnoreCase(PlayerUUID)) {
					continue;
				}
				equal_ips.add(PlayerID);
			}
			if (equal_ips.size() != 0) {
				for (Player p : Bukkit.getServer().getOnlinePlayers()) {
					String group = PermissionsManager.getPermissionMainGroup(p);
					if (group.equalsIgnoreCase("Admin") || group.equalsIgnoreCase("Moderator")
							|| group.equalsIgnoreCase("Regular")) {
						p.sendMessage("[AntiAlts2] " + ChatColor.GREEN + "|-- " + name + " : - : サブアカウント情報 --|");
						p.sendMessage("[AntiAlts2] " + ChatColor.GREEN + "このプレイヤーには、以下、" + equal_ips.size()
								+ "個のアカウントが見つかっています。");
						p.sendMessage("[AntiAlts2] " + ChatColor.GREEN + implode(equal_ips, ", "));
					}
				}
				Discord.send("223582668132974594", "__**[AntiAlts2]**__ `" + name + "` : - : サブアカウント情報\n"
						+ "このプレイヤーには、以下、" + equal_ips.size() + "個のアカウントが見つかっています。\n"
						+ implode(equal_ips, ", "));
			}

			// 10. 同一ドメインの非同一UUIDで、48h以内にラストログインしたプレイヤーをリスト化。管理部・モデレーターに出力
			PreparedStatement statement12 = MySQL.getNewPreparedStatement(
					"SELECT * FROM antialts WHERE domain = ? AND uuid != ? AND DATE_ADD(date, INTERVAL 2 DAY) > NOW()");
			statement12.setString(1, domain);
			statement12.setString(2, uuid.toString());
			ResultSet domains_res = statement12.executeQuery();
			Set<String> equal_domains = new HashSet<>();
			while (domains_res.next()) {
				String PlayerID = domains_res.getString("player");
				String PlayerUUID = domains_res.getString("uuid");
				if (uuid.toString().equalsIgnoreCase(PlayerUUID)) {
					continue;
				}
				equal_domains.add(PlayerID);
			}
			if (equal_domains.size() != 0) {
				for (Player p : Bukkit.getServer().getOnlinePlayers()) {
					String group = PermissionsManager.getPermissionMainGroup(p);
					if (group.equalsIgnoreCase("Admin") || group.equalsIgnoreCase("Moderator")) {
						p.sendMessage("[AntiAlts2] " + ChatColor.GREEN + "|-- " + name + " : - : 同一ドメイン情報 --|");
						p.sendMessage("[AntiAlts2] " + ChatColor.GREEN + "このプレイヤードメインと同一のプレイヤーが" + equal_domains.size()
								+ "個見つかっています。(DOMAIN: " + domain + ")");
						p.sendMessage("[AntiAlts2] " + ChatColor.GREEN + implode(equal_domains, ", "));
					}
				}
			}
		} catch (SQLException | ClassNotFoundException e) {
			AntiAlts2.report(e);
		}
	}

	/**
	 * リストの内容をStringにし、その間にglueを挟んで返す(PHPのimplodeと同等)
	 * @see https://qiita.com/rkowase/items/7e73468d421c16add76d
	 * @param list リスト
	 * @param glue 挟むテキスト
	 * @return 処理したテキスト
	 */
	public static <T> String implode(Set<T> list, String glue) {
		StringBuilder sb = new StringBuilder();
		for (T e : list) {
			sb.append(glue).append(e);
		}
		return sb.substring(glue.length());
	}
}