package npu.agents.model;

import npu.agents.communication.utils.CommUtils.MessageID;
import rescuecore2.worldmodel.EntityID;

public class Message {
	private MessageID messageID;
	private int index;
	private int time;
	private int channel;

	public Message(MessageID messageID, int index, int time, int channel) {
		super();
		this.messageID = messageID;
		this.index = index;
		this.time = time;
		this.channel = channel;
	}

	public MessageID getMessageID() {
		return messageID;
	}

	public int getIndex() {
		return index;
	}

	public void setIndex(int index) {
		this.index = index;
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
	public String toMessage(){
		return getMessageID().ordinal()+","+index;
	}

}
