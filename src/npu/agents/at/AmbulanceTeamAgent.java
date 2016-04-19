package npu.agents.at;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import npu.agents.clustering.Cluster;
import npu.agents.clustering.ClustingMap;
import npu.agents.clustering.ClustingMap3;
import npu.agents.common.AbstractCommonAgent;
import npu.agents.communication.utils.ChangeSetUtil;
import npu.agents.communication.utils.CommUtils.MessageID;
import npu.agents.model.Message;
import npu.agents.utils.DistanceSorter;
import npu.agents.utils.KConstants;
import rescuecore2.log.Logger;
import rescuecore2.messages.Command;
import rescuecore2.standard.entities.AmbulanceTeam;
import rescuecore2.standard.entities.Blockade;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.Civilian;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.Refuge;
import rescuecore2.standard.entities.Road;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.standard.messages.AKSpeak;
import rescuecore2.worldmodel.ChangeSet;
import rescuecore2.worldmodel.EntityID;

public class AmbulanceTeamAgent extends AbstractCommonAgent<AmbulanceTeam> {
	private Cluster cluster;

	private List<EntityID> pathByPriotity;
	private Set<EntityID> buildingsUnexplored;
	private Set<EntityID> buildingsExplored = new HashSet<EntityID>();
	private Set<EntityID> buriedFbPostions = new HashSet<EntityID>();
	private Set<EntityID> buriedCivilianPostions = new HashSet<EntityID>();
	private Set<EntityID> buriedPfPostions = new HashSet<EntityID>();
	private Set<EntityID> buriedHumanPostions = new HashSet<EntityID>();
	private Set<EntityID> seriouslyDamageHumans = new HashSet<EntityID>();
	private Map<EntityID, Set<EntityID>> buildingsEntrancesMap;
	private Set<EntityID> buildingAndRoadInCluster = new HashSet<EntityID>();
	private Set<EntityID> buildingsAndRoadsInCluster = new HashSet<EntityID>();
	private Set<EntityID> roadsInCluster;
	private Set<EntityID> buriedPlatoonsPositions = new HashSet<EntityID>();
	private Set<EntityID> injuredCiviliansPositions = new HashSet<EntityID>();
	private Set<EntityID> injuredPlatoonsPositions = new HashSet<EntityID>();
	private Map<EntityID, Set<EntityID>> refugesEntrancesMap;

	public ChangeSetUtil seenChanges;
	public Set<Message> messagesWillSend = new HashSet<Message>();
	
	private EntityID[] prePosition = new EntityID[2];
	private EntityID nowPosition;
	@Override
	public void postConnect() {
		super.postConnect();
		try {
			ClustingMap3.initMap(Math.min(KConstants.countOfat,configuration.getAmbulanceTeamsCount()), 100, model);
			cluster = ClustingMap3.assignAgentToCluster(me(), model);
			if (cluster == null) {
				System.out.println("该agent没有分配到cluster");
				return;
			}
			
			buildingsUnexplored = cluster.getBuildingsIDs();
			buildingsEntrancesMap = cluster.getBuildingsEntrances();
			roadsInCluster = cluster.getRoadsInCluster();
			buildingsAndRoadsInCluster.addAll(buildingsUnexplored);
			buildingsAndRoadsInCluster.addAll(roadsInCluster);
			refugesEntrancesMap = cluster.getRoadARoundRefuge();
			seenChanges = new ChangeSetUtil();
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("聚类失败");
			return;
		}
	}

	@Override
	public void think(int time, ChangeSet changes, Collection<Command> heard) {
		if (time < configuration.getIgnoreAgentCommand())
			return;
		handleReceiveMessages(time, changes, heard);
		sendMessages(time);
	}

