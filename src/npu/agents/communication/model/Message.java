package npu.agents.communication.model;

import npu.agents.communication.utils.CommUtils.MessageID;
import rescuecore2.worldmodel.EntityID;

public class Message {
	private MessageID messageID;
	private EntityID positionID;
	private int time;
	private int channel;

	public Message(MessageID messageID, EntityID positionID, int time, int channel) {
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

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((messageID == null) ? 0 : messageID.hashCode());
		result = prime * result + ((positionID == null) ? 0 : positionID.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		Message other = (Message) obj;
		if (messageID != other.messageID)
			return false;
		if (positionID == null) {
			if (other.positionID != null)
				return false;
		} else if (!positionID.equals(other.positionID))
			return false;
		return true;
	}

}
