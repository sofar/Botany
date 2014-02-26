package org.foo_projects.sofar.Botany;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.foo_projects.sofar.util.ChunkList.ChunkList;

public final class Botany extends JavaPlugin {
	private int conf_ticks = 20;
	private int conf_blocks = 100;
	private ChunkList chunkList;
	
	// main plant grow probability matrix - hashed over biome
	private class plantMatrix {
		public Material target_type;
		public byte     target_data;
		public Material base_type;
		public byte     base_data;
		public Material scan_type;
		public byte     scan_data;
		public double   density;
		public long     radius;

		public plantMatrix(Material tt, byte td, Material bt, byte bd, Material st, byte sd, double d, long r) {
			this.target_type = tt;
			this.target_data = td;
			this.base_type = bt;
			this.base_data = bd;
			this.scan_type = st;
			this.scan_data = sd;
			this.density = d;
			this.radius = r;
		}
	}

	// contains our biome - plant probability matrix
	private static Map<Biome, List<plantMatrix>> matrix = new HashMap<Biome, List<plantMatrix>>();
	// used to fill our plant prob. matrix at startup

	private void mapadd(Biome biome, Material tt, byte td, Material bt, byte bd, Material st, byte sd, double d, long r) {
		plantMatrix pm = new plantMatrix(tt, td, bt, bd, st, sd, d, r);
		List<plantMatrix> pml;
		if (matrix.containsKey(biome)) {
			pml = matrix.get(biome);
		} else {
			pml = new ArrayList<plantMatrix>();
		}
		pml.add(pm);
		matrix.put(biome, pml);
	}
	
	private Biome BiomeReduce(Biome b) {
		switch (b) {
		case SWAMPLAND:
		case SWAMPLAND_MOUNTAINS:
			return Biome.SWAMPLAND;
		case FOREST:
		case FOREST_HILLS:
			return Biome.FOREST;
		case BIRCH_FOREST:
		case BIRCH_FOREST_HILLS:
		case BIRCH_FOREST_MOUNTAINS:
		case BIRCH_FOREST_HILLS_MOUNTAINS:
			return Biome.BIRCH_FOREST;
		case TAIGA:
		case TAIGA_MOUNTAINS:
		case COLD_TAIGA:
		case COLD_TAIGA_HILLS:
		case MEGA_TAIGA:
		case MEGA_TAIGA_HILLS:
		case TAIGA_HILLS:
		case COLD_TAIGA_MOUNTAINS:
		case MEGA_SPRUCE_TAIGA:
		case MEGA_SPRUCE_TAIGA_HILLS:
			return Biome.TAIGA;
		case JUNGLE:
		case JUNGLE_HILLS:
		case JUNGLE_EDGE:
		case JUNGLE_MOUNTAINS:
		case JUNGLE_EDGE_MOUNTAINS:
			return Biome.JUNGLE;
		case SAVANNA:
		case SAVANNA_PLATEAU:
		case SAVANNA_MOUNTAINS:
		case SAVANNA_PLATEAU_MOUNTAINS:
			return Biome.SAVANNA;
		case MESA:
		case MESA_PLATEAU_FOREST:
		case MESA_PLATEAU:
		case MESA_BRYCE:
		case MESA_PLATEAU_FOREST_MOUNTAINS:
		case MESA_PLATEAU_MOUNTAINS:
			return Biome.MESA;
		case DESERT:
		case DESERT_HILLS:
		case DESERT_MOUNTAINS:
			return Biome.DESERT;
		case BEACH:
		case STONE_BEACH:
		case COLD_BEACH:
			return Biome.BEACH;
		case OCEAN:
		case DEEP_OCEAN:
		case FROZEN_OCEAN:
			return Biome.OCEAN;
		case RIVER:
		case FROZEN_RIVER:
			return Biome.RIVER;
		case SMALL_MOUNTAINS:
		case EXTREME_HILLS:
		case EXTREME_HILLS_PLUS:
		case EXTREME_HILLS_MOUNTAINS:
		case EXTREME_HILLS_PLUS_MOUNTAINS:
			return Biome.EXTREME_HILLS;
		case ROOFED_FOREST:
		case ROOFED_FOREST_MOUNTAINS:
			return Biome.ROOFED_FOREST;
		case ICE_PLAINS:
		case ICE_MOUNTAINS:
			return Biome.ICE_PLAINS;
		case MUSHROOM_ISLAND:
		case MUSHROOM_SHORE:
			return Biome.MUSHROOM_ISLAND;

		case PLAINS:
		case HELL:
		case SKY:
		case SUNFLOWER_PLAINS:
		case FLOWER_FOREST:
		case ICE_PLAINS_SPIKES:

			return b;
		}
		return b;
	}
	
