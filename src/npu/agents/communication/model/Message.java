package npu.agents.communication.model;

import npu.agents.communication.utils.CommUtils.MessageID;
import rescuecore2.worldmodel.EntityID;

public class Message {
	private MessageID messageID;
	private EntityID agentID;
	private int time;
	private int channel;
	public Message(MessageID messageID,EntityID agentID, int time, int channel) {
		super();
		this.messageID = messageID;
		this.agentID = agentID;
		this.time = time;
		this.channel = channel;
	}
	public EntityID getEntityID() {
		return agentID;
	}
	public void setEntityID(EntityID agentID) {
		this.agentID = agentID;
	}
	public int getTime() {
		return time;
	}
	public void setTime(int time) {
		this.time = time;
	}
	public int getChannel() {
		return channel;
	}
	public void setChannel(int channel) {
		this.channel = channel;
	}
}
