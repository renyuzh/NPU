package npu.agents.at;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
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
import npu.agents.utils.Point;
import rescuecore2.config.Config;
import rescuecore2.connection.Connection;
import rescuecore2.log.Logger;
import rescuecore2.messages.Command;
import rescuecore2.messages.control.KASense;
import rescuecore2.misc.Pair;
import rescuecore2.misc.geometry.GeometryTools2D;
import rescuecore2.misc.geometry.Line2D;
import rescuecore2.misc.geometry.Point2D;
import rescuecore2.misc.geometry.Vector2D;
import rescuecore2.standard.entities.AmbulanceTeam;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.Blockade;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.Civilian;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.Refuge;
import rescuecore2.standard.entities.Road;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.standard.entities.StandardWorldModel;
import rescuecore2.standard.messages.AKSpeak;
import rescuecore2.worldmodel.ChangeSet;
import rescuecore2.worldmodel.Entity;
import rescuecore2.worldmodel.EntityID;

public class AmbulanceTeamAgent extends AbstractCommonAgent<AmbulanceTeam> {
	private Cluster cluster;

	private Set<Message> messagesWillSend = new HashSet<Message>();

	private EntityID[] prePosition = new EntityID[2];
	private EntityID nowPosition;

	private List<EntityID> pathByPriotity;
	private Set<EntityID> buildingUnexplored = new HashSet<EntityID>();
	private List<EntityID> buriedFbPostions = new ArrayList<EntityID>();
	private List<EntityID> buriedCivilianPostions = new ArrayList<EntityID>();
	private List<EntityID> buriedPfPostions = new ArrayList<EntityID>();
	private List<EntityID> buriedHumanPostions = new ArrayList<EntityID>();
	private List<EntityID> seriouslyDamageHumans = new ArrayList<EntityID>();
	private Map<EntityID, Set<EntityID>> buildingEntrances;

	@Override
	protected void postConnect() {
		super.postConnect();
		try {
			ClustingMap.initMap(KConstants.countOfat, 100, model);
			cluster = ClustingMap.assignAgentToCluster(me(), model);
			buildingEntrances = ClustingMap.getBuildingEntrances();
			if (cluster == null) {
				System.out.println("该agent没有分配到cluster");
				return;
			}
			buildingUnexplored = cluster.getMembersID();
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("聚类失败");
		}
	}

	@Override
	protected void think(int time, ChangeSet changes, Collection<Command> heard) {
		if (time < configuration.getIgnoreAgentCommand())
			return;
		handleReceiveMessages(time, changes, heard);
		sendAllVoiceMessages(time);
		sendAllRadioMessages(time);
	}

	private void handleReceiveMessages(int time, ChangeSet changes, Collection<Command> heard) {
		if (time == configuration.getIgnoreAgentCommand()) {
			sendSubscribe(time, configuration.getAmbulanceChannel());// 注册了该通道的都可以接收到
			if (me().isHPDefined() && me().isBuriednessDefined() && me().getHP() > 0 && me().getBuriedness() > 0) {
				handleBuriedMe(time);
				return;
			}
		}
		handleHeard(time, heard);
		ChangeSetUtil seenChanges = new ChangeSetUtil();
		seenChanges.handleChanges(model, changes);
		addBuildingInfoToMessageSend(time);
		addRoadsInfoToMessageSend(time);
		prePosition[1] = prePosition[0];
		prePosition[0] = nowPosition;
		nowPosition = me().getPosition();
		if (me().getHP() > 0 && (me().getBuriedness() > 0 || me().getDamage() > 0)) {
			handleBuriedMe(time);
			return;
		}
		if (!buriedHumanPostions.isEmpty()) {
			distanceSort(buriedHumanPostions, me());
			EntityID dest = ((EntityID[]) buildingEntrances.get(buriedHumanPostions.get(0)).toArray())[0];
			pathByPriotity = search.getPath(me().getPosition(), dest, null);
			if (pathByPriotity != null)
				pathByPriotity.add(buriedHumanPostions.get(0));
		}

		basicRescue(time);
	}

