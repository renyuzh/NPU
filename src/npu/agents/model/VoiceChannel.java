package npu.agents.model;

public class VoiceChannel {

	/*
	 * The id of the voice channel
	 */
	public int id;

	/*
	 * The type of the channel "voice" or "radio"
	 */
	public String type;

	/*
	 * The maximum range of a message in mm (0-100000)
	 */
	public int range;

	/*
	 * The maximum number of a voice message an agent can send in one timestep
	 * (0-100)
	 */
	public int maxMessages;

	/*
	 * The maximum size of a voice message in bytes (0-2048)
	 */
	public int messageSize;

	/**
	 * Constructor for a voice channel
	 * 
	 * @param id
	 *            : the id of the channel
	 * @param type
	 *            : the type of the channel
	 * @param range
	 *            : the range of the channel in mm
	 * @param maxMessages
	 *            : number of messages that can be sent by an agent in one
	 *            timestep
	 * @param messageSize
	 *            : the size of a message on the channel in bytes
	 */
	public VoiceChannel(int id, String type, int range, int maxMessages,
			int messageSize) {
		this.id = id;
		this.type = type;
		this.range = range;
		this.maxMessages = maxMessages;
		this.messageSize = messageSize;
	}

	public String toString() {
		return "Channel id: " + this.id + "\n" + "Channel type: " + this.type
				+ "\n" + "Channel range: " + this.range + "\n"
				+ "Message size: " + this.messageSize + "\n" + "Max messages "
				+ this.maxMessages + "\n";
	}
}
