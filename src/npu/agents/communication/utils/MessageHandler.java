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
	private Set<Message> voiceMessagesWillSend = new HashSet<Message>();
	public MessageHandler(ConfigUtil configuration){
		this.configuration = configuration;
	}
	public void reportBuildingInfo(int time,ChangeSetUtil seenChanges) {
		for (EntityID warmBuidingID : seenChanges.getBuildingsIsWarm()) {
			System.out.println("send warm building message");
			int index = MessageCompressUtil.getAreaIndex(warmBuidingID);
			Message message = new Message(MessageID.BUILDING_WARM, index, time, configuration.getFireChannel());
			messagesWillSend.add(message);
			voiceMessagesWillSend.add(message);
		}
		for (EntityID onFireBuildingID : seenChanges.getBuildingsOnFire()) {
			System.out.println("send on fire building message");
			int index = MessageCompressUtil.getAreaIndex(onFireBuildingID);
			Message message = new Message(MessageID.BUILDING_ON_FIRE, index, time,
					configuration.getFireChannel());
			messagesWillSend.add(message);
			voiceMessagesWillSend.add(message);
		}
		for (EntityID extinguishBuildingID : seenChanges.getBuildingsExtinguished()) {
			System.out.println("send extinguished building message");
			int index = MessageCompressUtil.getAreaIndex(extinguishBuildingID);
			Message message = new Message(MessageID.BUILDING_EXTINGUISHED, index, time,
					configuration.getFireChannel());
			messagesWillSend.add(message);
			voiceMessagesWillSend.add(message);
		}
		for (EntityID burtOutBuildingID : seenChanges.getBuildingBurtOut()) {
			System.out.println("send burtOut building message");
			int index = MessageCompressUtil.getAreaIndex(burtOutBuildingID);
			Message message = new Message(MessageID.BUILDING_BURNT_OUT, index, time,
					configuration.getFireChannel());
			messagesWillSend.add(message);
			voiceMessagesWillSend.add(message);
		}
	}

	public void reportInjuredHumanInfo(int time,ChangeSetUtil seenChanges) {
		for (Human human : seenChanges.getBuriedPlatoons()) {
			int index = MessageCompressUtil.getAreaIndex(human.getPosition());
			Message message = new Message(MessageID.PLATOON_BURIED, index, time,
					configuration.getAmbulanceChannel());
				messagesWillSend.add(message);
				voiceMessagesWillSend.add(message);
		}
		for (Human human : seenChanges.getInjuredCivilians()) {
			int index = MessageCompressUtil.getAreaIndex(human.getPosition());
			Message message = new Message(MessageID.CIVILIAN_INJURED, index, time,
					configuration.getAmbulanceChannel());
				messagesWillSend.add(message);
				voiceMessagesWillSend.add(message);
		}
	}

	public void reportRoadsInfo(int time,ChangeSetUtil seenChanges) {
		for (Blockade blockade : seenChanges.getSeenBlockades()) {
			int index = MessageCompressUtil.getAreaIndex(blockade.getPosition());
			Message message = new Message(MessageID.PLATOON_BLOCKED, index, time,
					configuration.getPoliceChannel());
			messagesWillSend.add(message);
			voiceMessagesWillSend.add(message);
		}
		for (Road road : seenChanges.getTotallyBlockedBuildingEntrance()) {
			int index = MessageCompressUtil.getAreaIndex(road.getID());
			Message message = new Message(MessageID.ENTRANCE_BLOCKED_TOTALLY, index, time,
					configuration.getPoliceChannel());
			messagesWillSend.add(message);
			voiceMessagesWillSend.add(message);
		}
		
		for (Road road : seenChanges.getTotallyBlockedMainRoad()) {
			int index = MessageCompressUtil.getAreaIndex(road.getID());
			Message message = new Message(MessageID.MAIN_ROAD_BLOCKED_TOTALLY,index, time,
					configuration.getPoliceChannel());
			messagesWillSend.add(message);
			voiceMessagesWillSend.add(message);
		}

		for (Blockade blockadeStuckedHuman : seenChanges.getBlockadesHumanStuckedIn()) {
			int index = MessageCompressUtil.getAreaIndex(blockadeStuckedHuman.getPosition());
			Message message = new Message(MessageID.HUMAN_STUCKED, index, time,
					configuration.getPoliceChannel());
			messagesWillSend.add(message);
			voiceMessagesWillSend.add(message);
		}
		for (EntityID roadID : seenChanges.getClearedRoads()) {
			int index = MessageCompressUtil.getAreaIndex(roadID);
			Message message = new Message(MessageID.ROAD_CLEARED, index, time, configuration.getPoliceChannel());
			messagesWillSend.add(message);
			voiceMessagesWillSend.add(message);
		}
	}
	public Set<Message> getMessagesWillSend() {
		return messagesWillSend;
	}
	public Set<Message> getVoiceMessagesWillSend() {
		return voiceMessagesWillSend;
	}
	public void addMessage(Message message) {
		messagesWillSend.add(message);
	}
	public void addVoiceMessage(Message message){
		voiceMessagesWillSend.add(message);
		
	}
}
