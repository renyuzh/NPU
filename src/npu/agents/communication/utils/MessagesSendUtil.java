package npu.agents.communication.utils;

import java.util.HashSet;
import java.util.Set;

import npu.agents.communication.model.Message;
import rescuecore2.worldmodel.EntityID;

public class MessagesSendUtil {
	private Set<Message> sendMessages = new HashSet<Message>();
	public void addFireInfo(Set<EntityID> fireBuildings) {
		for(EntityID buidingID : fireBuildings) {
			
		}
	}

	public void addInjuredHumanInfo(Set<EntityID> injuredHuman) {
		// TODO Auto-generated method stub
		
	}

}
