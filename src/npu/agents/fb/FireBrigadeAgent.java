package npu.agents.fb;

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
import npu.agents.common.AbstractCommonAgent;
import npu.agents.communication.model.Message;
import npu.agents.communication.utils.ChangeSetUtil;
import npu.agents.communication.utils.CommUtils.MessageID;
import npu.agents.utils.DistanceSorter;
import npu.agents.utils.KConstants;
import rescuecore2.log.Logger;
import rescuecore2.messages.Command;
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
	private int distance;
	private Cluster cluster;
	private Set<EntityID> roadsInMyCluster = new HashSet<EntityID>();
	private Set<EntityID> roadsHasCleared = new HashSet<EntityID>();
	private Set<EntityID> roadsID;
	private List<EntityID> firingBuildings = new ArrayList<EntityID>();
	private List<EntityID> stuckingHumans = new ArrayList<EntityID>();
	private List<EntityID> nearbyStuckingHumans = new ArrayList<EntityID>();
	private List<EntityID> nearbyfiringBuildings = new ArrayList<EntityID>();
	private List<EntityID> nearbyInjuredHuman = new ArrayList<EntityID>();
	private List<EntityID> farInjuredHuman = new ArrayList<EntityID>();
	private List<EntityID> refugeStuck = new ArrayList<EntityID>();

	private Set<Message> messagesSend = new HashSet<Message>();

	private EntityID[] prePosition = {};
	private EntityID nowPosition;

	private Map<EntityID, Set<EntityID>> roadsAroundRefuge;
	private List<EntityID> pathByPriotity;
	
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
		 maxWater = configuration.getMaxWater();
	     maxDistance = configuration.getMaxFireExtinguishDistance();
	     maxPower = configuration.getMaxFirePower();
	}
	@Override
	protected void think(int time, ChangeSet changes, Collection<Command> heard) {
		if (time < configuration.getIgnoreAgentCommand())
			return;
		if (time == configuration.getIgnoreAgentCommand()) {
			sendSubscribe(time, configuration.getFireChannel());// 注册了该通道的都可以接收到
		}
		handleHeared(heard);
		ChangeSetUtil seenChanges = new ChangeSetUtil();
		seenChanges.handleChanges(model, changes);
		//addFireInfo(seenChanges.getFireBuildings(), time);
		//addInjuredHumanInfo(seenChanges.getInjuredHuman(), time);
		prePosition[1] = prePosition[0];
		prePosition[0] = nowPosition;
		nowPosition = me().getPosition();
		if (me().getHP() > 0 && (me().getBuriedness() > 0 || me().getDamage() > 0)) {
			handleInjuredMe(time);
			return;
		}
		basicExtinguish(time,seenChanges);
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
		sendAllVoiceMessages();
		sendAllRadioMessages();
	}

	private void sendAllRadioMessages() {
		
	}
	private void sendAllVoiceMessages() {
		
	}
	private void removeFinishedTask(EntityID nowPosition2) {
		if (refugeStuck.contains(nowPosition))
			refugeStuck.remove(nowPosition);
		if (stuckingHumans.contains(nowPosition))
			stuckingHumans.remove(nowPosition);
		if (firingBuildings.contains(nowPosition))
			firingBuildings.remove(nowPosition);
	}
	
	private void handleHeared(Collection<Command> heards) {
		for (Command heard : heards) {
			if (!(heard instanceof AKSpeak))
				continue;
			AKSpeak info = (AKSpeak) heard;
			String content = new String(info.getContent());
			if (content.isEmpty())
				continue;
			String[] heardInfo = content.split(",");
			String typeOfhearedInfo = heardInfo[0];
			int id = Integer.parseInt(typeOfhearedInfo);
			MessageID messageID = MessageID.values()[id];
			EntityID entityID = info.getAgentID();
			if (configuration.getRadioChannels().contains(info.getChannel()))
				handleRadio(entityID, messageID);
			else
				handleVoice(entityID, messageID);
		}
	}

	private void handleVoice(EntityID voice, MessageID messageID) {
		switch (messageID) {
		case HUMAN_STUCKED:
			nearbyStuckingHumans.add(voice);
			break;
		case BUILDING_ON_FIRE:
			nearbyfiringBuildings.add(voice);
			break;
		case HUMAN_INJURED:
			nearbyInjuredHuman.add(voice);
		default:
			break;
		}
	}

	private void handleRadio(EntityID radio, MessageID messageID) {
		switch (messageID) {
		case HUMAN_STUCKED:
			stuckingHumans.add(radio);
			break;
		case BUILDING_ON_FIRE:
			firingBuildings.add(radio);
			break;
		case HUMAN_INJURED:
			farInjuredHuman.add(radio);
		default:
			break;
		}
	}

	public void addFireInfo(Set<EntityID> fireBuildings, int time) {
		for (EntityID buidingID : fireBuildings) {
			Message message = new Message(MessageID.BUILDING_ON_FIRE, buidingID, time, configuration.getFireChannel());
			messagesSend.add(message);
		}
	}

	public void addInjuredHumanInfo(Set<EntityID> injuredHuman, int time) {
		for (EntityID humanID : injuredHuman) {
			Message message = new Message(MessageID.HUMAN_INJURED, humanID, time, configuration.getAmbulanceChannel());
			messagesSend.add(message);
		}
	}

	public void handleInjuredMe(int time) {
		Message message = new Message(MessageID.HUMAN_INJURED, me().getPosition(), time, configuration.getAmbulanceChannel());
		messagesSend.add(message);
		sendRest(time);
	}

	public boolean isStucked() {
		if (prePosition[0] == prePosition[1] && prePosition[0] == nowPosition)
			return true;
		else
			return false;
	}

	private boolean basicExtinguish(int time, ChangeSetUtil seenChanges) {
		if (isfillingWater(time))
			return true;
		if(moveForWater(time))
			return true;
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
            // Head for a refuge
            List<EntityID> path = search.breadthFirstSearch(me().getPosition(), refugeIDs);
            if (path != null) {
                Logger.info("Moving to refuge");
                sendMove(time, path);
                return true;
            }
            else {
                Logger.debug("Couldn't plan a path to a refuge.");
                path = randomWalkAroundCluster(roadsID);
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

}