	private void growAt(World world, int x, int z) {
		Block b = world.getHighestBlockAt(x, z);

		// verify this block is empty
		if (b.getType() != Material.AIR)
			return;

		// do we plant in this biome?
		if (!matrix.containsKey(BiomeReduce(b.getBiome())))
			return;

		List<plantMatrix> pml = matrix.get(BiomeReduce(b.getBiome()));

		for (plantMatrix pm: pml) {
			int count = 0;

			Block base = b.getRelative(BlockFace.DOWN);

			// check if base is OK for this material
			if ((base.getType() != pm.base_type) || (base.getData() != pm.base_data))
				continue;

			// determine density of plant in radius
			for (long xx = b.getX() - pm.radius; xx < b.getX() + pm.radius; xx++) {
				for (long zz = b.getZ() - pm.radius; zz < b.getZ() + pm.radius; zz++) {
					Block h = world.getHighestBlockAt((int)xx, (int)zz);
					if (h.getType() == Material.AIR)
						h = h.getRelative(BlockFace.DOWN);

					if (((h.getType() == pm.scan_type) && (h.getData() == pm.scan_data)) ||
						((h.getType() == pm.target_type) && (h.getData() == pm.target_data)))
						count++;
				}
			}

			
			if (((double)count / (pm.radius * pm.radius)) < pm.density) {
				// plant the thing
				b.setType(pm.target_type);
				b.setData(pm.target_data);
				getLogger().info("count " + count + " density " + ((double)count / (pm.radius * pm.radius)));
				getLogger().info("In a " + b.getBiome().toString() + " biome, planted a " + pm.target_type.toString() + ":" + pm.target_data + " at " + b.getX() + "," + b.getY() + "," + b.getZ());
				return;
			}
		}
	}
	
	private class BotanyRunnable implements Runnable {
		@Override
		public void run() {
			for (World w: chunkList.listWorlds()) {
				for (int j = 0; j < conf_blocks; j++) {
					Chunk c = chunkList.getRandom(w);
					Random rnd = new Random();
					int x = rnd.nextInt(16);
					int z = rnd.nextInt(16);
					growAt(c.getWorld(), c.getX() * 16 + x, c.getZ() * 16 + z);
				}
			}
		}
	}
	
	public void onEnable() {
		// setup
		this.chunkList = new ChunkList(this);
		// test - "world" only for now
		chunkList.enableWorld(Bukkit.getWorld("world"));

		// long array of plants for each biome
		mapadd(Biome.BEACH, Material.LONG_GRASS, (byte)1, Material.GRASS, (byte)0, Material.LONG_GRASS, (byte)1, 0.01, 16);
		
		mapadd(Biome.SAVANNA, Material.LONG_GRASS, (byte)1, Material.GRASS, (byte)0, Material.LONG_GRASS, (byte)1, 0.6, 8);
		mapadd(Biome.SAVANNA, Material.SAPLING, (byte)4, Material.GRASS, (byte)0, Material.LEAVES_2, (byte)0, 0.1, 32);
		
		mapadd(Biome.PLAINS, Material.LONG_GRASS, (byte)1, Material.GRASS, (byte)0, Material.LONG_GRASS, (byte)1, 0.6, 8);
		mapadd(Biome.PLAINS, Material.YELLOW_FLOWER, (byte)0, Material.GRASS, (byte)0, Material.YELLOW_FLOWER, (byte)0, 0.01, 16);
		mapadd(Biome.PLAINS, Material.RED_ROSE, (byte)0, Material.GRASS, (byte)0, Material.RED_ROSE, (byte)0, 0.01, 16);
		
		mapadd(Biome.DESERT, Material.CACTUS, (byte)0, Material.SAND, (byte)0, Material.CACTUS, (byte)0, 0.0004, 32);
		mapadd(Biome.DESERT, Material.LONG_GRASS, (byte)0, Material.SAND, (byte)0, Material.LONG_GRASS, (byte)0, 0.0004, 32);

		// schedule our planter
		BukkitScheduler scheduler = Bukkit.getServer().getScheduler();
		scheduler.scheduleSyncRepeatingTask(this, new BotanyRunnable(), 1L, conf_ticks);
	}
}
