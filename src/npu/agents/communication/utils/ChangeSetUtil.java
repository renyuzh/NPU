package npu.agents.communication.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import rescuecore2.standard.entities.AmbulanceCentre;
import rescuecore2.standard.entities.AmbulanceTeam;
import rescuecore2.standard.entities.Blockade;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.Civilian;
import rescuecore2.standard.entities.FireBrigade;
import rescuecore2.standard.entities.FireStation;
import rescuecore2.standard.entities.GasStation;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.Hydrant;
import rescuecore2.standard.entities.PoliceForce;
import rescuecore2.standard.entities.PoliceOffice;
import rescuecore2.standard.entities.Refuge;
import rescuecore2.standard.entities.Road;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityConstants;
import rescuecore2.standard.entities.StandardWorldModel;
import rescuecore2.worldmodel.ChangeSet;
import rescuecore2.worldmodel.EntityID;

public class ChangeSetUtil {
	private static final int WARM_TEMPERATURE = 30;
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
	private Set<Human> humanBuried = new HashSet<Human>();
    private Set<Human> humanInjured = new HashSet<Human>();
	private Set<Blockade> blockadesAroundRefuge = new HashSet<Blockade>();
	private EntityID refugeID;
	
	private Set<EntityID> roadsNeedToClear = new HashSet<EntityID>();

	private StandardWorldModel world ;
	public void handleChanges(StandardWorldModel model, ChangeSet changes) {
		previousChangesClear();
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
		world = model;
	}

	public void analyzeBuildings() {
		for (Building building : buildings) {
			if (building.isFierynessDefined() && building.isOnFire()
					&& building.getFierynessEnum() != StandardEntityConstants.Fieryness.BURNT_OUT)
				buildingsOnFire.add(building.getID());
			if (building.isFierynessDefined() && building.isTemperatureDefined()
					&& building.getTemperature() > WARM_TEMPERATURE && !building.isOnFire()
					&& building.getFierynessEnum() != StandardEntityConstants.Fieryness.BURNT_OUT
					&& (!(building instanceof Refuge)) && (!(building instanceof AmbulanceCentre))
					&& (!(building instanceof FireStation)) && (!(building instanceof PoliceOffice))) {
				buildingsIsWarm.add(building.getID());
			}
			if (building.isFierynessDefined() && building.isTemperatureDefined() && (building
					.getTemperature() <= WARM_TEMPERATURE
					&& ((building.getFierynessEnum() == StandardEntityConstants.Fieryness.WATER_DAMAGE)
							|| (building.getFierynessEnum() == StandardEntityConstants.Fieryness.MINOR_DAMAGE)
							|| (building.getFierynessEnum() == StandardEntityConstants.Fieryness.MODERATE_DAMAGE)
							|| (building.getFierynessEnum() == StandardEntityConstants.Fieryness.SEVERE_DAMAGE)))) {
				buildingsExtinguished.add(building.getID());
			}
			if (building.isFierynessDefined() && building.isTemperatureDefined()
					&& building.getFierynessEnum() == StandardEntityConstants.Fieryness.UNBURNT
					&& building.getTemperature() <= WARM_TEMPERATURE) {
				buildingsUnburnt.add(building.getID());
			}
		}
	}
	
	public void analyzeRoads() {
		for(Road road: roads) {
			if(road.isBlockadesDefined() && road.getBlockades().isEmpty()) {
				clearedRoadsIDs.add(road.getID());
				if(roadsNeedToClear.contains(road.getID()))
					roadsNeedToClear.remove(road.getID());
			}
		}
	}
	
	public void analyzeHumans() {
		for (Human human : humans) {
			if (human.isHPDefined() && human.isDamageDefined() && human.isBuriednessDefined()
					&& human.isPositionDefined() && human.getHP() > 0
					&& human.getBuriedness() > 0 ) {
				humanBuried.add(human);
			}
			if (human.isHPDefined() && human.isDamageDefined() && human.isBuriednessDefined()
					&& human.isPositionDefined() && human.getHP() > 0
					&& (human.getBuriedness() > 0 || human.getDamage() > 0)) {
				humanInjured.add(human);
			}
		}
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
	public Set<Blockade> getBlockadesHumanStucked() {
		Set<Blockade> blockades = new HashSet<Blockade>();
        for(Blockade blockade: getSeenBlockades()) {
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
	}
	public  boolean isBlocked(EntityID lastPosition, Human me,
			double lastPositionX, double lastPositionY) {
		return (lastPosition != null
				&& lastPosition.getValue() == me.getPosition().getValue() && Math
				.hypot(Math.abs(me.getX() - lastPositionX),
						Math.abs(me.getY() - lastPositionY)) < 8000);
	}
	public  boolean in_or_out_of_polygon(ArrayList<Integer> X,
			ArrayList<Integer> Y, int x, int y) {
		int i, j;
		boolean c = false;
		for (i = 0, j = X.size() - 1; i < X.size(); j = i++) {
			if ((((Y.get(i) <= y) && (y < Y.get(j))) || ((Y.get(j) <= y) && (y < Y
					.get(i))))
					&& (x < (X.get(j) - X.get(i)) * (y - Y.get(i))
							/ (Y.get(j) - Y.get(i)) + X.get(i)))
				c = !c;
		}
		return c;
	}
	
}
