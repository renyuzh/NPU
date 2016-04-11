package npu.agents.communication.model;

import npu.agents.communication.utils.CommUtils.MessageID;
import rescuecore2.worldmodel.EntityID;

public class Message {
	private MessageID messageID;
	private EntityID positionID;
	private int time;
	private int channel;
	public Message(MessageID messageID,EntityID positionID, int time, int channel) {
		super();
		this.messageID = messageID;
		this.positionID = positionID;
		this.time = time;
		this.channel = channel;
	}
	public MessageID getMessageID() {
		return messageID;
	}
	public EntityID getPositionID() {
		return positionID;
	}
	public void setPostionID(EntityID positionID) {
		this.positionID = positionID;
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
