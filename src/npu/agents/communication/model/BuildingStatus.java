package npu.agents.communication.model;

import rescuecore2.standard.entities.AmbulanceCentre;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.FireStation;
import rescuecore2.standard.entities.PoliceOffice;
import rescuecore2.standard.entities.Refuge;
import rescuecore2.standard.entities.StandardEntityConstants;
import rescuecore2.standard.entities.StandardEntityConstants.Fieryness;

public class BuildingStatus {
	private static final int WARM_TEMPERATURE = 30;
	private Building building;
	private int fieryness = Integer.MAX_VALUE;// only compare
												// heating,building,and Inferno

	public BuildingStatus(Building building, Fieryness fieryness) {
		this.building = building;
		if (isOnFire(fieryness))
			this.fieryness = fieryness.ordinal();
	}

	public boolean isOnFire() {
		if (building.isFierynessDefined() && building.isOnFire()
				&& building.getFierynessEnum() != StandardEntityConstants.Fieryness.BURNT_OUT)
			return true;
		return false;
	}

	public boolean isWarm() {
		if (building.isFierynessDefined() && building.isTemperatureDefined()
				&& building.getTemperature() > WARM_TEMPERATURE && !building.isOnFire()
				&& building.getFierynessEnum() != StandardEntityConstants.Fieryness.BURNT_OUT
				&& (!(building instanceof Refuge)) && (!(building instanceof AmbulanceCentre))
				&& (!(building instanceof FireStation)) && (!(building instanceof PoliceOffice)))
			return true;
		return false;
	}

	public boolean isExtinguished() {
		if (building.isFierynessDefined() && building.isTemperatureDefined()
				&& (building.getTemperature() <= WARM_TEMPERATURE
						&& ((building.getFierynessEnum() == StandardEntityConstants.Fieryness.WATER_DAMAGE)
								|| (building.getFierynessEnum() == StandardEntityConstants.Fieryness.MINOR_DAMAGE)
								|| (building.getFierynessEnum() == StandardEntityConstants.Fieryness.MODERATE_DAMAGE)
								|| (building.getFierynessEnum() == StandardEntityConstants.Fieryness.SEVERE_DAMAGE))))
			return true;
		return false;
	}

	public boolean isUnburnt() {
		if (building.isFierynessDefined() && building.isTemperatureDefined()
				&& building.getFierynessEnum() == StandardEntityConstants.Fieryness.UNBURNT
				&& building.getTemperature() <= WARM_TEMPERATURE)
			return true;
		return false;
	}

	public boolean isFireynessBurning() {
		building.getFieryness();
		if (building.isFierynessDefined() && building.getFierynessEnum() == StandardEntityConstants.Fieryness.BURNING)
			return true;
		return false;
	}

	public boolean isFireynessHeating() {
		if (building.isFierynessDefined() && building.getFierynessEnum() == StandardEntityConstants.Fieryness.HEATING)
			return true;
		return false;
	}

	public boolean isFireynessInferno() {
		if (building.isFierynessDefined() && building.getFierynessEnum() == StandardEntityConstants.Fieryness.INFERNO)
			return true;
		return false;
	}

	public int getFieryness() {
		return fieryness;
	}

	private boolean isOnFire(Fieryness fieryness) {
		if (fieryness == StandardEntityConstants.Fieryness.HEATING
				|| fieryness == StandardEntityConstants.Fieryness.BURNING
				|| fieryness == StandardEntityConstants.Fieryness.INFERNO)
			return true;
		return false;
	}

}
