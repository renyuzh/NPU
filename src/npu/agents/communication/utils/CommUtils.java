package npu.agents.communication.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import npu.agents.model.RadioChannel;
import npu.agents.model.VoiceChannel;
import rescuecore2.config.Config;

public class CommUtils {
	public static enum MessageID {
		ROAD_LOCATION,BUILDING_LOCATION,PLATOON_BURIED, PLATOON_INJURED, FB_STUCKED, FB_NEED_COLLABORATION, HUMAN_STUCKED, CIVILIAN_BLOCKED, PLATOON_STUCKED, PLATOON_BLOCKED, MAIN_ROAD_BLOCKED_TOTALLY, ENTRANCE_BLOCKED_TOTALLY,AT_BLOCKED, AT_STUCKED, FB_BURIED, HUMAN_INJURED_SERIOUSLY, BUILDING_ON_FIRE, AT_BURIED, HUMAN_BURIED, CIVILIAN_BlOCKED, CIVILIAN_BURIED, CIVILIAN_INJURED, REFUGE_BLOCKED, REMOVE_AGENT, PF_INJURED, PF_BURIED,PF_STUCKED, BUILDING_EXTINGUISHED, BUILDING_COLLAPSED, BUILDING_WARM, BUILDING_UNBURNT, BUILDING_CLEARED, BUILDING_HEATING, BUILDING_BURNING, BUILDING_INFERNO, BUILDING_BURNT_OUT, CIVILIAN_LOCATION_BURIED, CIVILIAN_LOCATION_NOT_BURIED, HYDRANT_AVAILABLE, HYDRANT_OCCUPIED, REFUGE_FILLING_RATE, AFTER_SHOCK, ROAD_BLOCKED, EDGE_BLOCKED, ROAD_CLEARED, EDGE_CLEARED, ROAD_OCCUPIED, ROAD_BLOCKED_PRIORITIZED, FINISHED_CLUSTER_POLICE, FINISHED_CLUSTER_FIRE, FINISHED_CLUSTER_AMBULANCE, AGENT_LOCATION_HEARD_CIVILIAN, BURIED_AGENT_LOCATION, STUCK_INSIDE_BLOCKADE, CLUSTER_STATUS, ONE_CLUSTER_STATUS, REMOVE_ENTITY
	};

	/**
	 * Discovers the available communication channels and their properties
	 * 
	 * @param voiceChannel
	 *            : list of voice channels
	 * @param radioChannel
	 *            : list of radio channels
	 * @param config
	 */
	public static void discoverChannels(ArrayList<VoiceChannel> voiceChannel, ArrayList<RadioChannel> radioChannel,
			Config config) {
		String comm = rescuecore2.standard.kernel.comms.ChannelCommunicationModel.PREFIX;
		int channels = config.getIntValue(comm + "count");
		for (int i = 0; i < channels; i++) {
			String type = config.getValue(comm + i + ".type");
			if (type.equalsIgnoreCase("radio")) {
				int bw = config.getIntValue(comm + i + ".bandwidth");
				radioChannel.add(new RadioChannel(i, type, bw));
			} else if (type.equalsIgnoreCase("voice")) {
				int range = config.getIntValue(comm + i + ".range");
				int msgSize = config.getIntValue(comm + i + ".messages.size");
				int maxMsg = config.getIntValue(comm + i + ".messages.max");
				voiceChannel.add(new VoiceChannel(i, type, range, maxMsg, msgSize));
			}
		}
	}

	/**
	 * Decides the radio channels that the agent will subscribe to
	 * 
	 * @param channels
	 *            : the list of available channels
	 * @param type
	 *            : the type of the agent ('f','p' or 'a')
	 * @return
	 */
	public static int decideRadioChannel(ArrayList<RadioChannel> channels, char type) {
		int size = channels.size();
		// arrange according to bandwidth
		Comparator<RadioChannel> comp = new Comparator<RadioChannel>() {
			public int compare(RadioChannel o1, RadioChannel o2) {
				if (o1.getBandwidth() < o2.getBandwidth())
					return -1;
				if (o1.getBandwidth() > o2.getBandwidth())
					return 1;
				return 0;
			}
		};
		Collections.sort(channels, comp);
		// channel decision
		switch (size) {
		case 0:
			;
			break;
		case 1:
			return channels.get(0).getId();
		case 2:
			switch (type) {
			case 'f':
				return channels.get(1).getId();
			case 'p':
				return channels.get(0).getId();
			case 'a':
				return channels.get(0).getId();
			}
			;
			break;
		default:
			switch (type) {
			case 'f':
				return channels.get(size - 1).getId();
			case 'p':
				return channels.get(size - 3).getId();
			case 'a':
				return channels.get(size - 2).getId();
			}
			;
			break;
		}
		return -1;
	}

}
