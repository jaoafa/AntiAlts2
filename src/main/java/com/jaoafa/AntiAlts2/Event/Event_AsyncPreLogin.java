package com.jaoafa.AntiAlts2.Event;

import java.sql.SQLException;
import java.sql.Statement;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;

import com.jaoafa.AntiAlts2.AntiAlts2;
import com.jaoafa.AntiAlts2.MySQL;
public class Event_AsyncPreLogin implements Listener {
	JavaPlugin plugin;
	public Event_AsyncPreLogin(JavaPlugin plugin) {
		this.plugin = plugin;
	}
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onAsyncPreLogin(AsyncPlayerPreLoginEvent event){
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
				return;
			}
		} catch (SQLException e) {
			// TODO 自動生成された catch ブロック
			e.printStackTrace();
			return;
		}
		
		statement = MySQL.check(statement);
		
		try {
			statement.executeQuery("");
		} catch (SQLException e) {
			// TODO 自動生成された catch ブロック
			e.printStackTrace();
		}
	}
}