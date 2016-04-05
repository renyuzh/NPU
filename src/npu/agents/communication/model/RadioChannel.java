package npu.agents.communication.model;

public class RadioChannel {
	/*
	 * The id of the radio channel
	 */
	public int id;

	/*
	 * The type of the channel "voice" or "radio"
	 */
	public String type;

	/*
	 * The maximum capacity of the channel in bytes per timestep (0 - 4096)
	 */
	public int bandwidth;

	/**
	 * Constructor for a voice channel
	 * 
	 * @param id
	 *            : the id of the channel
	 * @param type
	 *            : the type of the channel
	 * @param bandwidth
	 *            : the bandwidth of the channel
	 */
	public RadioChannel(int id, String type, int bandwidth) {
		this.id = id;
		this.type = type;
		this.bandwidth = bandwidth;
	}

	public String toString() {
		return "Channel id: " + this.id + "\n" + "Channel type: " + this.type
				+ "\n" + "Channel bandwidth: " + this.bandwidth + "\n";
	}
}
