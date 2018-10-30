package org.foo_projects.sofar.Botany;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;

import org.foo_projects.sofar.util.ChunkList.ChunkList;

import com.bekvon.bukkit.residence.Residence;
import com.bekvon.bukkit.residence.protection.ClaimedResidence;

import com.massivecraft.factions.entity.BoardColls;
import com.massivecraft.factions.entity.Faction;
import com.massivecraft.mcore.ps.PS;

import com.palmergames.bukkit.towny.object.TownyUniverse;

import com.sk89q.worldguard.bukkit.WGBukkit;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;

public final class Botany extends JavaPlugin {
	private long conf_blocks = 1;
	private int conf_ticks = 1;
	private double conf_density = 1.0;

	private boolean conf_saplings = true;
	private boolean conf_cacti = true;
	private boolean conf_trees = true;

	private boolean conf_protect = true;

	private final int conf_plants_csv_version = 2;

	private boolean have_factions = false;
	private boolean have_factions_old = false;
	private boolean have_towny = false;
	private boolean have_worldguard = false;
	private boolean have_residence = false;

	private boolean conf_factions = true;
	private boolean conf_towny = true;
	private boolean conf_worldguard = true;
	private boolean conf_residence = true;

	private Map<String, Long> stat_planted = new HashMap<String, Long>();

	private ChunkList chunkList;

	// main plant grow probability matrix - hashed over biome
	private class plantMatrix {
		public Material target_type;
		public byte     target_data;
		public Material base_type;
		public Material scan_type;
		public byte     scan_data;
		public double   density;
		public long     radius;

		public plantMatrix(Material tt, byte td, Material bt, Material st, byte sd, double d, long r) {
			target_type = tt;
			target_data = td;
			base_type = bt;
			scan_type = st;
			scan_data = sd;
			density = d;
			radius = r;
		}
	}

	// contains our biome - plant probability matrix
	private static Map<Biome, List<plantMatrix>> matrix;
	// used to fill our plant prob. matrix at startup

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

	@SuppressWarnings("deprecation")
	/* needed to make sure we don't think that a red_rose:9 is a different flower than :0 */
	private byte getSimpleData(Block b) {
		switch(b.getType()) {
		case LONG_GRASS:
			return (byte) (b.getData() % 3);
		case DOUBLE_PLANT:
			return (byte) (b.getData() % 8);
		case LEAVES:
			return (byte) (b.getData() % 4);
		case LEAVES_2:
			return (byte) (b.getData() % 2);
		case SAPLING:
			return (byte) (b.getData() % 6);
		case RED_ROSE:
			return (byte) (b.getData() % 9);
		default:
			return 0;
		}
	}

	@SuppressWarnings("deprecation")
	private void setData(Block b, byte d) {
		b.setData(d);
	}

	private boolean isProtected(Block block) {
		if (!conf_protect)
			return false;

		if (have_factions_old && conf_factions) {
			com.massivecraft.factions.Faction faction = com.massivecraft.factions.Board.getFactionAt(new com.massivecraft.factions.FLocation(block));
			if (!faction.isNone())
				return true;
		}
		if (have_factions && conf_factions) {
			Faction faction = BoardColls.get().getFactionAt(PS.valueOf(block.getLocation()));
			if (!faction.isNone())
				return true;
		}
		if (have_towny && conf_towny) {
			if (TownyUniverse.getTownBlock(block.getLocation()) != null)
				return true;
		}
		if (have_worldguard && conf_worldguard) {
			RegionManager rm = WGBukkit.getRegionManager(block.getWorld());
			if (rm == null)
				return false;
			ApplicableRegionSet set = rm.getApplicableRegions(block.getLocation());
			return (set.size() > 0);
		}
		if (have_residence && conf_residence) {
			ClaimedResidence res = Residence.getResidenceManager().getByLoc(block.getLocation());
			if (res != null)
				return true;
		}

		return false;
	}