	private void handleBuriedMe(int time) {
		Message message = new Message(MessageID.AT_BURIED, me().getID(), time, configuration.getAmbulanceChannel());
		messagesWillSend.add(message);
		sendRest(time);
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

	public void addBuildingInfoToMessageSend(int time) {
		for (EntityID unburntBuidingID : seenChanges.getBuildingsUnburnt()) {
			Message message1 = new Message(MessageID.BUILDING_UNBURNT, unburntBuidingID, time,
					configuration.getFireChannel());
			messagesWillSend.add(message1);
		}
		for (EntityID extinguishBuildingID : seenChanges.getBuildingsExtinguished()) {
			Message message2 = new Message(MessageID.BUILDING_EXTINGUISHED, extinguishBuildingID, time,
					configuration.getFireChannel());
			messagesWillSend.add(message2);
		}
		for (EntityID warmBuidingID : seenChanges.getBuildingsIsWarm()) {
			Message message3 = new Message(MessageID.BUILDING_UNBURNT, warmBuidingID, time,
					configuration.getFireChannel());
			messagesWillSend.add(message3);
		}
		for (EntityID onFireBuildingID : seenChanges.getBuildingsOnFire()) {
			Message message4 = new Message(MessageID.BUILDING_EXTINGUISHED, onFireBuildingID, time,
					configuration.getFireChannel());
			messagesWillSend.add(message4);
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
				System.out.println("am of " + me().getID() + " heard dropped message from " + info.getAgentID()
						+ " from " + info.getChannel());
				continue;
			}
			if (content.equalsIgnoreCase("Ouch") || content.equalsIgnoreCase("Help")) {
				Civilian civilian = (Civilian) model.getEntity(agentID);
				EntityID civilianPositionID = civilian.getPosition();
				if (civilian.isHPDefined() && civilian.isBuriednessDefined() && civilian.isDamageDefined()
						&& civilian.getHP() > 0 && civilian.getBuriedness() > 0) {
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
			if (!buildingUnexplored.contains(postionID))
				continue;
			/*
			 * if(buriedFbPostions.contains(postionID)) continue;
			 * if(buriedCivilianPostions.contains(postionID)) continue;
			 * if(buriedPfPostions.contains(postionID)) continue;
			 */
			if (buriedHumanPostions.contains(postionID))
				continue;
			if (seriouslyDamageHumans.contains(postionID))
				continue;
			if (configuration.getRadioChannels().contains(info.getChannel()))
				handleRadio(postionID, messageID);
			else
				handleVoice(postionID, messageID);
		}
	}

	private void handleVoice(EntityID postionID, MessageID messageID) {
		switch (messageID) {
		/*
		 * case FB_BURIED: buriedFbPostions.add(postionID); break; case
		 * CIVILIAN_BURIED: buriedCivilianPostions.add(postionID); break; case
		 * PF_BURIED: buriedPfPostions.add(postionID); break; case
		 * HUMAN_INJURED_SERIOUSLY: seriouslyDamageHumans.add(postionID); break;
		 */
		case HUMAN_BURIED:
			buriedHumanPostions.add(postionID);
			break;
		case HUMAN_INJURED_SERIOUSLY:
			seriouslyDamageHumans.add(postionID);
			break;
		default:
			break;
		}
	}

	private void handleRadio(EntityID postionID, MessageID messageID) {
		switch (messageID) {
		/*
		 * case FB_BURIED: buriedFbPostions.add(postionID); break; case
		 * CIVILIAN_BURIED: buriedCivilianPostions.add(postionID); break; case
		 * PF_BURIED: buriedPfPostions.add(postionID); break;
		 */
		case HUMAN_BURIED:
			buriedHumanPostions.add(postionID);
			break;
		case HUMAN_INJURED_SERIOUSLY:
			seriouslyDamageHumans.add(postionID);
			break;
		default:
			break;
		}
	}

	public void handleInjuredMe(int time) {
		Message message = new Message(MessageID.HUMAN_BURIED, me().getPosition(), time,
				configuration.getAmbulanceChannel());
		messagesWillSend.add(message);
		sendRest(time);
	}

	private boolean basicRescue(int time) {
		if (handleNowRescue(time))
			return true;
		for (Human next : getTargets()) {
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
					dest = ((EntityID[]) buildingEntrances.get(targetPosition.getID()).toArray())[0];
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
				Logger.info("Unloading");
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
				Logger.debug("Failed to plan path to refuge");

			}
		}
		return false;
	}

	private boolean isRescuing(ChangeSetUtil seen) {
		for (Human next : seen.getInjuredHuman()) {
			if (next.getPosition().equals(getID())) {
				Logger.debug(next + " is on board");
				return true;
			}
		}
		return false;
	}

	private boolean prepareRescue(Human next, int time) {
		if (next.getPosition().equals(location().getID())) {
			buriedHumanPostions.remove(next.getPosition());
			// Targets in the same place might need rescueing or loading
			if ((next instanceof Human) && next.getBuriedness() == 0 && !(location() instanceof Refuge)) {
				// Load
				Logger.info("Loading " + next);
				sendLoad(time, next.getID());
				return true;
			}
			if (next.getBuriedness() > 0) {
				// Rescue
				Logger.info("Rescueing " + next);
				sendRescue(time, next.getID());
				return true;
			}
		}
		return false;
	}

	private List<Human> getTargets() {
		List<Human> targets = new ArrayList<Human>(seenChanges.getInjuredHuman());
		Collections.sort(targets, new DistanceSorter(location(), model));
		return targets;
	}

	@Override
	protected EnumSet<StandardEntityURN> getRequestedEntityURNsEnum() {
		return EnumSet.of(StandardEntityURN.AMBULANCE_TEAM);
	}

	public boolean blockadesBlockRoadTotally(int time) {
		if (seenChanges.getTotallyBlockedRoad() != null) {
			List<EntityID> path = randomWalkAroundRoadsOnly(null);
			if (path != null) {
				sendMove(time, path);
				return true;
			}
		}
		return false;
	}
}
