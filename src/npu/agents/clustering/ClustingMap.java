package npu.agents.clustering;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import npu.agents.utils.Point;
import rescuecore2.log.Logger;
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
	private static boolean hasCompute = false;

	/**
	 * 将给定地图的建筑物聚类，并设置每簇周围的道路
	 * 
	 * @param k
	 * @param iterTimes
	 * @param model
	 */
	public static void initMap(int k, int iterTimes, StandardWorldModel model) {
		if (!hasCompute) {
			clustingBuildings(k, iterTimes, model);
			setRoadAroundCluster(model);
			setRoadAroundRefugesInCluster(model);
			hasCompute = true;
		}
	}

	private static void clustingBuildings(int k, int iterTimes, StandardWorldModel model) {
		Logger.info("ClusteringBuidings");
		List<Point> allPoints = new ArrayList<Point>();
		Collection<StandardEntity> buildings = model.getEntitiesOfType(StandardEntityURN.BUILDING,
				StandardEntityURN.REFUGE);
		for (StandardEntity next : buildings) {
			Building building = (Building) next;
			Point point = new Point(building.getX(), building.getY());
			point.setId(building.getID());
			/*
			 * point.setCanPass(false); Set<Point> neighbours =
			 * getNeighbours(building,model); point.setNeighbours(neighbours);
			 */
			if(!allPoints.contains(point))
				allPoints.add(point);
		}
		clusters = KMeans.getClusters(k, iterTimes, allPoints);
	}

	private static void setRoadAroundCluster(StandardWorldModel model) {
		Logger.info("setRoadAroundCluster");
		for (Cluster cluster : clusters) {
			Set<EntityID> roads = new HashSet<EntityID>();
			List<Point> members = cluster.getMembers();
			for (Point member : members) {
				StandardEntity entity = model.getEntity(member.getId());
				Set<EntityID> r = getRoadAroundBuilding((Building) entity, model);
				roads.addAll(r);
			}
			/*
			 * Iterator<EntityID> iter = roads.iterator(); double minDistance =
			 * Double.MAX_VALUE; Road closest = null; while(iter.hasNext()) {
			 * EntityID r = iter.next(); model.getEntity(r) double distance =
			 * GeometryTools2D.getDistance(cluster.getCenterPoint(),new
			 * Point(r.getX(),r.getY())); if(distance < minDistance) {
			 * minDistance = distance; closest = r; } }
			 */
			cluster.setRoadsAroundCluster(roads);
			// cluster.setClosestRoadAroundCenter(closest);
		}
	}

	private static void setRoadAroundRefugesInCluster(StandardWorldModel model) {
		Set<EntityID> roads = new HashSet<EntityID>();
		EntityID refugeID = null;
		for (Cluster cluster : clusters) {
			List<Point> members = cluster.getMembers();
			for (Point member : members) {
				StandardEntity entity = model.getEntity(member.getId());
				if (entity instanceof Refuge) {
					Refuge refuge = (Refuge) entity;
					for (EntityID id : refuge.getNeighbours()) {
						StandardEntity nearEntity = model.getEntity(id);
						if (nearEntity instanceof Road) {
							roads.add(nearEntity.getID());
							refugeID = refuge.getID();
						}
					}
				}
				if (refugeID != null) {
					cluster.setRoadAroudRefuge(refugeID, roads);
					refugeID = null;
					roads.clear();
				}
			}
		}
	}

	private static Set<EntityID> getRoadAroundBuilding(Building building, StandardWorldModel model) {
		Set<EntityID> roads = new HashSet<EntityID>();
		for (EntityID id : building.getNeighbours()) {
			StandardEntity entity = model.getEntity(id);
			if (entity instanceof Road) {
				roads.add(id);
			}
		}
		return roads;
	}

	/**
	 * 将Agent分给指定的簇
	 * 
	 * @param human
	 * @param model
	 * @throws Exception
	 *             地图聚类失败
	 */
	public static Cluster assignAgentToCluster(Human human, StandardWorldModel model) throws Exception {
		if (clusters == null) {
			throw new Exception();
		}
		System.out.println("assignAgentToCluster");
		Double minDistance = Double.MAX_VALUE;
		int j = -1;
		Point source = new Point(human.getX(), human.getY());
		for (int i = 0; i < clusters.size(); i++) {
			if (clusters.get(i).getAgentID() != null)
				continue;
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
		return null;
	}

	public static Cluster getClusterByAgentID(EntityID id) {
		for (int i = 0; i < clusters.size(); i++) {
			Cluster cluster = clusters.get(i);
			if (cluster.getAgentID() != null && cluster.getAgentID().equals(id)) {
				return cluster;
			}
		}
		return null;
	}

	public static List<Cluster> getClusters() {
		return clusters;
	}

	/**
	 * 获得当前坐标点附近坐标点
	 * 
	 * @param building
	 * @param model
	 * @return
	 */
	private static Set<Point> getNeighbours(Building building, StandardWorldModel model) {
		Set<Point> neighbours = new HashSet<Point>();
		for (EntityID id : building.getNeighbours()) {
			StandardEntity entity = model.getEntity(id);
			Point point = null;
			if (entity instanceof Building) {
				point = new Point(((Building) entity).getX(), ((Building) entity).getY());
				point.setId(((Building) entity).getID());
				point.setCanPass(false);
			}
			if (entity instanceof Road) {
				point = new Point(((Road) entity).getX(), ((Road) entity).getY());
				point.setId(((Road) entity).getID());
				point.setCanPass(true);
			}
			if (point != null)
				neighbours.add(point);
		}
		return neighbours;
	}
}
