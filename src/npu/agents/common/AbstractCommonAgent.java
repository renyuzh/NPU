package npu.agents.common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import npu.agents.clustering.Cluster;
import npu.agents.clustering.ClustingMap;
import npu.agents.communication.utils.ChangeSetUtil;
import npu.agents.communication.utils.ConfigUtil;
import npu.agents.pf.strategy.ClearUtil;
import npu.agents.search.AStar;
import npu.agents.utils.KConstants;
import npu.agents.utils.Point;
//import npu.agents.utils.SampleSearch;
import rescuecore2.config.Config;
import rescuecore2.connection.Connection;
import rescuecore2.messages.Command;
import rescuecore2.misc.Pair;
import rescuecore2.standard.components.StandardAgent;
import rescuecore2.standard.entities.Blockade;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.Hydrant;
import rescuecore2.standard.entities.Refuge;
import rescuecore2.standard.entities.Road;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.ChangeSet;
import rescuecore2.worldmodel.Entity;
import rescuecore2.worldmodel.EntityID;

public abstract class AbstractCommonAgent<E extends StandardEntity> extends StandardAgent<E> {
	private static final int RANDOM_WALK_LENGTH = 50;
	protected ConfigUtil configuration;
	/**
	 * The search algorithm.
	 */
	protected AStar search;
	/**
	 * Cache of building IDs.
	 */
	protected Set<EntityID> buildingIDs;

	/**
	 * Cache of road IDs.
	 */
	protected Set<EntityID> roadIDs;

	/**
	 * Cache of refuge
	 */
	protected Set<Refuge> refuges;
	/**
	 * Cache of hydrant IDs
	 */
	protected Set<EntityID> hydrantIDs;

	private Map<EntityID, Set<EntityID>> neighbours;

	protected ClearUtil clearUtil;
	protected ChangeSetUtil seenChanges;

	private Map<EntityID, Set<EntityID>> entrancesOfRefuges;

	@Override
	protected void postConnect() {
		super.postConnect();
		buildingIDs = new HashSet<EntityID>();
		roadIDs = new HashSet<EntityID>();
		refuges = new HashSet<Refuge>();
		hydrantIDs = new HashSet<EntityID>();
		ArrayList<EntityID> test = new ArrayList<EntityID>();
		for (StandardEntity next : model) {
			if (next instanceof Building) {
				buildingIDs.add(next.getID());
				test.add(next.getID());
			}
			if (next instanceof Road) {
				roadIDs.add(next.getID());
			}
			if (next instanceof Refuge) {
				refuges.add((Refuge) next);
			}
			if (next instanceof Hydrant) {
				hydrantIDs.add(next.getID());
			}
		}
		search = new AStar(model);
		neighbours = search.getGraph();
		configuration = new ConfigUtil(config);
		clearUtil = new ClearUtil();
		seenChanges = new ChangeSetUtil();
	}

	protected List<EntityID> randomWalkAroundCluster(Set<EntityID> roadsID) {
		List<EntityID> result = new ArrayList<EntityID>(RANDOM_WALK_LENGTH);
		Set<EntityID> seen = new HashSet<EntityID>();
		EntityID current = ((Human) me()).getPosition();
		for (int i = 0; i < RANDOM_WALK_LENGTH; ++i) {
			result.add(current);
			seen.add(current);
			List<EntityID> possible = new ArrayList<EntityID>(neighbours.get(current));
			Collections.shuffle(possible, random);
			boolean found = false;
			for (EntityID next : possible) {
				// 警察和消防只能在该簇内的道路上遍历，医生都要遍历
				if (roadsID != null && roadsID.contains(next)) {
					if (seen.contains(next)) {
						continue;
					}
					current = next;
					found = true;
					System.out.print("在该簇内的道路遍历");
					break;
				}
				if (roadsID == null) {
					if (seen.contains(next)) {
						continue;
					}
					current = next;
					found = true;
					System.out.print("在该簇内的道路遍历");
					break;
				}
			}
			if (!found) {
				break;
			}
		}
		return result;
	}

	protected Map<EntityID, Set<EntityID>> getEntrancesOfRefuges() {
		Set<EntityID> roads = new HashSet<EntityID>();
		EntityID refugeID = null;
		for (Refuge refuge : refuges) {
			for (EntityID id : refuge.getNeighbours()) {
				StandardEntity nearEntity = model.getEntity(id);
				if (nearEntity instanceof Road) {
					roads.add(nearEntity.getID());
					refugeID = refuge.getID();
				}
			}
			if (refugeID != null) {
				entrancesOfRefuges.put(refugeID, roads);
				refugeID = null;
				roads.clear();
			}
		}
		return entrancesOfRefuges;
	}

	protected void distanceSort(List<EntityID> targets, final Human me) {
		Collections.sort(targets, new Comparator<EntityID>() {
			@Override
			public int compare(EntityID o1, EntityID o2) {
				int distance1 = findDistanceTo(o1, me.getX(), me.getY());
				int distance2 = findDistanceTo(o2, me.getX(), me.getY());
				if (distance1 > distance2)
					return 1;
				if (distance1 < distance2)
					return -1;
				return 0;
			}
		});
	}

	protected int findDistanceTo(EntityID id, int x, int y) {
		StandardEntity entity = model.getEntity(id);
		Pair<Integer, Integer> pair = entity.getLocation(model);
		return (int) Math.hypot(pair.first() - x, pair.second() - y);
	}

	protected EntityID positionMeStuckedIn(Set<Blockade> seenBlockades, Human me) {
		Set<Blockade> blockades = new HashSet<Blockade>();
		for (Blockade blockade : seenBlockades) {
			ArrayList<Integer> X = new ArrayList<Integer>();
			ArrayList<Integer> Y = new ArrayList<Integer>();
			for (int i = 0; i < blockade.getApexes().length - 1; i = i + 2) {
				X.add(blockade.getApexes()[i]);
				Y.add(blockade.getApexes()[i + 1]);
			}
			if (in_or_out_of_polygon(X, Y, me.getX(), me.getY())) {
				return blockade.getPosition();
			}
		}
		return null;
	}

	public boolean in_or_out_of_polygon(ArrayList<Integer> X, ArrayList<Integer> Y, int x, int y) {
		int i, j;
		boolean c = false;
		for (i = 0, j = X.size() - 1; i < X.size(); j = i++) {
			if ((((Y.get(i) <= y) && (y < Y.get(j))) || ((Y.get(j) <= y) && (y < Y.get(i))))
					&& (x < (X.get(j) - X.get(i)) * (y - Y.get(i)) / (Y.get(j) - Y.get(i)) + X.get(i)))
				c = !c;
		}
		return c;
	}
}