	private void growAt(World world, int x, int z) {
		boolean canopy = false;
		BlockFace[] sides = { BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST };

		Block b = world.getHighestBlockAt(x, z);

		/* do not consider planting in chunks that have just recently been
		 * loaded, as this could flood the server with chunk load requests
		 * and cause massive amounts of chunks to be loaeded
		 */
		if (!chunkList.isLoadedLongerThan(b.getLocation(), 5L * 60L * 1000000000L))
			return;

		if (isProtected(b))
			return;

		// verify this block is empty
		if (b.getType() != Material.AIR)
			return;

		// do we plant in this biome?
		List<plantMatrix> pml;
		if (matrix.containsKey(b.getBiome())) {
			pml = matrix.get(b.getBiome());
		} else {
			if (!matrix.containsKey(BiomeReduce(b.getBiome())))
				return;
			pml = matrix.get(BiomeReduce(b.getBiome()));
		}

		/* randomize which plant get picked first */
		Collections.shuffle(pml);

		/* We can plant under existing trees */
		while ((b.getRelative(BlockFace.DOWN).getType() == Material.LEAVES) ||
				(b.getRelative(BlockFace.DOWN).getType() == Material.LEAVES_2) ||
						(b.getRelative(BlockFace.DOWN).getType() == Material.AIR)) {
			b = b.getRelative(BlockFace.DOWN);
			canopy = true;
		}
		
		/* do not replace leaves on the ground */
		if (canopy && b.getType() != Material.AIR) {
			return;
		}

nextplant:
		for (plantMatrix pm: pml) {
			long count = 0;
			long found = 0;

			// are these plant types enabled?
			if (pm.target_type == Material.SAPLING && (!conf_saplings))
				continue;
			if (pm.target_type == Material.CACTUS && (!conf_cacti))
				continue;

			/* Don't plant saplings underneath any form of canopy - this reduces the problem if
			 * having to measure space around saplings much as under an open sky it will be
			 * easier for the saplings to grow
			 * */
			if ((canopy) && (pm.target_type == Material.SAPLING))
				continue;

			Block base = b.getRelative(BlockFace.DOWN);

			// check if base is OK for this material
			if (base.getType() != pm.base_type)
				continue;

			/* if DOUBLE_PLANT, must have 2 air spaces */
			if (pm.target_type == Material.DOUBLE_PLANT)
				if (b.getRelative(BlockFace.UP).getType() != Material.AIR)
						continue nextplant;

			/* cactus planting test */
			if (pm.target_type == Material.CACTUS) {
				for (BlockFace f: sides) {
					if (b.getRelative(f).getType() != Material.AIR)
						continue nextplant;
				}
			}

			/* Sugar cane planting test */
			if (pm.target_type == Material.SUGAR_CANE_BLOCK){
				boolean wateredge = false;
				for (BlockFace f: sides) {
					if (base.getRelative(f).getType() == Material.WATER) {
						wateredge = true;
						break;
					}
				}
				if (!wateredge)
					continue;
			}

			/* lower plant density slowly by height - 25% each 16 blocks above sea level */
			double density = pm.density * Math.pow(0.75, ((b.getY() - b.getWorld().getSeaLevel()) / 16));
			long radius = (long)((double)pm.radius / Math.pow(0.75, ((b.getY() - b.getWorld().getSeaLevel()) / 16)));

			/* vary density by a small percentage each block to create some variation, otherwise
			 * we will create regular mathematical patterns that look horrible
			 */
			int variation = (((x * 19) + (z * 43)) % 16) - 8; /* ranges from -8 to 7 */
			double dv = ((2.0 * variation) / 100.0) + 1.0; /* 0.84 to 1.14 */

			// determine density of plant in radius
			for (long xx = b.getX() - radius; xx < b.getX() + radius; xx++) {
				for (long zz = b.getZ() - radius; zz < b.getZ() + radius; zz++) {
					Block h = world.getHighestBlockAt((int)xx, (int)zz);

					/* make sure we don't scan outside our biome */
					if (h.getBiome() != b.getBiome())
						continue;

					count++;

					/* don't scan the top air block */
					while (h.getType() == Material.AIR)
						h = h.getRelative(BlockFace.DOWN);

					/* if we're not scanning for leaves, lower scan to beneath any */
					if ((pm.scan_type != Material.LEAVES) && (pm.scan_type != Material.LEAVES_2)) {
						while (h.getType() == Material.LEAVES || h.getType() == Material.LEAVES_2 || h.getRelative(BlockFace.DOWN).getType() == Material.AIR)
							h = h.getRelative(BlockFace.DOWN);
					}

					/* cop-out - prevent planting saplings too soon after each other.
					 * If anywhere there is an ungrown sapling nearby, just don't plant
					 * another one nearby.
					 * There is a small risk that that sapling won't ever grow, so we
					 * may just want to figure out a way to break the cycle like PwnPlantgrowth does,
					 * which essentially kills saplings after a while (makes them dead bushes) */
					if ((pm.target_type == Material.SAPLING) && (h.getType() == Material.SAPLING) &&
							(pm.target_data == getSimpleData(h)))
						continue nextplant;

					if (((h.getType() == pm.scan_type) && (getSimpleData(h) == pm.scan_data)) ||
						((h.getType() == pm.target_type) && (getSimpleData(h) == pm.target_data)))
						found++;
				}
			}

			// The cast to double here is critical!
			if (((double)found / (double)count) < density * dv) {
				// plant the thing
				b.setType(pm.target_type);
				setData(b, pm.target_data);
				if (pm.target_type == Material.DOUBLE_PLANT) {
					Random rnd = new Random();
					Block tb = b.getRelative(BlockFace.UP);
					tb.setType(Material.DOUBLE_PLANT);
					/* top half seems to be (8 & orientation of planting) - make it random */
					setData(tb, (byte)(8 + rnd.nextInt(4)));
				}

				/* grow fullsize trees instead of saplings */
				if (conf_trees && (pm.target_type == Material.SAPLING)) {
					setData(b, (byte)0);
					b.setType(Material.AIR);
					TreeType tt = TreeType.TREE;
					switch(pm.target_data) {
					case (0):
						if (BiomeReduce(b.getBiome()) == Biome.SWAMPLAND) {
							tt = TreeType.SWAMP;
						} else {
							if (Math.random() > 0.25)
								tt = TreeType.TREE;
							else
								tt = TreeType.BIG_TREE;
						}
						break;
					case (1):
						if (pm.density < 0.25)
							tt = TreeType.REDWOOD;
						else
							tt = TreeType.TALL_REDWOOD;
						break;
					case (2):
						if (Math.random() > 0.25)
							tt = TreeType.BIRCH;
						else
							tt = TreeType.TALL_BIRCH;
						break;
					case (3):
						if (Math.random() > 0.5)
							tt = TreeType.JUNGLE_BUSH;
						else if (Math.random() > 0.5)
							tt = TreeType.SMALL_JUNGLE;
						else
							tt = TreeType.JUNGLE;
						break;
					case (4):
						tt = TreeType.ACACIA;
						break;
					case (5):
						tt = TreeType.DARK_OAK;
						break;
					default:
						break;
					}
					b.getWorld().generateTree(b.getLocation(), tt);
				} else if (pm.target_type == Material.SAPLING) {
					/* Do we need to plant the saplings in a 2x2 fashion? */
					if ((pm.target_data == 5) || ((pm.target_data == 3) && (Math.random() > 0.9))) {
						boolean planted_bigtree = false;
						/* try and find 3 spots next to this location to plant the same saplings */
						BlockFace[] s = {
								BlockFace.NORTH, BlockFace.NORTH_EAST, BlockFace.EAST,
								BlockFace.EAST, BlockFace.SOUTH_EAST, BlockFace.SOUTH,
								BlockFace.SOUTH, BlockFace.SOUTH_WEST, BlockFace.WEST,
								BlockFace.WEST, BlockFace.NORTH_WEST, BlockFace.NORTH,
								BlockFace.NORTH, BlockFace.NORTH_EAST, BlockFace.EAST,
								BlockFace.EAST, BlockFace.SOUTH_EAST, BlockFace.SOUTH,
								BlockFace.SOUTH, BlockFace.SOUTH_WEST, BlockFace.WEST
						};

						Random rnd = new Random();
						int start = rnd.nextInt(4) * 3;
						for (int c = start; c < start + 12; c += 3) {
							if ((b.getRelative(s[c]).getType() == Material.AIR) &&
								(b.getRelative(s[c]).getRelative(BlockFace.DOWN).getType() == pm.base_type) &&
								(b.getRelative(s[c + 1]).getType() == Material.AIR) &&
								(b.getRelative(s[c + 1]).getRelative(BlockFace.DOWN).getType() == pm.base_type) &&
								(b.getRelative(s[c + 2]).getType() == Material.AIR) &&
								(b.getRelative(s[c + 2]).getRelative(BlockFace.DOWN).getType() == pm.base_type)) {
									b.getRelative(s[c]).setType(pm.target_type);
									setData(b.getRelative(s[c]), pm.target_data);
									b.getRelative(s[c + 1]).setType(pm.target_type);
									setData(b.getRelative(s[c + 1]), pm.target_data);
									b.getRelative(s[c + 2]).setType(pm.target_type);
									setData(b.getRelative(s[c + 2]), pm.target_data);
									planted_bigtree = true;
									break;
								}
						}

						/* It's ok if jungle trees don't grow large, but remove any dark oak saplings */
						if ((!planted_bigtree) && (pm.target_data == 5)) {
							b.setType(Material.AIR);
							setData(base, (byte)0);
							/* don't increment counters */
							return;
						}
					}
				}

				if (stat_planted.get(pm.target_type.toString() + ":" + pm.target_data) == null)
					stat_planted.put(pm.target_type.toString() + ":" + pm.target_data, (long)1);
				else
					stat_planted.put(pm.target_type.toString() + ":" + pm.target_data, stat_planted.get(pm.target_type.toString() + ":" + pm.target_data) + 1);

				return;
			}
		}
	}

