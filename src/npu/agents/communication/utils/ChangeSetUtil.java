package npu.agents.communication.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import rescuecore2.standard.entities.AmbulanceTeam;
import rescuecore2.standard.entities.Blockade;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.Civilian;
import rescuecore2.standard.entities.FireBrigade;
import rescuecore2.standard.entities.GasStation;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.Hydrant;
import rescuecore2.standard.entities.PoliceForce;
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
	private ArrayList<Blockade> blockades = new ArrayList<Blockade>();

	private ArrayList<Civilian> civilians = new ArrayList<Civilian>();
	private ArrayList<PoliceForce> policeForces = new ArrayList<PoliceForce>();
	private ArrayList<FireBrigade> fireBrigades = new ArrayList<FireBrigade>();
	private ArrayList<AmbulanceTeam> ambulanceTeams = new ArrayList<AmbulanceTeam>();
	private ArrayList<Human> humans = new ArrayList<Human>();
	 
	//TODO 分析建筑，人类当前状态
	private Set<EntityID> buildingUnburnt = new HashSet<EntityID>();
	private Set<EntityID> buildingOnFire = new HashSet<EntityID>();
	private Set<EntityID> humanInjured = new HashSet<EntityID>();
	private Map<EntityID,EntityID> humanStucked = new HashMap<EntityID,EntityID>();
	private Map<EntityID,EntityID> recordHumanPosition = new HashMap<EntityID,EntityID>();
	
	public void handleChanges(StandardWorldModel model,ChangeSet changes) {
		   previousChangesClear();
		   Set<EntityID> seenIDs = changes.getChangedEntities();
		   for(EntityID seenID : seenIDs) {
			   StandardEntity standardEntity = model.getEntity(seenID);
				switch (standardEntity.getStandardURN()) {
				case BUILDING:
					buildings.add((Building) standardEntity);
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
					humans.add((Human) standardEntity);
					break;
				}
		   }
		   analyzeBuildings();
		   analyzeHumans();
	   }
	   
	   public void analyzeBuildings() {
		   for(Building building : buildings) {
			   if(building.isFierynessDefined()&&building.isOnFire())
				   buildingOnFire.add(building.getID());
		   }
	   }
	   
	   public void analyzeHumans() {
		   for(Human human : humans) {
			   if(human.isHPDefined()&&human.isDamageDefined()
					   &&human.isBuriednessDefined() && human.isPositionDefined()
					   		&& human.getHP() > 0&&(human.getBuriedness() > 0 || human.getDamage() > 0)) {
				   humanInjured.add(human.getID());
			   }
			   //TODO 人类被困判别
/*			   if(!recordHumanPosition.isEmpty()) {
				   if(recordHumanPosition.keySet().contains(human.getID())) {
					   EntityID position = recordHumanPosition.get(human.getID());
					   if(position.equals(human.getPosition()))
							   humanStucked.put(human.getID(), position);
				   }
			   }*/
		   }
	   }
	   
	   public Set<EntityID> getFireBuildings() {
		   return buildingOnFire;
	   }
	   
	   public Set<EntityID> getInjuredHuman() {
		   return humanInjured;
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
	   }
}
