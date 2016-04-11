package npu.agents.at;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
import rescuecore2.misc.geometry.GeometryTools2D;
import rescuecore2.misc.geometry.Line2D;
import rescuecore2.misc.geometry.Point2D;
import rescuecore2.misc.geometry.Vector2D;
import rescuecore2.standard.entities.AmbulanceTeam;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.Blockade;
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
	


	@Override
	protected void postConnect() {
		super.postConnect();
		try {
			ClustingMap.initMap(KConstants.countOfat, 100, model);
			cluster = ClustingMap.assignAgentToCluster(me(), model);
			if (cluster == null) {
				System.out.println("该agent没有分配到cluster");
				return;
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("聚类失败");
		}
	}
	@Override
	protected void think(int time, ChangeSet changes, Collection<Command> heard) {
		if (time < configuration.getIgnoreAgentCommand())
			return;
		if (time == configuration.getIgnoreAgentCommand()) {
			sendSubscribe(time, configuration.getAmbulanceChannel());// 注册了该通道的都可以接收到
		}
		handleHeared(heard);
		ChangeSetUtil seenChanges = new ChangeSetUtil();
		seenChanges.handleChanges(model, changes);
		addFireInfo(seenChanges.getBuildingsOnFire(), time);
		//addInjuredHumanInfo(seenChanges.getInjuredHuman(), time);
		prePosition[1] = prePosition[0];
		prePosition[0] = nowPosition;
		nowPosition = me().getPosition();
		if (me().getHP() > 0 && (me().getBuriedness() > 0 || me().getDamage() > 0)) {
			handleInjuredMe(time);
			return;
		}
		basicRescue(time,seenChanges);
		sendAllVoiceMessages();
		sendAllRadioMessages();
	}

	private void sendAllRadioMessages() {
		// TODO Auto-generated method stub
		
	}
	private void sendAllVoiceMessages() {
		// TODO Auto-generated method stub
		
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

	private boolean basicRescue(int time, ChangeSetUtil seenChanges) {
		if (handleNowRescue(time,seenChanges))
			return true;;
	      for (Human next : getTargets()) {
	            if (prepareRescue(next,time)) {
	               return true;
	            }
	            else {
	                // Try to move to the target
	                List<EntityID> path = search.breadthFirstSearch(me().getPosition(), next.getPosition());
	                if (path != null) {
	                    Logger.info("Moving to target");
	                    sendMove(time, path);
	                    return true;
	                }
	            }
	        }
	      return false;
	}
	
	private boolean handleNowRescue(int time,ChangeSetUtil seen) {
		if(isRescuing(seen)){
			 // Am I at a refuge?
            if (location() instanceof Refuge) {
                // Unload!
                Logger.info("Unloading");
                sendUnload(time);
                return true;
            }
            else {
                // Move to a refuge
                List<EntityID> path = search.breadthFirstSearch(me().getPosition(), refugeIDs);
                if (path != null) {
                    Logger.info("Moving to refuge");
                    sendMove(time, path);
                    return true;
                }
                // What do I do now? Might as well carry on and see if we can dig someone else out.
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

    private boolean prepareRescue(Human next,int time) {
    	 if (next.getPosition().equals(location().getID())) {
             // Targets in the same place might need rescueing or loading
             if ((next instanceof Civilian) && next.getBuriedness() == 0 && !(location() instanceof Refuge)) {
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
        List<Human> targets = new ArrayList<Human>();
        for (StandardEntity next : model.getEntitiesOfType(StandardEntityURN.CIVILIAN, StandardEntityURN.FIRE_BRIGADE, StandardEntityURN.POLICE_FORCE, StandardEntityURN.AMBULANCE_TEAM)) {
            Human h = (Human)next;
            if (h == me()) {
                continue;
            }
            if (h.isHPDefined()
                && h.isBuriednessDefined()
                && h.isDamageDefined()
                && h.isPositionDefined()
                && h.getHP() > 0
                && (h.getBuriedness() > 0 || h.getDamage() > 0)) {
            	if(!targets.contains(h))
            		targets.add(h);
            }
        }
        Collections.sort(targets, new DistanceSorter(location(), model));
        return targets;
    }
	@Override
	protected EnumSet<StandardEntityURN> getRequestedEntityURNsEnum() {
		return EnumSet.of(StandardEntityURN.AMBULANCE_TEAM);
	}
}
