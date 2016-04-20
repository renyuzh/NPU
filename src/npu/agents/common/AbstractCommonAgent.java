package npu.agents.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import npu.agents.communication.utils.MessageCompressUtil;
import npu.agents.search.AStar;
import npu.agents.utils.ConfigUtil;
import rescuecore2.misc.Pair;
import rescuecore2.standard.components.StandardAgent;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.Blockade;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.Hydrant;
import rescuecore2.standard.entities.Refuge;
import rescuecore2.standard.entities.Road;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.worldmodel.EntityID;

public abstract class AbstractCommonAgent<E extends StandardEntity> extends StandardAgent<E> {
	protected static final int RANDOM_WALK_LENGTH = 50;
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
    
	protected Set<EntityID> areaIDs;
	protected Map<EntityID, Set<EntityID>> neighbours;

	private Map<EntityID, Set<EntityID>> entrancesOfRefuges;
	
	private List<StandardEntity> allBuildings;
	private List<StandardEntity> allRoads;
	private List<StandardEntity> allAreas;
	@Override
	protected void postConnect() {
		super.postConnect();
		buildingIDs = new HashSet<EntityID>();
		roadIDs = new HashSet<EntityID>();
		refuges = new HashSet<Refuge>();
		hydrantIDs = new HashSet<EntityID>();
		areaIDs = new HashSet<EntityID>();
		allBuildings =  new ArrayList<StandardEntity>();
		allRoads = new ArrayList<StandardEntity>();
		allAreas = new ArrayList<StandardEntity>();
		ArrayList<EntityID> test = new ArrayList<EntityID>();
		for (StandardEntity next : model) {
			if(next instanceof Area){
				areaIDs.add(next.getID());
				if(!(allAreas.contains(next)))
					allAreas.add(next);
			}
			if (next instanceof Building) {
				buildingIDs.add(next.getID());
				if(!allBuildings.contains(next))
					allBuildings.add(next);
			}
			if (next instanceof Road) {
				roadIDs.add(next.getID());
				if(!allRoads.contains(next))
				allRoads.add(next);
			}
			if (next instanceof Refuge) {
				refuges.add((Refuge) next);
			}
			if (next instanceof Hydrant) {
				hydrantIDs.add(next.getID());
			}
		}
		System.out.println(roadIDs.size());
		System.out.println(buildingIDs.size());
		search = new AStar(model);
		neighbours = search.getGraph();
		configuration = new ConfigUtil(config);
		final Comparator<StandardEntity> comp = new Comparator<StandardEntity>() {
			@Override
			public int compare(StandardEntity o1, StandardEntity o2) {
				if (o1.getID().getValue() < o2.getID().getValue()) {
					return -1;
				} else if (o1.getID().getValue() > o2.getID().getValue()) {
					return 1;
				}
				return 0;
			}
		};
		Collections.sort(allRoads,comp);
		Collections.sort(allBuildings,comp);
		Collections.sort(allAreas,comp);
		MessageCompressUtil.init(model, allRoads, allBuildings,allAreas);
	}
	protected List<EntityID> randomWalk(Set<EntityID> ids) {
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
				if (ids.contains(next)) {
					if (seen.contains(next)) {
						continue;
					}
					current = next;
					found = true;
				//	System.out.print("在该簇内的道路遍历");
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

	protected int getDistanceByPath(List<EntityID> path, Human me) {
		int distance = 0;
		Pair<Integer, Integer> start = new Pair(me.getX(), me.getY());
		for (EntityID id : path) {
			Pair<Integer, Integer> next = model.getEntity(id).getLocation(model);
			distance += (int) Math.hypot(next.first() - start.first(), next.second() - start.second());
			start = next;
		}
		return distance;
	}

	protected EntityID positionWhereIStuckedIn(Set<Blockade> seenBlockades, Human me) {
		Set<Blockade> blockades = new HashSet<Blockade>();
		for (Blockade blockade : seenBlockades) {
			ArrayList<Integer> X = new ArrayList<Integer>();
			ArrayList<Integer> Y = new ArrayList<Integer>();
			for (int i = 0; i < blockade.getApexes().length - 1; i = i + 2) {
				X.add(blockade.getApexes()[i]);
				Y.add(blockade.getApexes()[i + 1]);
			}
			if (in_or_out_of_polygon(X, Y, me.getX(), me.getY())) {
				System.out.println(me().getID()+" 我就这样被无情的困死了，救命啊");
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
	/*protected int getRoadIndex(EntityID id){
		return binarySearch(allRoads,model.getEntity(id));
	}
	protected int getBuildingIndex(EntityID id){
		return binarySearch(allBuildings,model.getEntity(id));
	}
	protected Road getRoadByIndex(int index){
		StandardEntity entity = allRoads.get(index);
		if(entity instanceof Road)
			return (Road)entity;
		return null;
	}
	protected Building getBuildingByIndex(int index){
		StandardEntity entity = allRoads.get(index);
		if(entity instanceof Road)
			return (Building)entity;
		return null;
	}
	private int binarySearch(List<StandardEntity> entities,
			StandardEntity standardEntity) {
		if (entities == null || entities.size() == 0)
			return standardEntity.getID().getValue();
		StandardEntity[] array = entities.toArray(new StandardEntity[entities
				.size()]);
		return binarySearchUtil(array, array.length,standardEntity);
	}
	int binarySearchUtil(StandardEntity[] a, int len, StandardEntity goal)
	{
	    int low = 0;
	    int high = len -1;
	    while (low <= high)
	    {
	        int middle = (high - low) / 2 + low; // 直接使用(high + low) / 2 可能导致溢出
	        if (a[middle].getID().getValue() == goal.getID().getValue())
	            return middle;
	        //在左半边
	        else if (a[middle].getID().getValue() > goal.getID().getValue())
	            high = middle - 1;
	        //在右半边
	        else
	            low = middle + 1;
	    }
	    //没找到
	    return -1;
	}*/
}
