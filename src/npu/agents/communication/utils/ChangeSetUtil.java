package npu.agents.communication.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import npu.agents.communication.model.BuildingStatus;
import rescuecore2.misc.geometry.GeometryTools2D;
import rescuecore2.misc.geometry.Line2D;
import rescuecore2.misc.geometry.Point2D;
import rescuecore2.standard.entities.AmbulanceTeam;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.Blockade;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.Civilian;
import rescuecore2.standard.entities.Edge;
import rescuecore2.standard.entities.FireBrigade;
import rescuecore2.standard.entities.GasStation;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.Hydrant;
import rescuecore2.standard.entities.PoliceForce;
import rescuecore2.standard.entities.Refuge;
import rescuecore2.standard.entities.Road;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardWorldModel;
import rescuecore2.worldmodel.ChangeSet;
import rescuecore2.worldmodel.EntityID;

public class ChangeSetUtil {
	private ArrayList<Building> buildings = new ArrayList<Building>();
	private ArrayList<GasStation> gasStations = new ArrayList<GasStation>();

	private ArrayList<Hydrant> hydrants = new ArrayList<Hydrant>();
	private ArrayList<Road> roads = new ArrayList<Road>();
	private Set<Blockade> blockades = new HashSet<Blockade>();
	private Set<EntityID> clearedRoadsIDs = new HashSet<EntityID>();
	private ArrayList<Civilian> civilians = new ArrayList<Civilian>();
	private ArrayList<PoliceForce> policeForces = new ArrayList<PoliceForce>();
	private ArrayList<FireBrigade> fireBrigades = new ArrayList<FireBrigade>();
	private ArrayList<AmbulanceTeam> ambulanceTeams = new ArrayList<AmbulanceTeam>();
	private ArrayList<Human> humans = new ArrayList<Human>();

	// TODO 分析建筑，人类当前状态
	private Set<EntityID> buildingsUnburnt = new HashSet<EntityID>();
	private Set<EntityID> buildingsOnFire = new HashSet<EntityID>();
	private Set<EntityID> buildingsIsWarm = new HashSet<EntityID>();
	private Set<EntityID> buildingsExtinguished = new HashSet<EntityID>();
	private Map<EntityID, BuildingStatus> buildingStatusMap = new HashMap<EntityID, BuildingStatus>();
	private Map<EntityID, BuildingStatus> preBuildingStatusMap;
	private Set<EntityID> worseStatusBuildings = new HashSet<EntityID>();
	private int preCountFieryBuildings;
	private int countOfFieryBuildings;
	private Set<Human> humanBuried = new HashSet<Human>();
	private Set<Human> humanInjured = new HashSet<Human>();
	private Set<Blockade> blockadesAroundRefuge = new HashSet<Blockade>();
	private EntityID refugeID;

	private Set<EntityID> buildingsHeating = new HashSet<EntityID>();
	private Set<EntityID> buildingsBurning = new HashSet<EntityID>();
	private Set<EntityID> buildingsInferno = new HashSet<EntityID>();
	private Set<EntityID> roadsNeedToClear = new HashSet<EntityID>();

	private StandardWorldModel world;

	public Set<EntityID> getWorseStatusBuildings() {
		return worseStatusBuildings;
	}

	public void handleChanges(StandardWorldModel model, ChangeSet changes) {
		previousChangesClear();
		world = model;
		Set<EntityID> seenIDs = changes.getChangedEntities();
		for (EntityID seenID : seenIDs) {
			StandardEntity standardEntity = model.getEntity(seenID);
			switch (standardEntity.getStandardURN()) {
			case BUILDING:
				if (!(standardEntity instanceof Refuge))
					buildings.add((Building) standardEntity);
				break;
			case REFUGE:
				refugeID = standardEntity.getID();
				break;
			case GAS_STATION:
				gasStations.add((GasStation) standardEntity);
				break;
			case ROAD:
				roads.add((Road) standardEntity);
				break;
			case CIVILIAN:
				civilians.add((Civilian) standardEntity);
				break;
			case HYDRANT:
				hydrants.add((Hydrant) standardEntity);
				break;
			case POLICE_FORCE:
				policeForces.add((PoliceForce) standardEntity);
				humans.add((Human) standardEntity);
				break;
			case AMBULANCE_TEAM:
				ambulanceTeams.add((AmbulanceTeam) standardEntity);
				humans.add((Human) standardEntity);
				break;
			case FIRE_BRIGADE:
				fireBrigades.add((FireBrigade) standardEntity);
				humans.add((Human) standardEntity);
				break;
			case BLOCKADE:
				blockades.add((Blockade) standardEntity);
				break;
			}
		}
		analyzeBuildings();
		analyzeRoads();
		analyzeHumans();
	}

