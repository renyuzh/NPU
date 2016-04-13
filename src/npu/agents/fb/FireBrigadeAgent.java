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

public class FireBrigadeAgent extends AbstractCommonAgent<FireBrigade>{
	private Cluster cluster;
	private Set<EntityID> buildingUnexplored = new HashSet<EntityID>();
	private Set<Message> messagesWillSend = new HashSet<Message>();

	private EntityID[] prePosition = new EntityID[2];
	private EntityID nowPosition;

	private Map<EntityID, Set<EntityID>> roadsAroundRefuge;
	private List<EntityID> pathByPriotity;
	private List<EntityID> onFireBuildings = new ArrayList<EntityID>();
	private List<EntityID> warmBuildings = new ArrayList<EntityID>();
	private List<EntityID> unburntBuildings = new ArrayList<EntityID>();
    private int maxWater;
    private int maxDistance;
    private int maxPower;
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
		 maxWater = configuration.getMaxWater();
	     maxDistance = configuration.getMaxFireExtinguishDistance();
	     maxPower = configuration.getMaxFirePower();
	}
	@Override
	protected void think(int time, ChangeSet changes, Collection<Command> heard) {
		if (time < configuration.getIgnoreAgentCommand())
			return;
		handleReceiveMessages(time, changes, heard);
		sendAllVoiceMessages(time);
		sendAllRadioMessages(time);
	}

	public void handleReceiveMessages(int time,ChangeSet changes,Collection<Command> heard){
		if (time == configuration.getIgnoreAgentCommand()) {
			sendSubscribe(time, configuration.getFireChannel());// 注册了该通道的都可以接收到
		}
		handleHeard(time,heard);
		ChangeSetUtil seenChanges = new ChangeSetUtil();
		seenChanges.handleChanges(model, changes);
		addInjuredHumanInfoToMessageSend(time);
		addRoadsInfoToMessageSend(time);
		updateExtinguishedBuildings(time);
		prePosition[1] = prePosition[0];
		prePosition[0] = nowPosition;
		nowPosition = me().getPosition();
		if (me().getHP() > 0 && (me().getBuriedness() > 0 || me().getDamage() > 0)) {
			handleBuriedMe(time);
			return;
		}
		if (!onFireBuildings.isEmpty()) {
			distanceSort(onFireBuildings);
			pathByPriotity = search.getPath(me().getPosition(), onFireBuildings.get(0), null);
		} else if (!warmBuildings.isEmpty()) {
			distanceSort(warmBuildings);
			pathByPriotity = search.getPath(me().getPosition(), warmBuildings.get(0), null);
		} 
		basicExtinguish(time);
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
	        /*    List<EntityID> path = planPathToFire(next);
	            if (path != null) {
	                Logger.info("Moving to target");
	                sendMove(time, path);
	                return;
	            }*/
	        }
	        List<EntityID> path = null;
	        Logger.debug("Couldn't plan a path to a fire.");
	        /*path = randomWalk();
	        Logger.info("Moving randomly");
	        sendMove(time, path);*/
	}
	private void updateExtinguishedBuildings(int time) {
		Set<EntityID> extinguishedBuildingsIDs = seenChanges.getBuildingsExtinguished();
		for (EntityID extinguishedBuildingID : extinguishedBuildingsIDs) {
			if (onFireBuildings.contains(extinguishedBuildingID)) {
				onFireBuildings.remove(extinguishedBuildingID);
			}
			if (warmBuildings.contains(extinguishedBuildingID)) {
				warmBuildings.remove(extinguishedBuildingID);
			}
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
		for (Blockade blockadeStuckedHuman : seenChanges.getBlockadesHumanStucked()) {
			Message message2 = new Message(MessageID.HUMAN_STUCKED, blockadeStuckedHuman.getPosition(), time,
					configuration.getPoliceChannel());
			messagesWillSend.add(message2);
		}
		for (EntityID roadID : seenChanges.getClearedRoads()) {
			Message message3 = new Message(MessageID.ROAD_CLEARED, roadID, time, configuration.getPoliceChannel());
			messagesWillSend.add(message3);
		}
	}
	private void handleHeard(int time,Collection<Command> heards) {
		for (Command heard : heards) {
			if (!(heard instanceof AKSpeak))
				continue;
			AKSpeak info = (AKSpeak) heard;
			EntityID agentID = info.getAgentID();
			String content = new String(info.getContent());
			if (content.isEmpty()) {
				System.out.println("pf of " + me().getID() + " heard dropped message from " + info.getAgentID()
						+ " from " + info.getChannel());
				continue;
			}
			//居民发出求救信息，要么受伤，要么被困
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
				System.out.println("pf of " + me().getID() + "handle cicilian help");
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
			if (onFireBuildings.contains(postionID))
				continue;
			if (warmBuildings.contains(postionID))
				continue;
			if (messageID.equals(MessageID.BUILDING_UNBURNT) && unburntBuildings.contains(postionID))
				continue;
			if (configuration.getRadioChannels().contains(info.getChannel()))
				handleRadio(postionID, messageID);
			else
				handleVoice(postionID, messageID);
		}
	}

	private void handleVoice(EntityID postionID, MessageID messageID) {
		switch (messageID) {
		case BUILDING_ON_FIRE:
			onFireBuildings.add(postionID);
			break;
		case BUILDING_IS_WARM:
			warmBuildings.add(postionID);
			break;
		case BUILDING_UNBURNT:
			unburntBuildings.add(postionID);
			break;
		default:
			break;
		}
	}

	private void handleRadio(EntityID postionID, MessageID messageID) {
		switch (messageID) {
		case BUILDING_ON_FIRE:
			onFireBuildings.add(postionID);
			break;
		case BUILDING_IS_WARM:
			warmBuildings.add(postionID);
			break;
		case BUILDING_UNBURNT:
			unburntBuildings.add(postionID);
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

	private boolean basicExtinguish(int time) {
		if (isfillingWater(time))
			return true;
		if(moveForWater(time))
			return true;
		if(pathByPriotity !=null){
			sendMove(time,pathByPriotity);
			return true;
		}
	      return false;
	}
	private boolean isfillingWater(int time) {
        if (me().isWaterDefined() && me().getWater() < maxWater && (location() instanceof Refuge || location() instanceof Hydrant)) {
            Logger.info("Filling with water at " + location());
            sendRest(time);
            return true;
        }
        return false;
	}
	
	private boolean moveForWater(int time) {
	     // Are we out of water?
        if (me().isWaterDefined() && me().getWater() == 0) {
        	Set<EntityID> refugeIDs =  getRoadsAroundRefuges().keySet();
            refugeIDs.addAll(hydrantIDs);
            List<EntityID> refugeAndHydrantIDs = new ArrayList<EntityID>(refugeIDs);
            distanceSort(refugeAndHydrantIDs);
            // Head for a refuge
            List<EntityID> path = search.getPath(me().getPosition(), refugeAndHydrantIDs.get(0), null);
            if (path != null) {
                Logger.info("Moving to refuge or hydrant");
                sendMove(time, path);
                return true;
            }
            else {
                Logger.debug("Couldn't plan a path to a refuge or hydrant");
                path = randomWalkAroundCluster(buildingUnexplored);
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
	private void distanceSort(List<EntityID> targets) {
		Collections.sort(targets, new Comparator<EntityID>() {
			@Override
			public int compare(EntityID o1, EntityID o2) {
				int distance1 = findDistanceTo(o1, me().getX(), me().getY());
				int distance2 = findDistanceTo(o2, me().getX(), me().getY());
				if (distance1 > distance2)
					return 1;
				if (distance1 < distance2)
					return -1;
				return 0;
			}
		});
	}
	private int findDistanceTo(EntityID id, int x, int y) {
		StandardEntity entity = model.getEntity(id);
		Pair<Integer, Integer> pair = entity.getLocation(model);
		return (int) Math.hypot(pair.first() - x, pair.second() - y);
	}
	public void handleBuriedMe(int time) {
		Message message = new Message(MessageID.FB_BURIED, me().getID(), time, configuration.getAmbulanceChannel());
		messagesWillSend.add(message);
		sendRest(time);
		System.out.println("fb of " + me().getID() + "is buried at time " + time);
	}
}
