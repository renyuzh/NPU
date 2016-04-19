package npu.agents.communication.utils;

import java.util.HashSet;
import java.util.Set;

import npu.agents.communication.utils.CommUtils.MessageID;
import npu.agents.model.Message;
import npu.agents.utils.ConfigUtil;
import rescuecore2.standard.entities.Blockade;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.Road;
import rescuecore2.worldmodel.EntityID;

public class MessageHandler {
	private Set<Message> messagesWillSend = new HashSet<Message>();
	private ConfigUtil configuration;
	public MessageHandler(ConfigUtil configuration){
		this.configuration = configuration;
	}
	public void reportBuildingInfo(int time,ChangeSetUtil seenChanges) {
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

	public void reportInjuredHumanInfo(int time,ChangeSetUtil seenChanges) {
		for (Human human : seenChanges.getBuriedPlatoons()) {
			Message message = new Message(MessageID.PLATOON_BURIED, human.getPosition(), time,
					configuration.getAmbulanceChannel());
				messagesWillSend.add(message);
		}
		for (Human human : seenChanges.getInjuredCivilians()) {
			Message message = new Message(MessageID.CIVILIAN_INJURED, human.getPosition(), time,
					configuration.getAmbulanceChannel());
				messagesWillSend.add(message);
		}
	}

	public void reportRoadsInfo(int time,ChangeSetUtil seenChanges) {
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
	public Set<Message> getMessagesWillSend() {
		return messagesWillSend;
	}
	public void addMessage(Message message) {
		messagesWillSend.add(message);
	}
}