	private void handleReceiveMessages(int time, ChangeSet changes, Collection<Command> heard) {
		if (time == configuration.getIgnoreAgentCommand()) {
			sendSubscribe(time, configuration.getAmbulanceChannel());// 注册了该通道的都可以接收到
			if (me().isHPDefined() && me().isBuriednessDefined() && me().getHP() > 0 && me().getBuriedness() > 0) {
				// TODO 暂时不管
				callForATHelp(time, MessageID.AT_BURIED);
				System.out.println(me().getID() + " is buried,hp is" + me().getHP());
			}
			return;
		}
		handleHeard(time, heard);
		seenChanges.handleChanges(model, changes);
		addBuildingInfoToMessageSend(time);
		addRoadsInfoToMessageSend(time);
		updateHelpedHuman();
		if(somethingWrong(time))
		{
		}
		EntityID position = positionWhereIStuckedIn(seenChanges.getSeenBlockades(), me());
		if (position != null) {
			Message message = new Message(MessageID.PLATOON_STUCKED, position, time, configuration.getPoliceChannel());
			messagesWillSend.add(message);
			return;
		}
		if (me().isHPDefined() && me().isDamageDefined() && me().getHP() > 0 && me().getDamage() > 0) {
			if (canMoveToRefuge(time, me(), refugesEntrancesMap)) {
			} else {
				callForATHelp(time, MessageID.PLATOON_BURIED);
			}
			System.out.println(me().getID() + " is buried,hp is" + me().getHP() + ",damage is" + me().getDamage());
		}
		List<EntityID> templist = new ArrayList<EntityID>();
		if (!buriedPlatoonsPositions.isEmpty()) {
			templist.addAll(buriedPlatoonsPositions);
			distanceSort(templist, me());
			EntityID dest = null;
			if(buildingsEntrancesMap.keySet().contains(templist.get(0))){
				dest = ((EntityID[]) buildingsEntrancesMap.get(templist.get(0)).toArray())[0];
				pathByPriotity = search.getPath(me().getPosition(), dest, null);
				if(pathByPriotity != null)
					pathByPriotity.add(templist.get(0));
			}else if(roadsInCluster.contains(templist)){
				dest = templist.get(0);
				pathByPriotity = search.getPath(me().getPosition(), dest, null);
			}
		} else if (!injuredCiviliansPositions.isEmpty()) {
			templist.addAll(injuredCiviliansPositions);
			distanceSort(templist, me());
			EntityID dest = null;
			if(buildingsEntrancesMap.keySet().contains(templist.get(0))){
				dest = ((EntityID[]) buildingsEntrancesMap.get(templist.get(0)).toArray())[0];
				pathByPriotity = search.getPath(me().getPosition(), dest, null);
				if(pathByPriotity != null)
					pathByPriotity.add(templist.get(0));
			}else if(roadsInCluster.contains(templist)){
				dest = templist.get(0);
				pathByPriotity = search.getPath(me().getPosition(), dest, null);
			}
		}

		if (!basicRescue(time)) {
			System.out.println(me().getID() + " basic rescue failed");
			List<EntityID> path = randomWalk(buildingsAndRoadsInCluster);
			if (path != null) {
				sendMove(time, path);
			}
		}
	}

