package npu.agents.fb;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import npu.agents.clustering.Cluster;
import npu.agents.clustering.ClustingMap;
import npu.agents.clustering.ClustingMap2;
import npu.agents.common.AbstractCommonAgent;
import npu.agents.communication.utils.ChangeSetUtil;
import npu.agents.communication.utils.CommUtils.MessageID;
import npu.agents.model.Message;
import npu.agents.utils.KConstants;
import rescuecore2.log.Logger;
import rescuecore2.messages.Command;
import rescuecore2.standard.entities.Blockade;
import rescuecore2.standard.entities.Civilian;
import rescuecore2.standard.entities.FireBrigade;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.Hydrant;
import rescuecore2.standard.entities.Refuge;
import rescuecore2.standard.entities.Road;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.standard.messages.AKSpeak;
import rescuecore2.worldmodel.ChangeSet;
import rescuecore2.worldmodel.EntityID;

public class FireBrigadeAgent extends AbstractCommonAgent<FireBrigade> {
	private Cluster cluster;
	private Set<EntityID> buildingUnexplored = new HashSet<EntityID>();
	private Set<Message> messagesWillSend = new HashSet<Message>();
	private Set<EntityID> onFireBuildingsIDs = new HashSet<EntityID>();
	/*
	 * private Set<EntityID> warmBuildingsIDs = new HashSet<EntityID>(); private
	 * Set<EntityID> heatingBuildingsIDs = new HashSet<EntityID>(); private
	 * Set<EntityID> burningBuildingsIDs = new HashSet<EntityID>(); private
	 * Set<EntityID> InfernoBuildingsIDs = new HashSet<EntityID>();
	 */
	private Set<EntityID> burnOutBuildingIDs = new HashSet<EntityID>();
	private Set<EntityID> extinguishedBuildingsIDs = new HashSet<EntityID>();
	private Set<EntityID> unburntBuildingsIDs = new HashSet<EntityID>();
	private Set<EntityID> collaborationTasks = new HashSet<EntityID>();
	private Set<EntityID> callForFBHelpTasks = new HashSet<EntityID>();
	private Map<EntityID, Integer> distanceToHelp = new HashMap<EntityID, Integer>();
	private Map<EntityID, Set<Integer>> distancesToHelpOfotherFBs = new HashMap<EntityID, Set<Integer>>();
	// private Set<EntityID> fieryBuildingsNowCluster = new HashSet<EntityID>();
	private int maxWater;
	private int maxDistance;
	private int maxPower;
	private Set<EntityID> roadsInCluster;
	private Map<EntityID, Set<EntityID>> buildingEntrances;
	private Map<EntityID, Set<EntityID>> refugesEntrancesMap;
	private EntityID[] prePosition = new EntityID[2];
	private EntityID nowPosition;
	
	public ChangeSetUtil seenChanges;

