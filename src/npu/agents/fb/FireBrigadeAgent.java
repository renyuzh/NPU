package npu.agents.fb;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import npu.agents.clustering.Cluster;
import npu.agents.clustering.ClustingMap;
import npu.agents.common.AbstractCommonAgent;
import npu.agents.communication.model.Message;
import npu.agents.communication.utils.ChangeSetUtil;
import npu.agents.communication.utils.CommUtils.MessageID;
import npu.agents.utils.DistanceSorter;
import npu.agents.utils.KConstants;
import rescuecore2.log.Logger;
import rescuecore2.messages.Command;
import rescuecore2.misc.Pair;
import rescuecore2.standard.entities.Blockade;
import rescuecore2.standard.entities.Civilian;
import rescuecore2.standard.entities.FireBrigade;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.Hydrant;
import rescuecore2.standard.entities.Refuge;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.standard.messages.AKSpeak;
import rescuecore2.worldmodel.ChangeSet;
import rescuecore2.worldmodel.EntityID;

public class FireBrigadeAgent extends AbstractCommonAgent<FireBrigade> {
	private Cluster cluster;
	private Set<EntityID> buildingUnexplored = new HashSet<EntityID>();
	private Set<Message> messagesWillSend = new HashSet<Message>();

	private EntityID[] prePosition = new EntityID[2];
	private EntityID nowPosition;

	private Map<EntityID, Set<EntityID>> roadsAroundRefuge;
	private List<EntityID> pathByPriotity;
	private Set<EntityID> onFireBuildingsIDs = new HashSet<EntityID>();
	private Set<EntityID> warmBuildingsIDs = new HashSet<EntityID>();
	private Set<EntityID>  heatingBuildingsIDs = new HashSet<EntityID>();
	private Set<EntityID>  burningBuildingsIDs = new HashSet<EntityID>();
	private Set<EntityID>  InfernoBuildingsIDs = new HashSet<EntityID>();
	private Set<EntityID>  extinguishedBuildingsIDs = new HashSet<EntityID>();
	private Set<EntityID>  unburntBuildingsIDs = new HashSet<EntityID>();
	private Set<EntityID>  collaborationTasks = new HashSet<EntityID>();
	private Set<EntityID> callForPFHelpTasks = new HashSet<EntityID>();
	private int maxWater;
	private int maxDistance;
	private int maxPower;
	private Set<EntityID> roadsInCluster;
	private Map<EntityID, Set<EntityID>> buildingEntrances;