	private void updateHelpedHuman() {
		if (seenChanges.getInjuredHumans().size() <= 1) {
			injuredCiviliansPositions.remove(me().getPosition());
			buriedPlatoonsPositions.remove(me().getPosition());
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
				System.out.println("at of " + me().getID() + " heard dropped message from " + info.getAgentID()
						+ " from " + info.getChannel());
				continue;
			}
			if (content.equalsIgnoreCase("Ouch") || content.equalsIgnoreCase("Help")) {
				Civilian civilian = (Civilian) model.getEntity(agentID);
				EntityID civilianPositionID = civilian.getPosition();
				if (civilian.isHPDefined() && civilian.isBuriednessDefined() && civilian.isDamageDefined()
						&& civilian.getHP() > 0 && (civilian.getBuriedness() > 0 || civilian.getDamage() > 0)) {
					buriedCivilianPostions.add(civilianPositionID);
				} else {
					Message message = new Message(MessageID.CIVILIAN_BlOCKED, civilianPositionID, time,
							configuration.getPoliceChannel());
					messagesWillSend.add(message);
				}
				System.out.println("at of " + me().getID() + "handle cicilian help");
				continue;
			}
			String[] heardInfo = content.split(",");
			String typeOfhearedInfo = heardInfo[0];
			String positionOfInfo = heardInfo[1];
			int id = Integer.parseInt(typeOfhearedInfo);
			int postion = Integer.parseInt(positionOfInfo);
			EntityID postionID = new EntityID(postion);
			MessageID messageID = MessageID.values()[id];
			if (!buildingsAndRoadsInCluster.contains(postionID))
				continue;
			handleMessage(postionID, messageID);
		}
	}

	private void handleMessage(EntityID positionID, MessageID messageID) {
		switch (messageID) {
		case PLATOON_BURIED:
			buriedPlatoonsPositions.add(positionID);
			break;
		case CIVILIAN_INJURED:
			injuredCiviliansPositions.add(positionID);
			break;
		default:
			break;
		}
	}

	private boolean basicRescue(int time) {
		if (handleNowRescue(time))
			return true;
		for (Human next : getSeenTargets()) {
			if (prepareRescue(next, time)) {
				return true;
			} else {
				if (blockadesBlockRoadTotally(time)) {
					return false;
				}
				EntityID targetPositionID = next.getPosition();
				StandardEntity targetPosition = model.getEntity(targetPositionID);
				EntityID dest = null;
				if (targetPosition instanceof Building) {
					dest = ((EntityID[]) buildingsEntrancesMap.get(targetPosition.getID()).toArray())[0];
				}
				if (dest == null)
					return false;
				List<EntityID> path = search.getPath(me().getPosition(), dest, null);
				if (path != null) {
					Logger.info("Moving to target");
					path.add(targetPositionID);
					sendMove(time, path);
					return true;
				}
			}
		}
		if (pathByPriotity != null) {
			sendMove(time, pathByPriotity);
			return true;
		}
		return false;
	}

	private boolean handleNowRescue(int time) {
		if (isRescuing(seenChanges)) {
			// Am I at a refuge?
			if (location() instanceof Refuge) {
				// Unload!
				System.out.println(me().getID() + " is Unloading");
				sendUnload(time);
				return true;
			} else {
				// Move to a refuge
				List<EntityID> refugeIDs = new ArrayList<EntityID>(getEntrancesOfRefuges().keySet());
				distanceSort(refugeIDs, me());
				EntityID dest = ((EntityID[]) getEntrancesOfRefuges().get(refugeIDs.get(0)).toArray())[0];
				List<EntityID> path = search.getPath(me().getPosition(), dest, null);
				path.add(refugeIDs.get(0));
				if (path != null) {
					Logger.info("Moving to refuge");
					sendMove(time, path);
					return true;
				}
				// What do I do now? Might as well carry on and see if we can
				// dig someone else out.
				System.out.println("Failed to plan path to refuge");

			}
		}
		return false;
	}

	private boolean isRescuing(ChangeSetUtil seen) {
		for (Human next : seenChanges.getInjuredHumans()) {
			if (next.getPosition().equals(getID())) {
				System.out.println(next + " is on board of " + me().getID());
				return true;
			}
		}
		return false;
	}

	private boolean prepareRescue(Human next, int time) {
		if (next.getPosition().equals(location().getID())) {
			// Targets in the same place might need rescueing or loading
			if ((next instanceof Human) && next.getBuriedness() == 0 && !(location() instanceof Refuge)) {
				// Load
				System.out.println(me().getID() + "is Loading " + next);
				sendLoad(time, next.getID());
				return true;
			}
			if (next.getBuriedness() > 0) {
				// Rescue
				System.out.println(me().getID() + "is Rescueing " + next);
				sendRescue(time, next.getID());
				return true;
			}
		}
		return false;
	}

	private List<Human> getSeenTargets() {
		List<Human> targets = new ArrayList<Human>(seenChanges.getInjuredHumans());
		Collections.sort(targets, new DistanceSorter(location(), model));
		return targets;
	}

	@Override
	public EnumSet<StandardEntityURN> getRequestedEntityURNsEnum() {
		return EnumSet.of(StandardEntityURN.AMBULANCE_TEAM);
	}

	public boolean blockadesBlockRoadTotally(int time) {
		if (seenChanges.getTotallyBlockedMainRoad() != null) {
			List<EntityID> path = randomWalk(buildingsAndRoadsInCluster);
			if (path != null) {
				sendMove(time, path);
				return true;
			}
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
	public void addRoadsInfoToMessageSend(int time) {
		for (Blockade blockade : seenChanges.getSeenBlockades()) {
			Message message = new Message(MessageID.PLATOON_BLOCKED, blockade.getID(), time,
					configuration.getPoliceChannel());
			messagesWillSend.add(message);
		}
		for (Road road : seenChanges.getTotallyBlockedMainRoad()) {
			Message message = new Message(MessageID.MAIN_ROAD_BLOCKED_TOTALLY, road.getID(), time,
					configuration.getPoliceChannel());
			messagesWillSend.add(message);
		}
		for(Road road : seenChanges.getTotallyBlockedBuildingEntrance()) {
			Message message = new Message(MessageID.ENTRANCE_BLOCKED_TOTALLY, road.getID(), time,
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
	public void addBuildingInfoToMessageSend(int time) {
		
		/* * for (EntityID unburntBuidingID : seenChanges.getBuildingsUnburnt()) {
		 * Message message = new Message(MessageID.BUILDING_UNBURNT,
		 * unburntBuidingID, time, configuration.getFireChannel());
		 * messagesWillSend.add(message); }*/
		 
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
