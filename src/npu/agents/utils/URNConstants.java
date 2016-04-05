package npu.agents.utils;

import java.util.EnumSet;

import rescuecore2.standard.entities.StandardEntityURN;

public class URNConstants {
	public static final EnumSet<StandardEntityURN> BUILDINGS = EnumSet.of(
			StandardEntityURN.BUILDING,
			StandardEntityURN.REFUGE,
			StandardEntityURN.POLICE_OFFICE,
			StandardEntityURN.AMBULANCE_CENTRE,
			StandardEntityURN.FIRE_STATION,
			StandardEntityURN.GAS_STATION
			);
	public static final EnumSet<StandardEntityURN> ROADS = EnumSet.of(
			StandardEntityURN.ROAD, 
			StandardEntityURN.HYDRANT);
}
