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
import npu.agents.common.AbstractCommonAgent;
import npu.agents.communication.model.Message;
import npu.agents.communication.utils.CommUtils.MessageID;
import npu.agents.utils.KConstants;
import rescuecore2.log.Logger;
import rescuecore2.messages.Command;
import rescuecore2.standard.entities.Civilian;
import rescuecore2.standard.entities.FireBrigade;
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
		buildingUnexplored = cluster.getBuildingsIDs();
		roadsInCluster = cluster.getRoadsInCluster();
		maxWater = configuration.getMaxWater();
		maxDistance = configuration.getMaxFireExtinguishDistance();
		maxPower = configuration.getMaxFirePower();
		buildingEntrances = ClustingMap.getBuildingEntrances();
		refugesEntrancesMap = cluster.getRoadARoundRefuge();

	}

	@Override
	protected void think(int time, ChangeSet changes, Collection<Command> heard) {
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
				callForATHelp(time, MessageID.AT_BURIED);
				System.out.println(me().getID() + " is buried,hp is" + me().getHP());
			}
			return;
		}
		handleHeard(time, heard);
		seenChanges.handleChanges(model, changes);
		callOtherFBHelp(time);
		addInjuredHumanInfoToMessageSend(time);
		addRoadsInfoToMessageSend(time);
		updateExtinguishedBuildings(time);
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
		if (isFillingWater(time))
			return;
		if (roadBlockedTotally(time)) {
			sendRest(time);
			return;
		}
		if (moveForWater(time)) {
			return;
		}
		if (handleSeenFieryBuildings(time))
			return;
		if (handleHeardFieryBuildings(time)) {
			return;
		}
		distancesToHelpOfotherFBs.clear();
		/*
		 * if (changeClusterToHelpOtherFB(time)) {
		 * 
		 * }
		 */
		List<EntityID> path = randomWalk(roadIDs);
		if (path != null)
			sendMove(time, path);

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
		callForFBHelpTasks.addAll(otherFieryBuildingsIDs);
		callForFBHelpTasks.addAll(fieryWorseBuildingsIDS);
		for (EntityID id : callForFBHelpTasks) {
			Message message = new Message(MessageID.FB_NEED_COLLABORATION, id, time, configuration.getFireChannel());
			messagesWillSend.add(message);
		}
	}

	private boolean handleSeenFieryBuildings(int time) {
		if (me().getWater() == 0)
			return false;
		List<EntityID> fieryBuildingsIDs = new ArrayList<>(seenChanges.getBuildingsOnFire());
		distanceSort(fieryBuildingsIDs, me());
		for (EntityID next : fieryBuildingsIDs) {
			if (model.getDistance(getID(), next) <= maxDistance) {
				Logger.info("Extinguishing " + next);
				sendExtinguish(time, next, Math.min(me().getWater(), maxPower));
				sendSpeak(time, 1, ("Extinguishing " + next).getBytes());
				return true;
			} else {
				EntityID destID = ((EntityID[]) buildingEntrances.get(next).toArray())[0];
				List<EntityID> path = search.getPath(me().getPosition(), destID, null);
				if (path != null) {
					Road road = (Road) model.getEntity(destID);
					sendMove(time, path, road.getX(), road.getY());
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

	private boolean handleWater(int time) {
		if (isFillingWater(time))
			return true;
		if (moveForWater(time))
			return true;
		return false;
	}

	private boolean isFillingWater(int time) {
		if (me().isWaterDefined() && me().getWater() < maxWater
				&& (location() instanceof Refuge || location() instanceof Hydrant)) {
			sendRest(time);
			return true;
		}
		return false;
	}

	public boolean moveForWater(int time) {
		// Are we out of water?
		if (me().isWaterDefined() && me().getWater() == 0) {
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
					return true;
				}
			} else {
				path = search.getPath(me().getPosition(), destRefugeID, null);
				if (path != null) {
					path.add(refugeIDs.get(0));
					Refuge refuge = (Refuge) model.getEntity(refugeIDs.get(0));
					sendMove(time, path, refuge.getX(), refuge.getY());
					return true;
				}
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

	public boolean roadBlockedTotally(int time) {
		Set<Road> totallyBlockedRoads = seenChanges.getTotallyBlockedRoad();
		for (Road totallyBlockedRoad : totallyBlockedRoads) {
			Message message = new Message(MessageID.ROAD_BLOCKED_TOTALLY, totallyBlockedRoad.getID(), time,
					configuration.getPoliceChannel());
			messagesWillSend.add(message);
			return true;
		}
		return false;
	}
}