	private class BotanyRunnable implements Runnable {
		@Override
		public void run() {
			if (chunkList.isEmpty())
				return;
			for (World w: chunkList.listWorlds()) {
				for (int j = 0; j < conf_blocks; j++) {
					Chunk c = chunkList.getRandom(w);
					if (c == null)
						continue;
					Random rnd = new Random();
					int x = rnd.nextInt(16);
					int z = rnd.nextInt(16);
					growAt(c.getWorld(), c.getX() * 16 + x, c.getZ() * 16 + z);
				}
			}
		}
	}

	class BotanyCommand implements CommandExecutor {
		public boolean onCommand(CommandSender sender, Command command, String label, String[] split) {
			String msg = "unknown command";
			String helpmsg = "\n" + 
					"/botany help - display this help message\n" +
					"/botany stats - display statistics\n" +
					"/botany list - display enabled worlds\n" +
					"/botany blocks <int> - set number of block attempts per cycle\n" +
					"/botany enable <world> - enable for world\n" +
					"/botany disable <world> - enable for world\n" +
					"/botany scan [radius] - scan the area in your current biome for plants";

command:
			if (split.length >= 1) {
				switch (split[0]) {
					case "blocks":
						if (split.length == 2) {
							conf_blocks = Long.parseLong(split[1]);
							getConfig().set("blocks", conf_blocks);
							saveConfig();
						}
						msg = "number of blocks set to " + conf_blocks;
						break;
					case "test":
						if (split.length != 7) {
							msg = "test requires 6 parameters: world x1 z1 x2 z2 blocks";
							break;
						};
						for (World w: chunkList.listWorlds()) {
							if (w.getName().equals(split[1])) {
								int x1 = Integer.parseInt(split[2]);
								int z1 = Integer.parseInt(split[3]);
								int x2 = Integer.parseInt(split[4]);
								int z2 = Integer.parseInt(split[5]);
								if (x1 > x2) {
									int t = x1;
									x1 = x2;
									x2 = t;
								}
								if (z1 > z2) {
									int t = z1;
									z1 = z2;
									z2 = t;
								}
								for (long i = 0; i < Long.parseLong(split[6]); i++)
									for (int x = x1; x <= x2; x++)
										for (int z = z1; z <= z2; z++)
											growAt(w, x, z);
								msg = "test cycle finished";
								break command;
							}
						}
						msg = "Invalid world name - world must be enabled already";
						break;
					case "enable":
						if (split.length != 2) {
							msg = "enable requires a world name";
							break;
						};
						if (!chunkList.enableWorld(split[1])) {
							msg = "unable to enable for world \"" + split[1] + "\"";
						} else {
							msg = "enabled for world \"" + split[1] + "\"";
							List<String> worldStringList = getConfig().getStringList("worlds");
							if (worldStringList.indexOf(split[1]) == -1) {
								worldStringList.add(split[1]);
								getConfig().set("worlds", worldStringList);
								saveConfig();
							}
						}
						break;
					case "disable":
						if (split.length != 2) {
							msg = "disable requires a world name";
							break;
						};
						if (!chunkList.disableWorld(split[1])) {
							msg = "unable to disable for world \"" + split[1] + "\"";
						} else {
							msg = "disabled for world \"" + split[1] + "\"";
							List<String> worldStringList = getConfig().getStringList("worlds");
							worldStringList.remove(split[1]);
							getConfig().set("worlds", worldStringList);
							saveConfig();
						}
						break;
					case "list":
						msg = "plugin enabled for worlds:\n";
						if (chunkList.isEmpty()) {
							msg = "Not enabled for any worlds\n";
							break;
						}
						for (World w: chunkList.listWorlds())
							msg += "- " + w.getName() + "\n";
						break;
					case "stats":
						msg = "Planting statistics: ";
						msg += "(Chunks loaded: " + chunkList.size() + ")";
						for (String m: stat_planted.keySet())
							msg += "\n" + m + " - " + stat_planted.get(m);
						break;
					case "scan":
						if (!(sender instanceof Player)) {
							msg = "You must be a player to issue this command!\n";
							break;
						}

						int radius = 100;
						if (split.length == 2)
							radius = Math.max(8, Integer.parseInt(split[1]));

						Player player = (Player)sender;
						Block block = player.getLocation().getBlock();
						Biome biome = block.getBiome();
						World world = player.getWorld();
						long area = 0;
						Map <String,Long> plants = new HashMap<String,Long>();

						for (int x = block.getX() - radius; x < block.getX() + radius; x++) {
							for (int z = block.getZ() - radius; z < block.getZ() + radius; z++) {
								Block scan = world.getHighestBlockAt(x, z);
								if (!scan.getBiome().equals(biome))
									continue;
								if (scan.getType() == Material.AIR)
									scan = scan.getRelative(BlockFace.DOWN);

								area++;

								/* scan under foliage */
								if ((scan.getType() == Material.LEAVES) || (scan.getType() == Material.LEAVES_2)) {
									/* first, add this plant type to the scan results */
									Material mat = scan.getType();
									String name = mat.toString() + ":" + getSimpleData(scan);
									if (plants.get(name) != null)
										plants.put(name, plants.get(name) + 1);
									else
										plants.put(name, (long)1);

									/* second, scan further below */
									while (true) {
										Material m = scan.getRelative(BlockFace.DOWN).getType();
										if ((m == Material.AIR) || (m == Material.LEAVES) || (m == Material.LEAVES_2)) {
											scan = scan.getRelative(BlockFace.DOWN);
										} else {
											break;
										}
									}
								}

								Material mat = scan.getType();
								switch (mat) {
								case LONG_GRASS:
								case DOUBLE_PLANT:
								case CACTUS:
								case LEAVES:
								case LEAVES_2:
								case SAPLING:
								case RED_ROSE:
								case YELLOW_FLOWER:
								case DEAD_BUSH:
								case SUGAR_CANE_BLOCK:
								case WATER_LILY:
									String name = mat.toString() + ":" + getSimpleData(scan);
									if (plants.get(name) != null)
										plants.put(name, plants.get(name) + 1);
									else
										plants.put(name, (long)1);
									break;
								default:
									break;
								}
							}
						}
						msg = "Scan results:\n";
						msg += "Biome: " + biome.toString() + " Area: " + area + " range: " + radius;
						for (String name: plants.keySet())
							msg += "\n" + biome.toString() + "," + name + "," + plants.get(name) + "," + (String.format("%.3f",  (float)plants.get(name) / (float)area));
						break;
					case "help":
					default:
						msg = helpmsg;
						break;
				}
			} else {
				msg = helpmsg;
			}

			if (!(sender instanceof Player)) {
				getLogger().info(msg);
			} else {
				Player player = (Player) sender;
				player.sendMessage(msg);
			}
			return true;
		}
	}