	@Override
	protected void postConnect() {
		super.postConnect();
		try {
			ClustingMap.initMap(KConstants.countOfpf, 100, model);
			cluster = ClustingMap.assignAgentToCluster(me(), model);
			if (cluster == null) {
				System.out.println("该agent没有分配到cluster");
				return;
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("聚类失败");
		}
		buildingUnexplored = cluster.getMembersID();
		roadsInCluster = cluster.getRoadsAroundCluster();
		maxWater = configuration.getMaxWater();
		maxDistance = configuration.getMaxFireExtinguishDistance();
		maxPower = configuration.getMaxFirePower();
		buildingEntrances = ClustingMap.getBuildingEntrances();

	}

	@Override
	protected void think(int time, ChangeSet changes, Collection<Command> heard) {
		if (time < configuration.getIgnoreAgentCommand())
			return;
		handleReceiveMessages(time, changes, heard);
		sendAllVoiceMessages(time);
		sendAllRadioMessages(time);
	}

	public void handleReceiveMessages(int time, ChangeSet changes, Collection<Command> heard) {
		if (time == configuration.getIgnoreAgentCommand()) {
			sendSubscribe(time, configuration.getFireChannel());// 注册了该通道的都可以接收到
		}
		
		handleHeard(time, heard);
		seenChanges.handleChanges(model, changes);
		callOtherFBHelp(time);
		addInjuredHumanInfoToMessageSend(time);
		addRoadsInfoToMessageSend(time);
		updateExtinguishedBuildings(time);
		EntityID position = positionMeStuckedIn(seenChanges.getSeenBlockades(),me());
		if(position != null) {
			Message message = new Message(MessageID.FB_STUCKED,position,time,configuration.getPoliceChannel());
			messagesWillSend.add(message);
			return;
		}
		if (me().getHP() > 0 && (me().getBuriedness() > 0 || me().getDamage() > 0)) {
			handleBuriedMe(time);
			return;
		}
/*		EntityID dest;
		if (!onFireBuildings.isEmpty()) {
			distanceSort(onFireBuildings,me());
			dest = ((EntityID[]) buildingEntrances.get(onFireBuildings.get(0)).toArray())[0];
			pathByPriotity = search.getPath(me().getPosition(), onFireBuildings.get(0), null);
		} else if (!warmBuildings.isEmpty()) {
			distanceSort(warmBuildings,me());
			dest = ((EntityID[]) buildingEntrances.get(warmBuildings.get(0)).toArray())[0];
			pathByPriotity = search.getPath(me().getPosition(), warmBuildings.get(0), null);
		}*/
		if (handleWater(time)) {
			return;
		}
		for (EntityID next : seenChanges.getBuildingsOnFire()) {
			if (model.getDistance(getID(), next) <= maxDistance) {
				Logger.info("Extinguishing " + next);
				sendExtinguish(time, next, maxPower);
				sendSpeak(time, 1, ("Extinguishing " + next).getBytes());
				return;
			}
		}
		// Plan a path to a fire
		for (EntityID next : seenChanges.getBuildingsOnFire()) {
			/*
			 * List<EntityID> path = planPathToFire(next); if (path != null) {
			 * Logger.info("Moving to target"); sendMove(time, path); return; }
			 */
		}
		List<EntityID> path = null;
		Logger.debug("Couldn't plan a path to a fire.");
		/*
		 * path = randomWalk(); Logger.info("Moving randomly"); sendMove(time,
		 * path);
		 */
	}

	private void callOtherFBHelp(int time) {
		Set<EntityID> otherFieryBuildingsIDs = seenChanges.getBuildingOnFireMore();
		Set<EntityID> fieryWorseBuildingsIDS = seenChanges.getWorseStatusBuildings();
		callForPFHelpTasks.addAll(otherFieryBuildingsIDs);
		callForPFHelpTasks.addAll(fieryWorseBuildingsIDS);
		for(EntityID id : callForPFHelpTasks) {
			Message message = new Message(MessageID.FB_NEED_COLLABORATION,id,time,configuration.getFireChannel());
			messagesWillSend.add(message);
		}
	}
	private void updateExtinguishedBuildings(int time) {
		Set<EntityID> seenExtinguishedBuildingsIDs = seenChanges.getBuildingsExtinguished();
		extinguishedBuildingsIDs.addAll(seenExtinguishedBuildingsIDs);
		for (EntityID extinguishedBuildingID : extinguishedBuildingsIDs) {
			heatingBuildingsIDs.remove(extinguishedBuildingID);
			burningBuildingsIDs.remove(extinguishedBuildingID);
			InfernoBuildingsIDs.remove(extinguishedBuildingID);
		}
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

	public void addInjuredHumanInfoToMessageSend(int time) {
		for (Human human : seenChanges.getBuriedHuman()) {
			Message message = new Message(MessageID.HUMAN_BURIED, human.getPosition(), time,
					configuration.getAmbulanceChannel());
			messagesWillSend.add(message);
		}
	}

	public void addRoadsInfoToMessageSend(int time) {
		for (Blockade blockade : seenChanges.getSeenBlockades()) {
			Message message1 = new Message(MessageID.HUMAN_BLOCKED, blockade.getPosition(), time,
					configuration.getPoliceChannel());
			messagesWillSend.add(message1);
		}
		for (Blockade blockadeStuckedHuman : seenChanges.getBlockadesHumanStuckedIn()) {
			Message message2 = new Message(MessageID.HUMAN_STUCKED, blockadeStuckedHuman.getPosition(), time,
					configuration.getPoliceChannel());
			messagesWillSend.add(message2);
		}
		for (EntityID roadID : seenChanges.getClearedRoads()) {
			Message message3 = new Message(MessageID.ROAD_CLEARED, roadID, time, configuration.getPoliceChannel());
			messagesWillSend.add(message3);
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
		case BUILDING_WARM:
			warmBuildingsIDs.add(targetID);
			break;
		case BUILDING_HEATING:
			heatingBuildingsIDs.add(targetID);
			break;
		case BUILDING_BURNING:
			burningBuildingsIDs.add(targetID);
			break;
		case BUILDING_INFERNO:
			InfernoBuildingsIDs.add(targetID);
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
		case BUILDING_WARM:
			warmBuildingsIDs.add(targetID);
			break;
		case BUILDING_HEATING:
			heatingBuildingsIDs.add(targetID);
			onFireBuildingsIDs.add(targetID);
			break;
		case BUILDING_BURNING:
			burningBuildingsIDs.add(targetID);
			onFireBuildingsIDs.add(targetID);
			break;
		case BUILDING_INFERNO:
			InfernoBuildingsIDs.add(targetID);
			onFireBuildingsIDs.add(targetID);
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

	public void addFireInfo(Set<EntityID> fireBuildings, int time) {
		for (EntityID buidingID : fireBuildings) {
			Message message = new Message(MessageID.BUILDING_ON_FIRE, buidingID, time, configuration.getFireChannel());
			messagesWillSend.add(message);
		}
	}

	public boolean isStucked() {
		if (prePosition[0] == prePosition[1] && prePosition[0] == nowPosition)
			return true;
		else
			return false;
	}

	private boolean handleWater(int time) {
		if (isfillingWater(time))
			return true;
		if (moveForWater(time))
			return true;
		return false;
	}

	private boolean isfillingWater(int time) {
		if (me().isWaterDefined() && me().getWater() < maxWater
				&& (location() instanceof Refuge || location() instanceof Hydrant)) {
			Logger.info("Filling with water at " + location());
			sendRest(time);
			return true;
		}
		return false;
	}

	public boolean moveForWater(int time) {
		// Are we out of water?
		if (me().isWaterDefined() && me().getWater() == 0) {
			if (blockadesBlockRoadTotally(time)) {
				return false;
			}
			List<EntityID> refugeIDs = new ArrayList<EntityID>(getEntrancesOfRefuges().keySet());
			distanceSort(refugeIDs, me());
			EntityID destRefugeID = ((EntityID[]) getEntrancesOfRefuges().get(refugeIDs.get(0)).toArray())[0];
			List<EntityID> result = new ArrayList<EntityID>(hydrantIDs);
			distanceSort(result,me());
			EntityID destHydrantID = result.get(0);
			int distance1 = findDistanceTo(destRefugeID,me().getX(),me().getY());
			int distance2 = findDistanceTo(destHydrantID,me().getX(),me().getY());
			List path = null;
			if(distance1 > distance2) {
				path = search.getPath(me().getPosition(), destHydrantID, null);
			}else{
				path = search.getPath(me().getPosition(), destRefugeID, null);
				if(path != null)
					path.add(refugeIDs.get(0));
			}
			// Head for a refuge
			if (path != null) {
				Logger.info("Moving to refuge or hydrant");
				sendMove(time, path);
				return true;
			} else {
				Logger.debug("Couldn't plan a path to a refuge or hydrant");
				path = randomWalkAroundCluster(roadsInCluster);
				Logger.info("Moving randomly");
				sendMove(time, path);
				return true;
			}
		}
		return false;
	}

	@Override
	protected EnumSet<StandardEntityURN> getRequestedEntityURNsEnum() {
		return EnumSet.of(StandardEntityURN.AMBULANCE_TEAM);
	}

	public void handleBuriedMe(int time) {
		Message message = new Message(MessageID.FB_BURIED, me().getID(), time, configuration.getAmbulanceChannel());
		messagesWillSend.add(message);
		sendRest(time);
		System.out.println("fb of " + me().getID() + "is buried at time " + time);
	}

	public boolean blockadesBlockRoadTotally(int time) {
		if (seenChanges.getTotallyBlockedRoad() != null) {
			List<EntityID> path = randomWalkAroundCluster(roadsInCluster);
			if (path != null) {
				sendMove(time, path);
				return true;
			}
		}
		return false;
	}
}