	public void analyzeBuildings() {
		for (Building building : buildings) {
			BuildingStatus buildingStatus = new BuildingStatus(building, building.getFierynessEnum());
			if (buildingStatus.isWarm()) {
				buildingsIsWarm.add(building.getID());
			} else if (buildingStatus.isFireynessHeating()) {
				buildingsHeating.add(building.getID());
				buildingStatusMap.put(building.getID(), buildingStatus);
			} else if (buildingStatus.isFireynessBurning()) {
				buildingsBurning.add(building.getID());
				buildingStatusMap.put(building.getID(), buildingStatus);
			} else if (buildingStatus.isFireynessInferno()) {
				buildingsBurning.add(building.getID());
				buildingStatusMap.put(building.getID(), buildingStatus);
			} else if (buildingStatus.isExtinguished()) {
				buildingsExtinguished.add(building.getID());
				buildingStatusMap.put(building.getID(), buildingStatus);
			} else if (buildingStatus.isUnburnt()) {
				buildingsUnburnt.add(building.getID());
				buildingStatusMap.put(building.getID(), buildingStatus);
			}
			if (isBuildingStatusWorse(building)) {
				worseStatusBuildings.add(building.getID());
			}
			if (buildingStatus.isOnFire()) {
				buildingsOnFire.add(building.getID());
				countOfFieryBuildings++;
			} else {
				buildingsOnFire.remove(building.getID());
			}
		}

		preBuildingStatusMap = buildingStatusMap;
		preCountFieryBuildings = countOfFieryBuildings;
	}

	public void analyzeRoads() {
		for (Road road : roads) {
			if (road.isBlockadesDefined() && road.getBlockades().isEmpty()) {
				clearedRoadsIDs.add(road.getID());
				if (roadsNeedToClear.contains(road.getID()))
					roadsNeedToClear.remove(road.getID());
			}
		}
	}

	public void analyzeHumans() {
		for (Human human : humans) {
			if (human.isHPDefined() && human.isDamageDefined() && human.isBuriednessDefined()
					&& human.isPositionDefined() && human.getHP() > 0 && human.getBuriedness() > 0) {
				humanBuried.add(human);
			}
			if (human.isHPDefined() && human.isDamageDefined() && human.isBuriednessDefined()
					&& human.isPositionDefined() && human.getHP() > 0
					&& (human.getBuriedness() > 0 || human.getDamage() > 0)) {
				humanInjured.add(human);
			}
		}
	}

	public boolean isBuildingStatusWorse(Building building) {
		if (preBuildingStatusMap != null && preBuildingStatusMap.keySet().contains(building.getID())) {
			BuildingStatus preStatus = preBuildingStatusMap.get(building.getID());
			if (preStatus.getFieryness() < building.getFieryness())
				return true;
		}
		return false;
	}

	public Set<Human> getBuriedHuman() {
		return humanBuried;
	}

	public Set<Human> getInjuredHuman() {
		return humanInjured;
	}

	public Set<Blockade> getSeenBlockades() {
		return blockades;
	}

	public Set<Blockade> getBlockadesHumanStuckedIn() {
		Set<Blockade> blockades = new HashSet<Blockade>();
		for (Blockade blockade : getSeenBlockades()) {
			ArrayList<Integer> X = new ArrayList<Integer>();
			ArrayList<Integer> Y = new ArrayList<Integer>();
			for (int i = 0; i < blockade.getApexes().length - 1; i = i + 2) {
				X.add(blockade.getApexes()[i]);
				Y.add(blockade.getApexes()[i + 1]);
			}
			for (Human human : humans) {
				if (in_or_out_of_polygon(X, Y, human.getX(), human.getY())) {
					blockades.add(blockade);
				}
			}
		}
		return blockades;
	}

	public EntityID getRefugeID() {
		return refugeID;
	}

	public Set<Blockade> getSeenBlockadesAroundRefuge(Map<EntityID, Set<EntityID>> map) {
		if (refugeID != null) {
			for (Blockade blockade : getSeenBlockades()) {
				if (map.keySet().contains(refugeID) && map.get(refugeID).contains(blockade.getPosition()))
					blockadesAroundRefuge.add(blockade);
			}
		}
		return blockadesAroundRefuge;
	}