	public void onEnable() {
		// config data handling
		saveDefaultConfig();

		// setup
		this.chunkList = new ChunkList(this);

		conf_blocks = getConfig().getInt("blocks");
		conf_ticks = getConfig().getInt("ticks");
		conf_density = getConfig().getDouble("density");

		conf_saplings = getConfig().getBoolean("saplings");
		conf_cacti = getConfig().getBoolean("cacti");
		conf_trees = getConfig().getBoolean("trees");

		conf_factions = getConfig().getBoolean("protect_factions");
		conf_towny = getConfig().getBoolean("protect_towny");
		conf_worldguard = getConfig().getBoolean("protect_worldguard");
		conf_residence = getConfig().getBoolean("protect_residence");

		getLogger().info("blocks: " + conf_blocks + " ticks: " + conf_ticks + " density: " + conf_density);

		List<String> worldStringList = getConfig().getStringList("worlds");

		/* populate chunk cache for each world */
		for (int i = 0; i < worldStringList.size(); i++)
			chunkList.enableWorld(worldStringList.get(i));

		/* Detect protection plugins like WG, factions, etc. */
		if (org.bukkit.Bukkit.getPluginManager().isPluginEnabled("Factions")) {
			try {
				/* this is an old API thing */
				new com.massivecraft.factions.FLocation();
			} catch (NoClassDefFoundError e) {
				have_factions = true;
			}
			if (!have_factions)
				have_factions_old = true;
		}

		if (org.bukkit.Bukkit.getPluginManager().isPluginEnabled("Towny"))
			have_towny = true;

		if (org.bukkit.Bukkit.getPluginManager().isPluginEnabled("WorldGuard"))
			have_worldguard = true;

		if (org.bukkit.Bukkit.getPluginManager().isPluginEnabled("Residence"))
			have_residence = true;

		getLogger().info("Protection plugins: " +
						(have_factions | have_factions_old ? "+" : "-") + "Factions, " +
						(have_towny ? "+" : "-") + "Towny, " +
						(have_worldguard ? "+" : "-") + "WorldGuard, " +
						(have_residence ? "+" : "-") + "Residence"
						);

		conf_protect = getConfig().getBoolean("protect");
		getLogger().info("protection is " + (conf_protect ? "on" : "off"));

		/* read plants.csv */
		matrix = new HashMap<Biome, List<plantMatrix>>();
		BufferedReader r;
		long linecount = 0;
		try {
			File cf = new File(getDataFolder(), "plants.csv");
			if (!cf.exists()) {
				saveResource("plants.csv", false);
				cf = new File(getDataFolder(), "plants.csv");
			}
			r = new BufferedReader(new FileReader(cf));
			long lineno = 0;
			while (true) {
				String line = r.readLine();
				lineno++;
				if (line == null)
					break;

				if (line.contains("#"))
					continue;

				String[] split = line.split(",");

				if (split.length == 1)
					continue;

				if ((split.length >= 2) && (split[0].equals("VERSION"))) {
					if (!split[1].equals(String.valueOf(conf_plants_csv_version))) {
						getLogger().info("The file \"plants.csv\" in the plugins folder is an outdated version");
						getLogger().info("Either remove the file and let the plugin install a new version for you, or");
						getLogger().info("fix it up manually.");
					}
					continue;
				}

				if (split.length != 5) {
					getLogger().info("Error parsing plants.csv at line " + lineno);
					continue;
				}

				Biome b;
				try {
					b = Biome.valueOf(split[0]);
				} catch (IllegalArgumentException e) {
					getLogger().info("Invalid Biome name \"" + split[0] + "\" at line " + lineno);
					continue;
				}

				String target[] = split[1].split(":");
				if (target.length != 2) {
					getLogger().info("Invalid target entry \"" + split[1] + "\" at line " + lineno);
					continue;
				}
				Material tt;
				byte td;
				try {
					tt = Material.valueOf(target[0]);
				} catch (IllegalArgumentException e) {
					getLogger().info("Invalid target Material name \"" + target[0] + "\" at line " + lineno);
					continue;
				}
				td = Byte.parseByte(target[1]);

				Material bt;
				try {
					bt = Material.valueOf(split[2]);
				} catch (IllegalArgumentException e) {
					getLogger().info("Invalid base Material name \"" + split[2] + "\" at line " + lineno);
					continue;
				}

				String scan[] = split[3].split(":");
				if (scan.length != 2) {
					getLogger().info("Invalid scan entry \"" + split[3] + "\" at line " + lineno);
					continue;
				}
				Material st;
				byte sd;
				try {
					st = Material.valueOf(scan[0]);
				} catch (IllegalArgumentException e) {
					getLogger().info("Invalid scan Material name \"" + scan[0] + "\" at line " + lineno);
					continue;
				}
				sd = Byte.parseByte(scan[1]);

				double d = Double.parseDouble(split[4]) * conf_density;
				if (d > 1.0)
					d = 1.0;

				/* finally, derive radius from density with a sane minimum */
				plantMatrix pm = new plantMatrix(tt, td, bt, st, sd, d,
						Math.max(8, (long)Math.sqrt(1 / Double.parseDouble(split[4])))
						);

				List<plantMatrix> pml;
				if (matrix.containsKey(b)) {
					pml = matrix.get(b);
				} else {
					pml = new ArrayList<plantMatrix>();
				}
				pml.add(pm);
				matrix.put(b, pml);

				linecount++;
			}
			r.close();
		} catch (FileNotFoundException e) {
			getLogger().info("Could not find a default or usable custom plants.csv! No plants will be planted!");
		} catch (IOException e) {
			getLogger().info("Error reading plants.csv data! No plants will be planted!");
		}
		getLogger().info("Read " + linecount + " plant entries from plants.csv");

		getCommand("botany").setExecutor(new BotanyCommand());

		// schedule our planter
		BukkitScheduler scheduler = Bukkit.getServer().getScheduler();
		scheduler.scheduleSyncRepeatingTask(this, new BotanyRunnable(), 1L, conf_ticks);
	}
}
