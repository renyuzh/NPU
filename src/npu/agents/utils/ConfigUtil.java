package npu.agents.utils;

import java.util.ArrayList;

import npu.agents.communication.utils.CommUtils;
import npu.agents.model.RadioChannel;
import npu.agents.model.VoiceChannel;
import rescuecore2.standard.StandardConstants;

public class ConfigUtil {
	private static final String MAX_WATER_KEY = "fire.tank.maximum";
	private static final String MAX_FIRE_EXTINGUISH_DISTANCE_KEY = "fire.extinguish.max-distance";
	private static final String MAX_FIRE_POWER_KEY = "fire.extinguish.max-sum";

	private static final String MAX_CLEAR_DISTANCE_KEY = "clear.repair.distance";
	private static final String HYDRANT_REFILL_RATE_KEY = "fire.tank.refill_hydrant_rate";

	private static final String COMM_PREFIX = rescuecore2.standard.kernel.comms.ChannelCommunicationModel.PREFIX;
	private static final String MAX_PLATOON_SUBSCRIBE_CHANNELS_KEY = "max.platoon";

	public static final int TIME_TO_LIVE_VOICE = 10;

	// max number of channels a platoon agent can subscribe to
	private final int maxPlatoonSubscribeChannels;

	private final int ignoreAgentCommand;
	private final int maxWater, maxFireExtinguishDistance, maxFirePower;
	private final int maxCleardistance;
	private final int hydrantRefillRate;

	private int policeChannel, fireChannel, ambulanceChannel;

	private final ArrayList<VoiceChannel> voiceChannels = new ArrayList<VoiceChannel>();
	private final ArrayList<RadioChannel> radioChannels = new ArrayList<RadioChannel>();

	private int policeForcesCount, fireBrigadesCount, ambulanceTeamsCount, policeOfficesCount, fireStationsCount,
			ambulanceCentersCount;

	public ConfigUtil(rescuecore2.config.Config config) {
		maxPlatoonSubscribeChannels = config.getIntValue(COMM_PREFIX + MAX_PLATOON_SUBSCRIBE_CHANNELS_KEY);

		ignoreAgentCommand = config.getIntValue(kernel.KernelConstants.IGNORE_AGENT_COMMANDS_KEY);
		// POLICE_FORCE RELATED
		maxCleardistance = config.getIntValue(MAX_CLEAR_DISTANCE_KEY) * 3 / 4;

		// FIRE_BRIGAE_RELATED
		maxWater = config.getIntValue(MAX_WATER_KEY);
		maxFireExtinguishDistance = config.getIntValue(MAX_FIRE_EXTINGUISH_DISTANCE_KEY);
		maxFirePower = config.getIntValue(MAX_FIRE_POWER_KEY);
		hydrantRefillRate = config.getIntValue(HYDRANT_REFILL_RATE_KEY);

		CommUtils.discoverChannels(voiceChannels, radioChannels, config);
		fireChannel = CommUtils.decideRadioChannel(getRadioChannels(), 'f');
		ambulanceChannel = CommUtils.decideRadioChannel(getRadioChannels(), 'a');
		policeChannel = CommUtils.decideRadioChannel(getRadioChannels(), 'p');

		policeForcesCount = config.getIntValue(StandardConstants.POLICE_FORCE_COUNT_KEY);
		fireBrigadesCount = config.getIntValue(StandardConstants.FIRE_BRIGADE_COUNT_KEY);
		ambulanceTeamsCount = config.getIntValue(StandardConstants.AMBULANCE_TEAM_COUNT_KEY);
		policeOfficesCount = config.getIntValue(StandardConstants.POLICE_OFFICE_COUNT_KEY);
		fireStationsCount = config.getIntValue(StandardConstants.FIRE_STATION_COUNT_KEY);
		ambulanceCentersCount = config.getIntValue(StandardConstants.AMBULANCE_CENTRE_COUNT_KEY);
	}

	public int getMaxPlatoonSubscribeChannels() {
		return maxPlatoonSubscribeChannels;
	}

	public int getIgnoreAgentCommand() {
		return ignoreAgentCommand;
	}

	public int getMaxWater() {
		return maxWater;
	}

	public int getMaxFireExtinguishDistance() {
		return maxFireExtinguishDistance;
	}

	public int getHydrantRefillRate() {
		return hydrantRefillRate;
	}

	public int getMaxFirePower() {
		return maxFirePower;
	}

	public int getMaxCleardistance() {
		return maxCleardistance;
	}

	public ArrayList<VoiceChannel> getVoiceChannels() {
		return voiceChannels;
	}

	public ArrayList<RadioChannel> getRadioChannels() {
		return radioChannels;
	}

	public int getPoliceChannel() {
		return policeChannel;
	}

	public void setPoliceChannel(int policeChannel) {
		this.policeChannel = policeChannel;
	}

	public int getFireChannel() {
		return fireChannel;
	}

	public void setFireChannel(int fireChannel) {
		this.fireChannel = fireChannel;
	}

	public int getAmbulanceChannel() {
		return ambulanceChannel;
	}

	public void setAmbulanceChannel(int ambulanceChannel) {
		this.ambulanceChannel = ambulanceChannel;
	}

	public int getPoliceForcesCount() {
		return policeForcesCount;
	}

	public int getFireBrigadesCount() {
		return fireBrigadesCount;
	}

	public int getAmbulanceTeamsCount() {
		return ambulanceTeamsCount;
	}

	public int getPoliceOfficesCount() {
		return policeOfficesCount;
	}

	public int getFireStationsCount() {
		return fireStationsCount;
	}

	public int getAmbulanceCentersCount() {
		return ambulanceCentersCount;
	}

	public RadioChannel getRadioChannelByID(int id) {
		for (RadioChannel r : radioChannels)
			if (r.getId() == id)
				return r;
		return null;
	}
}