	public Set<EntityID> getBuildingOnFireMore() {
		if (preCountFieryBuildings < countOfFieryBuildings)
			return buildingsOnFire;
		return null;
	}

	public Set<EntityID> getBuildingsUnburnt() {
		return buildingsUnburnt;
	}

	public Set<EntityID> getClearedRoads() {
		return clearedRoadsIDs;
	}

	public Set<EntityID> getBuildingsIsWarm() {
		return buildingsIsWarm;
	}

	public Set<EntityID> getBuildingsExtinguished() {
		return buildingsExtinguished;
	}

	public Set<EntityID> getBuildingsOnFire() {
		return buildingsOnFire;
	}

	public Set<EntityID> getBuildingsHeating() {
		return buildingsHeating;
	}

	public Set<EntityID> getBuildingsBurning() {
		return buildingsBurning;
	}

	public Set<EntityID> getBuildingsInferno() {
		return buildingsInferno;
	}

	public void previousChangesClear() {
		gasStations.clear();

		hydrants.clear();
		roads.clear();
		blockades.clear();

		civilians.clear();
		policeForces.clear();
		fireBrigades.clear();
		ambulanceTeams.clear();

		blockadesAroundRefuge.clear();
		refugeID = null;

		buildingsUnburnt.clear();
		buildingsIsWarm.clear();
		buildingsExtinguished.clear();
	    buildingsOnFire.clear();
		humanBuried.clear();

		buildingStatusMap.clear();
		worseStatusBuildings.clear();
		countOfFieryBuildings = 0;
	}

