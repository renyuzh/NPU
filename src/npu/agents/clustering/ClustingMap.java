package npu.agents.clustering;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import npu.agents.utils.Point;
import rescuecore2.log.Logger;
import rescuecore2.misc.Pair;
import rescuecore2.misc.geometry.GeometryTools2D;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.Refuge;
import rescuecore2.standard.entities.Road;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.standard.entities.StandardWorldModel;
import rescuecore2.worldmodel.EntityID;

public class ClustingMap {
	private static List<Cluster> clusters;
	public static boolean hasCompute = false;
	private static Map<EntityID, Set<EntityID>> buildingEntrances = new HashMap<EntityID, Set<EntityID>>();

	/**
	 * 将给定地图的建筑物聚类，并设置每簇周围的道路
	 * 
	 * @param k
	 * @param iterTimes
	 * @param model
	 */
	public  static synchronized void initMap(int k, int iterTimes, StandardWorldModel model) {
		if (!hasCompute) {
			buildingEntrances.clear();
			clustingBuildings(k, iterTimes, model);
			setRoadsInCluster(model);
			setRefugesAndBuildingsEntrances(model);
			hasCompute = true;
		}
	}

	private  static synchronized void clustingBuildings(int k, int iterTimes, StandardWorldModel model) {
		System.out.println("clustingBuildings");
		List<Point> allPoints = new ArrayList<Point>();
		Collection<StandardEntity> buildings = model.getEntitiesOfType(StandardEntityURN.BUILDING,
				StandardEntityURN.REFUGE, StandardEntityURN.POLICE_OFFICE, StandardEntityURN.AMBULANCE_CENTRE,
				StandardEntityURN.FIRE_STATION, StandardEntityURN.GAS_STATION);
		for (StandardEntity next : buildings) {
			Building building = (Building) next;
			Point point = new Point(building.getX(), building.getY());
			point.setId(building.getID());
			if (!allPoints.contains(point)) {
				allPoints.add(point);
				// setBuildingsEntrances(building, model);
			}
		}
		clusters = new KMeans().getClusters(k, iterTimes, allPoints);;
	}

	private  static synchronized void setRoadsInCluster(StandardWorldModel model) {
		System.out.println("setRoadsInCluster");
		for (StandardEntity road : model.getEntitiesOfType(StandardEntityURN.ROAD)) {
			int MIN_DISTANCE = Integer.MAX_VALUE;
			int j = new Random(clusters.size()).nextInt();
			for (int i = 0; i < clusters.size(); i++) {
				Cluster cluster = clusters.get(i);
				Point center = cluster.getCenterPoint();
				Pair<Integer, Integer> next = road.getLocation(model);
				int distance = (int) Math.hypot(next.first() - center.getX(), next.second() - center.getY());
				if (distance < MIN_DISTANCE) {
					MIN_DISTANCE = distance;
					j = i;
				}
			}
			clusters.get(j).setRoadsInCluster(road.getID());
		}
	}

	private  static synchronized Set<EntityID> getRoadAroundBuilding(Building building, StandardWorldModel model) {
		Set<EntityID> roads = new HashSet<EntityID>();
		for (EntityID id : building.getNeighbours()) {
			StandardEntity entity = model.getEntity(id);
			if (entity instanceof Road) {
				roads.add(id);
			}
		}
		return roads;
	}

	private  static synchronized void setBuildingsEntrances(Building building, StandardWorldModel model) {
		Set<EntityID> roadsIDs = getRoadAroundBuilding(building, model);
		if (!buildingEntrances.keySet().contains(building.getID()))
			buildingEntrances.put(building.getID(), roadsIDs);
	}

	public  static synchronized Map<EntityID, Set<EntityID>> getBuildingEntrances() {
		return buildingEntrances;
	}

	private  static synchronized void setRefugesAndBuildingsEntrances(StandardWorldModel model) {
		Set<EntityID> templist = new HashSet<EntityID>();
		EntityID entityID = null;
		for (Cluster cluster : clusters) {
			for (EntityID entity : cluster.getBuildingsIDs()) {
				StandardEntity standard = model.getEntity(entity);
				if (standard instanceof Refuge) {
					Refuge refuge = (Refuge) standard;
					for (EntityID id : refuge.getNeighbours()) {
						StandardEntity nearEntity = model.getEntity(id);
						if (nearEntity instanceof Road) {
							templist.add(nearEntity.getID());
							entityID = refuge.getID();
						}
					}
					if (entityID != null) {
						cluster.setRoadAroudRefuge(entityID, templist);
						System.out.println("set roads of refuge in cluster for " + entityID);
						entityID = null;
						templist.clear();
					}
				} else {
					Building building = (Building) standard;
					for (EntityID id : building.getNeighbours()) {
						StandardEntity nearEntity = model.getEntity(id);
						if (nearEntity instanceof Road) {
							templist.add(nearEntity.getID());
							entityID = building.getID();
						}
					}
					if (entityID != null) {
						cluster.setBuildingsEntrances(entityID, templist);
						System.out.println("set roads of building in cluster of " +cluster.getCenterPoint().getId()+ "for "+entityID);
						entityID = null;
						templist.clear();
					}
				}
			}
		}
	}

	/**
	 * 将Agent分给指定的簇
	 * 
	 * @param human
	 * @param model
	 * @throws Exception
	 *             地图聚类失败
	 */
	public  static synchronized Cluster assignAgentToCluster(Human human, StandardWorldModel model) throws Exception {
		if (clusters == null) {
			throw new Exception();
		}
		System.out.println("assignAgentToCluster");
		Double minDistance = Double.MAX_VALUE;
		int j = -1;
		Point source = new Point(human.getX(), human.getY());
		System.out.println(clusters.size()+" pf of "+human.getID());
		for (int i = 0; i < clusters.size(); i++) {
			if (clusters.get(i).getAgentID() != null){
				System.out.println(human.getID()+" of pf:" +clusters.get(i).getAgentID());
				continue;
			}else{
				System.out.println(human.getID()+" of pf: cluster has no agent" );
			}
			Double distance = GeometryTools2D.getDistance(source, clusters.get(i).getCenterPoint());
			if (distance < minDistance) {
				minDistance = distance;
				j = i;
			}
		}
		if (j != -1) {
			clusters.get(j).setAgentID(human.getID());
			return clusters.get(j);
		}
		System.out.println(j+"oh shit of pf of "+human.getID());
		return null;
	}

	public  static synchronized Cluster getClusterByAgentID(EntityID id) {
		for (int i = 0; i < clusters.size(); i++) {
			Cluster cluster = clusters.get(i);
			if (cluster.getAgentID() != null && cluster.getAgentID().equals(id)) {
				return cluster;
			}
		}
		return null;
	}

	public  static synchronized List<Cluster> getClusters() {
		return clusters;
	}
}
