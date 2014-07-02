package org.foo_projects.sofar.util.ChunkList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.plugin.Plugin;

public class ChunkList {
	private List<World> worldList;
	private Map<String, Long> chunkMap;
	private Plugin plugin;
	private Listener listener;

	public ChunkList(Plugin plugin) {
		this.chunkMap = new HashMap<String, Long>();
		this.worldList = new ArrayList<World>();
		this.plugin = plugin;
	}

	private void load(Chunk c) {
		if (!worldList.contains(c.getWorld()))
			return;
		String key = "(" + c.getWorld().getName() + ":" + c.getX() + "," + c.getZ() + ")";
		chunkMap.put(key, System.nanoTime());
	}

	private void unload(Chunk c) {
		if (!worldList.contains(c.getWorld()))
			return;
		String key = "(" + c.getWorld().getName() + ":" + c.getX() + "," + c.getZ() + ")";
		if (chunkMap.containsKey(key))
			chunkMap.remove(key);
	}

	private void unloadAll(World w) {
		String key = "(" + w.getName() + ":";
		Iterator<String> i = chunkMap.keySet().iterator();
		while (i.hasNext()) {
			String entry = i.next();
			if (entry.startsWith(key))
				i.remove();
		}
	}

	public boolean enableWorld(World w) {
		if (worldList.contains(w))
			return false;

		if (worldList.isEmpty()) {
			this.listener = new ChunkListener();
			Bukkit.getServer().getPluginManager().registerEvents(listener, plugin);
		}

		if (worldList.add(w)) {
			Chunk chunkList[] = w.getLoadedChunks();
			for (Chunk c: chunkList)
				this.load(c);
			return true;
		}
		return false;
	}

	public boolean enableWorld(String s) {
		World w = Bukkit.getWorld(s);
		if (w == null)
			return false;
		return enableWorld(w);
	}

	public boolean disableWorld(World w) {
		worldList.remove(w);

		if (worldList.isEmpty()) {
			HandlerList.unregisterAll(listener);
			this.listener = null;
		}

		unloadAll(w);

		return true;
	}

	public boolean disableWorld(String s) {
		World w  = Bukkit.getWorld(s);
		if (w == null)
			return false;
		return disableWorld(w);
	}

	public Boolean isEnabled(World w) {
		return worldList.contains(w);
	}

	public List<World> listWorlds() {
		if (worldList.isEmpty())
			return null;
		return worldList;
	}

	public boolean isEmpty() {
		return worldList.isEmpty();
	}

	public long size() {
		long size = 0;
		for (World w: worldList)
			size += w.getLoadedChunks().length;
		return size;
	}

	public Boolean isLoaded(Chunk c) {
		String key = "(" + c.getWorld().getName() + ":" + c.getX() + "," + c.getZ() + ")";
		return chunkMap.containsKey(key);
	}

	public Boolean isLoaded(Location l) {
		return isLoaded(l.getChunk());
	}

	public Boolean isLoadedLongerThan(Chunk c, long nanosec) {
		String key = "(" + c.getWorld().getName() + ":" + c.getX() + "," + c.getZ() + ")";
		if (chunkMap.containsKey(key))
			return (System.nanoTime() - chunkMap.get(key) > nanosec);
		return false;
	}

	public Boolean isLoadedLongerThan(Location l, long nanosec) {
		return isLoadedLongerThan(l.getChunk(), nanosec);
	}

	public Chunk getRandom(World w) {
		if (!isEnabled(w))
			return null;
		
		int n = w.getLoadedChunks().length;
		if (n <= 0)
			return null;

		Random rnd = new Random();
		return w.getLoadedChunks()[rnd.nextInt(n)];
	}

	private class ChunkListener implements Listener {
		@EventHandler
		public void onChunkLoadEvent(ChunkLoadEvent event) {
			load(event.getChunk());
		}

		@EventHandler
		public void onChunkUnloadEvent(ChunkUnloadEvent event) {
			unload(event.getChunk());
		}
	}
}