	public boolean isBlocked(EntityID lastPosition, Human me, double lastPositionX, double lastPositionY) {
		return (lastPosition != null && lastPosition.getValue() == me.getPosition().getValue()
				&& Math.hypot(Math.abs(me.getX() - lastPositionX), Math.abs(me.getY() - lastPositionY)) < 8000);
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

	/**
	 * gets the seen blockades on a certain road from the changeSet
	 */
	public List<Blockade> getSeenBlockadesOnRoad(Road road) {
		List<Blockade> blockadesOnRoad = new ArrayList<Blockade>();
		for (Blockade b : getSeenBlockades()) {
			if (b.getPosition().getValue() == road.getID().getValue())
				blockadesOnRoad.add(b);
		}
		return blockadesOnRoad;
	}

	public Set<Road> getTotallyBlockedRoad() {
		Set<Road> blockedRoads = new HashSet<Road>();
		for (Road r : roads) {
			if (isBlockingRoadTotally(r)) {
				blockedRoads.add(r);
			}
		}
		return blockedRoads;
	}

	public boolean isBlockingRoadTotally(Road road) {
		List<Blockade> blockadesOnRoad = getSeenBlockadesOnRoad(road);
		for (Blockade blockade : blockadesOnRoad) {
			int[] vertices = mergeAdjacentBlockades(blockade, blockadesOnRoad, 2000);
			for (Edge roadEdge : road.getEdges()) {
				if (roadEdge.isPassable()) {
					if (!openRoadEdge(road, roadEdge, vertices, 2000)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/**
	 * take an original blockade get all other blockades in the same road search
	 * for the adjacent blockades to the original blockade then merge them
	 * together
	 */
	public int[] mergeAdjacentBlockades(Blockade originalBlockade, List<Blockade> otherBlockades, int threshold) {
		List<Integer> verticesList = new ArrayList<Integer>();
		int[] blockadeVertices = originalBlockade.getApexes();

		// put the vertices in a list instead of an array
		for (int i = 0; i < blockadeVertices.length; i++) {
			verticesList.add(blockadeVertices[i]);
		}

		for (Blockade otherBlockade : otherBlockades) {
			// for each other blockade, check if it needs to be merged with the
			// original blockade
			if (otherBlockade.getID().getValue() != originalBlockade.getID().getValue()) {
				int[] otherBlockadeVertices = otherBlockade.getApexes();
				loop: for (int j = 0; j < otherBlockadeVertices.length; j += 2) {
					for (int i = 0; i < verticesList.size(); i += 2) {
						Line2D blockadeLine = new Line2D(
								new Point2D(verticesList.get(i).intValue(), verticesList.get(i + 1).intValue()),
								new Point2D(verticesList.get((i + 2) % verticesList.size()).intValue(),
										verticesList.get((i + 3) % verticesList.size()).intValue()));
						Point2D closestPt = GeometryTools2D.getClosestPointOnSegment(blockadeLine,
								new Point2D(otherBlockadeVertices[j], otherBlockadeVertices[j + 1]));
						double distance = GeometryTools2D.getDistance(closestPt,
								new Point2D(otherBlockadeVertices[j], otherBlockadeVertices[j + 1]));
						if (distance <= threshold) {
							// the two blockades are adjacent and need to be
							// merged before projection
							verticesList = mergeVertices(verticesList, otherBlockadeVertices);

							verticesList.add((int) closestPt.getX());
							verticesList.add((int) closestPt.getY());
							verticesList.add(otherBlockadeVertices[j]);
							verticesList.add(otherBlockadeVertices[j + 1]);
							break loop;
						}
					}

				}
			}
		}

		int[] vertices = new int[verticesList.size()];
		for (int i = 0; i < verticesList.size(); i++) {
			vertices[i] = verticesList.get(i).intValue();
		}
		return vertices;
	}

	public List<Integer> mergeVertices(List<Integer> list, int[] array) {
		for (int i = 0; i < array.length; i++) {
			list.add(array[i]);
		}
		return list;
	}

	public boolean openRoadEdge(Road road, Edge roadEdge, int[] blockadeVertices, int threshold) {
		double minDistanceStart = Double.POSITIVE_INFINITY;
		double minDistanceEnd = Double.POSITIVE_INFINITY;
		Line2D roadEdgeLine = roadEdge.getLine();
		ArrayList<Point2D> projectedPoints = new ArrayList<Point2D>();

		for (int i = 0; i < blockadeVertices.length; i += 2) {
			Point2D point = GeometryTools2D.getClosestPointOnSegment(roadEdgeLine,
					new Point2D(blockadeVertices[i], blockadeVertices[i + 1]));
			if (!projectedPoints.contains(point)) {
				projectedPoints.add(point);
			}
		}
		if (projectedPoints.size() < 2)
			return true;
		for (Point2D projectedPoint : projectedPoints) {
			double dStart = GeometryTools2D.getDistance(projectedPoint, roadEdge.getStart());
			if (dStart < minDistanceStart) {
				minDistanceStart = dStart;
			}
			double dEnd = GeometryTools2D.getDistance(projectedPoint, roadEdge.getEnd());
			if (dEnd < minDistanceEnd) {
				minDistanceEnd = dEnd;
			}
		}
		if (minDistanceEnd > threshold || minDistanceStart > threshold) {
			Edge neighbourEdge = getNeighbourEdge(road, roadEdge);
			Line2D neighbourEdgeLine = neighbourEdge.getLine();
			projectedPoints = new ArrayList<Point2D>();
			for (int i = 0; i < blockadeVertices.length; i += 2) {
				Point2D point = GeometryTools2D.getClosestPointOnSegment(neighbourEdgeLine,
						new Point2D(blockadeVertices[i], blockadeVertices[i + 1]));
				if (!projectedPoints.contains(point)) {
					projectedPoints.add(point);
				}
			}
			if (projectedPoints.size() < 2)
				return true;

			minDistanceStart = Double.POSITIVE_INFINITY;
			minDistanceEnd = Double.POSITIVE_INFINITY;
			for (Point2D projectedPoint : projectedPoints) {
				double dStart = GeometryTools2D.getDistance(projectedPoint, neighbourEdge.getStart());
				if (dStart < minDistanceStart) {
					minDistanceStart = dStart;
				}

				double dEnd = GeometryTools2D.getDistance(projectedPoint, neighbourEdge.getEnd());
				if (dEnd < minDistanceEnd) {
					minDistanceEnd = dEnd;
				}
			}
			if (minDistanceEnd > threshold || minDistanceStart > threshold) {
				return true;
			}
		}

		return false;
	}

	public Edge getNeighbourEdge(Road road, Edge roadEdge) {
		Area neighbour = (Area) world.getEntity(roadEdge.getNeighbour());
		for (Edge neighbourEdge : neighbour.getEdges()) {
			if (neighbourEdge.isPassable() && neighbourEdge.getNeighbour().getValue() == road.getID().getValue())
				return neighbourEdge;
		}
		return null;
	}
}