	@Override
	public void postConnect() {
		super.postConnect();
		try {
			ClustingMap2.initMap(Math.min(KConstants.countOffb,configuration.getFireBrigadesCount()), 100, model);
			cluster = ClustingMap2.assignAgentToCluster(me(), model);
			if (cluster == null) {
				System.out.println("该agent没有分配到cluster");
				return;
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("聚类失败");
			return;
		}
		buildingUnexplored = cluster.getBuildingsIDs();
		roadsInCluster = cluster.getRoadsInCluster();
		maxWater = configuration.getMaxWater();
		maxDistance = configuration.getMaxFireExtinguishDistance();
		maxPower = configuration.getMaxFirePower();
		buildingEntrances = ClustingMap.getBuildingEntrances();
		refugesEntrancesMap = cluster.getRoadARoundRefuge();
		seenChanges = new ChangeSetUtil();

	}

	@Override
	public void think(int time, ChangeSet changes, Collection<Command> heard) {
		if (time < configuration.getIgnoreAgentCommand())
			return;
		handleReceiveMessages(time, changes, heard);
		sendMessages(time);
	}

	public void handleReceiveMessages(int time, ChangeSet changes, Collection<Command> heard) {
		if (time == configuration.getIgnoreAgentCommand()) {
			sendSubscribe(time, configuration.getFireChannel());// 注册了该通道的都可以接收到
			if (me().isHPDefined() && me().isBuriednessDefined() && me().getHP() > 0 && me().getBuriedness() > 0) {
				// TODO 暂时不管
				callForATHelp(time, MessageID.FB_BURIED);
				System.out.println(me().getID() + " is buried,hp is" + me().getHP());
			}
			return;
		}
		System.out.println(me().getID()+" 我可以清理的最远距离 "+maxDistance);
		handleHeard(time, heard);
		seenChanges.handleChanges(model, changes);
		callOtherFBHelp(time);
		addInjuredHumanInfoToMessageSend(time);
		addRoadsInfoToMessageSend(time);
/*		updateExtinguishedBuildings(time);*/
		/*if (somethingWrong(time)){
		}*/
		/*EntityID position = positionWhereIStuckedIn(seenChanges.getSeenBlockades(), me());
		if (position != null) {
			Message message = new Message(MessageID.PLATOON_STUCKED, position, time, configuration.getPoliceChannel());
			messagesWillSend.add(message);
			return;
		}*/
		/*if (me().isHPDefined() && me().isDamageDefined() && me().getHP() > 0 && me().getDamage() > 0) {
			if (canMoveToRefuge(time, me(), refugesEntrancesMap)) {
			} else {
				callForATHelp(time, MessageID.PLATOON_BURIED);
			}
			System.out.println(me().getID() + " is buried,hp is" + me().getHP() + ",damage is" + me().getDamage());
		}*/
/*		if (isFillingWater(time))
			return;
		if (mainRoadBlockedTotally(time)) {
			sendRest(time);
			return;
		}
		if (moveForWater(time)) {
			return;
		}
		if (handleSeenFieryBuildings(time))
			return;*/
		/*if (handleHeardFieryBuildings(time)) {
			return;
		}
		distancesToHelpOfotherFBs.clear();*/
		/*
		 * if (changeClusterToHelpOtherFB(time)) {
		 * 
		 * }
		 */
		/*List<EntityID> path = randomWalk(areaIDs);
		if (path != null){
			sendMove(time, path);
			System.out.println(me().getID()+" see no targets,random walk");
			return;
		}
		System.out.println(me().getID()+" see no targets,random walk failed");*/

	}

	/*
	 * private boolean changeClusterToHelpOtherFB(int time) {
	 * if(seenChanges.getBuildingsOnFire() != null || ) return false; }
	 */

	private boolean handleHeardFieryBuildings(int time) {
		for (EntityID destID : distancesToHelpOfotherFBs.keySet()) {
			Set<EntityID> helps = distanceToHelp.keySet();
			if (helps.isEmpty())
				break;
			if (!helps.isEmpty() && helps.contains(destID)) {
				List<Integer> distances = new ArrayList<>(distancesToHelpOfotherFBs.get(destID));
				Collections.sort(distances);
				if (distances.get(1) >= distanceToHelp.get(destID)) {
					List<EntityID> path = search.getPath(me().getPosition(), destID, null);
					if (path != null) {
						distanceToHelp.clear();
						Road road = (Road) model.getEntity(destID);
						sendMove(time, path, road.getX(), road.getY());
						return true;
					}
				}

			}

		}
		distanceToHelp.clear();
		boolean flag = false;
		for (EntityID targetID : onFireBuildingsIDs) {
			EntityID destID = ((EntityID[]) buildingEntrances.get(targetID).toArray())[0];
			List<EntityID> path = search.getPath(me().getPosition(), destID, null);
			if (path != null) {
				int distance = getDistanceByPath(path, me());
				Message message = new Message(MessageID.BUILDING_ON_FIRE, targetID, time,
						configuration.getFireChannel());
				messagesWillSend.add(message);
				distanceToHelp.put(targetID, distance);
				flag = true;
			}
		}
		return flag;
	}

	private void callOtherFBHelp(int time) {
		Set<EntityID> otherFieryBuildingsIDs = seenChanges.getBuildingOnFireMore();
		Set<EntityID> fieryWorseBuildingsIDS = seenChanges.getWorseStatusBuildings();
		if(otherFieryBuildingsIDs != null ){
			callForFBHelpTasks.addAll(otherFieryBuildingsIDs);
		}
		if(fieryWorseBuildingsIDS != null) {
			callForFBHelpTasks.addAll(fieryWorseBuildingsIDS);
		}
		for (EntityID id : callForFBHelpTasks) {
			Message message = new Message(MessageID.FB_NEED_COLLABORATION, id, time, configuration.getFireChannel());
			messagesWillSend.add(message);
		}
	}

	private boolean handleSeenFieryBuildings(int time) {
		List<EntityID> fieryBuildingsIDs = new ArrayList<>(seenChanges.getBuildingsOnFire());
		distanceSort(fieryBuildingsIDs, me());
		for (EntityID next : fieryBuildingsIDs) {
			if (model.getDistance(getID(), next) <= maxDistance) {
				System.out.println(me().getID()+" is Extinguishing " + next);
				sendExtinguish(time, next, Math.min(me().getWater(), maxPower));
				//sendSpeak(time, 1, ("Extinguishing " + next).getBytes());
				return true;
			} else {
				EntityID destID = ((EntityID[]) buildingEntrances.get(next).toArray())[0];
				List<EntityID> path = search.getPath(me().getPosition(), destID, null);
				if (path != null) {
					sendMove(time, path);
					System.out.println(me().getID()+" is moving to seen fiery buildings");
					return true;
				}
			}
		}
		return false;
	}

	private void updateExtinguishedBuildings(int time) {
		Set<EntityID> seenExtinguishedBuildingsIDs = seenChanges.getBuildingsExtinguished();
		extinguishedBuildingsIDs.addAll(seenExtinguishedBuildingsIDs);
		extinguishedBuildingsIDs.addAll(extinguishedBuildingsIDs);
		extinguishedBuildingsIDs.addAll(burnOutBuildingIDs);
		for (EntityID extinguishedBuildingID : extinguishedBuildingsIDs) {
			/*
			 * heatingBuildingsIDs.remove(extinguishedBuildingID);
			 * burningBuildingsIDs.remove(extinguishedBuildingID);
			 * InfernoBuildingsIDs.remove(extinguishedBuildingID);
			 */
			onFireBuildingsIDs.remove(extinguishedBuildingID);
		}
	}

	private void handleHeard(int time, Collection<Command> heards) {
		for (Command heard : heards) {
			if (!(heard instanceof AKSpeak))
				continue;
			AKSpeak info = (AKSpeak) heard;
			EntityID agentID = info.getAgentID();
			String content = new String(info.getContent());
			if (content.isEmpty()) {
				System.out.println("fb of " + me().getID() + " heard dropped message from " + info.getAgentID()
						+ " from " + info.getChannel());
				continue;
			}
			// 居民发出求救信息，要么受伤，要么被困
			if (content.equalsIgnoreCase("Ouch") || content.equalsIgnoreCase("Help")) {
				Civilian civilian = (Civilian) model.getEntity(agentID);
				EntityID civilianPositionID = civilian.getPosition();
				if (civilian.isHPDefined() && civilian.isBuriednessDefined() && civilian.isDamageDefined()
						&& civilian.getHP() > 0 && (civilian.getBuriedness() > 0 || civilian.getDamage() > 0)) {
					Message message = new Message(MessageID.CIVILIAN_INJURED, civilianPositionID, time,
							configuration.getAmbulanceChannel());
					messagesWillSend.add(message);
				} else {
					Message message = new Message(MessageID.CIVILIAN_BlOCKED, civilianPositionID, time,
							configuration.getPoliceChannel());
					messagesWillSend.add(message);
				}
				System.out.println("fb of " + me().getID() + "handle civilian help");
				continue;
			}
			String[] heardInfo = content.split(",");
			String typeOfhearedInfo = heardInfo[0];
			String positionOfInfo = heardInfo[1];
			int id = Integer.parseInt(typeOfhearedInfo);
			int postion = Integer.parseInt(positionOfInfo);
			EntityID targetID = new EntityID(postion);
			MessageID messageID = MessageID.values()[id];
			if (configuration.getRadioChannels().contains(info.getChannel()))
				handleRadio(targetID, messageID);
			else
				handleVoice(targetID, messageID);
		}
	}

	private void handleVoice(EntityID targetID, MessageID messageID) {
		switch (messageID) {
		/*
		 * case BUILDING_WARM: warmBuildingsIDs.add(targetID); break; case
		 * BUILDING_HEATING: heatingBuildingsIDs.add(targetID); break; case
		 * BUILDING_BURNING: burningBuildingsIDs.add(targetID); break; case
		 * BUILDING_INFERNO: InfernoBuildingsIDs.add(targetID); break;
		 */
		case BUILDING_ON_FIRE:
			onFireBuildingsIDs.add(targetID);
			break;
		case BUILDING_BURNT_OUT:
			burnOutBuildingIDs.add(targetID);
			break;
		case BUILDING_EXTINGUISHED:
			extinguishedBuildingsIDs.add(targetID);
			break;
		case BUILDING_UNBURNT:
			unburntBuildingsIDs.add(targetID);
			break;
		case FB_NEED_COLLABORATION:
			collaborationTasks.add(targetID);
			break;
		default:
			break;
		}
	}

	private void handleRadio(EntityID targetID, MessageID messageID) {
		switch (messageID) {
		/*
		 * case BUILDING_WARM: warmBuildingsIDs.add(targetID); break; case
		 * BUILDING_HEATING: heatingBuildingsIDs.add(targetID);
		 * onFireBuildingsIDs.add(targetID); break; case BUILDING_BURNING:
		 * burningBuildingsIDs.add(targetID); onFireBuildingsIDs.add(targetID);
		 * break; case BUILDING_INFERNO: InfernoBuildingsIDs.add(targetID);
		 * onFireBuildingsIDs.add(targetID); break;
		 */
		case BUILDING_ON_FIRE:
			onFireBuildingsIDs.add(targetID);
		case BUILDING_EXTINGUISHED:
			extinguishedBuildingsIDs.add(targetID);
			break;
		case BUILDING_UNBURNT:
			unburntBuildingsIDs.add(targetID);
			break;
		case FB_NEED_COLLABORATION:
			collaborationTasks.add(targetID);
			break;
		default:
			break;
		}
	}

	public void addFireInfo(Set<EntityID> fireBuildings, int time) {
		for (EntityID buidingID : fireBuildings) {
			Message message = new Message(MessageID.BUILDING_ON_FIRE, buidingID, time, configuration.getFireChannel());
			messagesWillSend.add(message);
		}
	}

	private boolean isFillingWater(int time) {
		if (me().isWaterDefined() && me().getWater() < maxWater
				&& (location() instanceof Refuge || location() instanceof Hydrant)) {
			sendRest(time);
			System.out.println(me().getID()+" is filling water at "+me().getPosition());
			return true;
		}
		return false;
	}

	public boolean moveForWater(int time) {
		// Are we out of water?
		if (me().isWaterDefined() && me().getWater() == 0) {
			System.out.println(me().getID()+" is out of water");
			List<EntityID> refugeIDs = new ArrayList<EntityID>(getEntrancesOfRefuges().keySet());
			distanceSort(refugeIDs, me());
			EntityID destRefugeID = ((EntityID[]) getEntrancesOfRefuges().get(refugeIDs.get(0)).toArray())[0];
			List<EntityID> result = new ArrayList<EntityID>(hydrantIDs);
			distanceSort(result, me());
			EntityID destHydrantID = result.get(0);
			int distance1 = findDistanceTo(destRefugeID, me().getX(), me().getY());
			int distance2 = findDistanceTo(destHydrantID, me().getX(), me().getY());
			List path = null;
			if (distance1 > distance2) {
				path = search.getPath(me().getPosition(), destHydrantID, null);
				if (path != null) {
					Hydrant hydrant = (Hydrant) model.getEntity(destHydrantID);
					sendMove(time, path, hydrant.getX(), hydrant.getY());
					System.out.println(me().getID()+" is moving for hydrant");
					return true;
				}else{
					path = randomWalk(areaIDs);
					if(path != null){
						sendMove(time,path);
						System.out.println(me().getID()+" move for hydrant failed");
					}else{
						System.out.println(me().getID()+" randomly move for hydrant failed");
					}
				}
			} else {
				path = search.getPath(me().getPosition(), destRefugeID, null);
				if (path != null) {
					path.add(refugeIDs.get(0));
					Refuge refuge = (Refuge) model.getEntity(refugeIDs.get(0));
					sendMove(time, path, refuge.getX(), refuge.getY());
					System.out.println(me().getID()+"is moving for refuge");
					return true;
				}else{
					path = randomWalk(areaIDs);
					if(path != null){
						sendMove(time,path);
						System.out.println(me().getID()+" move for refuge failed");
					}else{
						System.out.println(me().getID()+" randomly move for refuge failed");
					}
				}
			}
		}
		return false;
	}

	@Override
	public EnumSet<StandardEntityURN> getRequestedEntityURNsEnum() {
		return EnumSet.of(StandardEntityURN.FIRE_BRIGADE);
	}

	public boolean mainRoadBlockedTotally(int time) {
		Set<Road> totallyBlockedRoads = seenChanges.getTotallyBlockedMainRoad();
		for (Road totallyBlockedRoad : totallyBlockedRoads) {
	/*		Message message = new Message(MessageID.ROAD_BLOCKED_TOTALLY, totallyBlockedRoad.getID(), time,
					configuration.getPoliceChannel());
			messagesWillSend.add(message);*/
			System.out.println(totallyBlockedRoad.getID()+"road is blocked totally,"+me().getID()+" need pf help");
			return true;
		}
		return false;
	}
	public void callForATHelp(int time, MessageID messageID) {
		Message message = new Message(messageID, me().getID(), time, configuration.getAmbulanceChannel());
		messagesWillSend.add(message);
		sendRest(time);
		System.out.println(messageID.name() + " of " + me().getID() + " at " + time);
	}

	public boolean canMoveToRefuge(int time, Human me, Map<EntityID, Set<EntityID>> refugesEntrancesMap) {
		Set<EntityID> refugeIDsInCluster = refugesEntrancesMap.keySet();
		if (!refugeIDsInCluster.isEmpty()) {
			List<EntityID> refugeIDs = new ArrayList<EntityID>(refugeIDsInCluster);
			distanceSort(refugeIDs, me);
			EntityID dest = ((EntityID[]) getEntrancesOfRefuges().get(refugeIDs.get(0)).toArray())[0];
			List<EntityID> path = search.getPath(me.getPosition(), dest, null);
			path.add(refugeIDs.get(0));
			if (path != null) {
				Refuge refuge = (Refuge) model.getEntity(refugeIDs.get(0));
				Logger.info("Moving to refuge");
				sendMove(time, path, refuge.getX(), refuge.getY());
				System.out.println(me().getID() + "of at move to refuge for damage");
				return true;
			}
		}
		return false;
	}
	public void addBuildingInfoToMessageSend(int time) {
		/*
		 * for (EntityID unburntBuidingID : seenChanges.getBuildingsUnburnt()) {
		 * Message message = new Message(MessageID.BUILDING_UNBURNT,
		 * unburntBuidingID, time, configuration.getFireChannel());
		 * messagesWillSend.add(message); }
		 */
		for (EntityID warmBuidingID : seenChanges.getBuildingsIsWarm()) {
			System.out.println("send warm building message");
			Message message = new Message(MessageID.BUILDING_WARM, warmBuidingID, time, configuration.getFireChannel());
			messagesWillSend.add(message);
		}
		for (EntityID onFireBuildingID : seenChanges.getBuildingsOnFire()) {
			System.out.println("send on fire building message");
			Message message = new Message(MessageID.BUILDING_ON_FIRE, onFireBuildingID, time,
					configuration.getFireChannel());
			messagesWillSend.add(message);
		}
		for (EntityID extinguishBuildingID : seenChanges.getBuildingsExtinguished()) {
			System.out.println("send extinguished building message");
			Message message = new Message(MessageID.BUILDING_EXTINGUISHED, extinguishBuildingID, time,
					configuration.getFireChannel());
			messagesWillSend.add(message);
		}
		for (EntityID burtOutBuildingID : seenChanges.getBuildingBurtOut()) {
			System.out.println("send burtOut building message");
			Message message = new Message(MessageID.BUILDING_BURNT_OUT, burtOutBuildingID, time,
					configuration.getFireChannel());
			messagesWillSend.add(message);
		}
	}

	public void addInjuredHumanInfoToMessageSend(int time) {
		for (Human human : seenChanges.getBuriedPlatoons()) {
			Message message = new Message(MessageID.PLATOON_BURIED, human.getPosition(), time,
					configuration.getAmbulanceChannel());
			if (human.getID() != me().getID())
				messagesWillSend.add(message);
		}
		for (Human human : seenChanges.getInjuredCivilians()) {
			Message message = new Message(MessageID.CIVILIAN_INJURED, human.getPosition(), time,
					configuration.getAmbulanceChannel());
			if (human.getID() != me().getID())
				messagesWillSend.add(message);
		}
	}
	public void sendMessages(int time) {
		sendAllVoiceMessages(time);
		sendAllRadioMessages(time);
	}

	private void sendAllRadioMessages(int time) {
		for (Message message : messagesWillSend) {
			String data = message.getMessageID().ordinal() + "," + message.getPositionID().getValue();
			sendSpeak(time, message.getChannel(), data.getBytes());
		}
		messagesWillSend.clear();
	}

	private void sendAllVoiceMessages(int time) {
		for (Message message : messagesWillSend) {
			String data = message.getMessageID().ordinal() + "," + message.getPositionID().getValue();
			sendSpeak(time, message.getChannel(), data.getBytes());
		}
		messagesWillSend.clear();
	}
	public void addRoadsInfoToMessageSend(int time) {
		for (Blockade blockade : seenChanges.getSeenBlockades()) {
			Message message = new Message(MessageID.PLATOON_BLOCKED, blockade.getID(), time,
					configuration.getPoliceChannel());
			messagesWillSend.add(message);
		}
		for (Road road : seenChanges.getTotallyBlockedBuildingEntrance()) {
			Message message = new Message(MessageID.ENTRANCE_BLOCKED_TOTALLY, road.getID(), time,
					configuration.getPoliceChannel());
			messagesWillSend.add(message);
		}
		
		for (Road road : seenChanges.getTotallyBlockedMainRoad()) {
			Message message = new Message(MessageID.MAIN_ROAD_BLOCKED_TOTALLY, road.getID(), time,
					configuration.getPoliceChannel());
			messagesWillSend.add(message);
		}

		for (Blockade blockadeStuckedHuman : seenChanges.getBlockadesHumanStuckedIn()) {
			Message message = new Message(MessageID.HUMAN_STUCKED, blockadeStuckedHuman.getPosition(), time,
					configuration.getPoliceChannel());
			messagesWillSend.add(message);
		}
		for (EntityID roadID : seenChanges.getClearedRoads()) {
			Message message = new Message(MessageID.ROAD_CLEARED, roadID, time, configuration.getPoliceChannel());
			messagesWillSend.add(message);
		}
	}
	private boolean somethingWrong(int time){
		prePosition[1] = prePosition[0];
		prePosition[0] = nowPosition;
		nowPosition = me().getPosition();
		if(prePosition[0] != null && prePosition[1] != null &&  nowPosition != null
				&& nowPosition.equals(prePosition[0])&& prePosition[0].equals(prePosition[1]))
		{
			List<EntityID> path = randomWalk(areaIDs);
			if(path != null) {
				sendMove(time, path);
				System.out.println("something is wrong for fb of "+ me().getID());
				prePosition[0] =null;
				prePosition[1] = null;
			  nowPosition = null;
			}
		}
			return false;
	}
}
