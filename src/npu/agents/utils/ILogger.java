package npu.agents.utils;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.log4j.FileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

import rescuecore2.worldmodel.EntityID;

public class ILogger {
	private Logger agentLogger;
	
	private static final String FIRE_BRIGADE_PREFIX = "fb-";
	private static final String POLICE_FORCE_PREFIX = "pf-";
	private static final String AMBULANCE_TEAM_PREFIX = "at-";
	private static final String LOGS_DIRECTORY = "../NPU_logs/";

	// determines the text file prefix
	public static enum AGENT_TYPE {
		FIRE_BRIGADE, POLICE_FORCE, AMBULANCE_TEAM
	};

	public ILogger(EntityID id, AGENT_TYPE agentType) {
		String textFilePrefix = "Agent-";
		String agentTypeDisplayText = "Agent";
		switch (agentType) {
		case FIRE_BRIGADE:
			agentTypeDisplayText = "FireBrigade";
			textFilePrefix = FIRE_BRIGADE_PREFIX;
			break;
		case POLICE_FORCE:
			agentTypeDisplayText = "PoliceForce";
			textFilePrefix = POLICE_FORCE_PREFIX;
			break;
		case AMBULANCE_TEAM:
			agentTypeDisplayText = "AmbulanceTeam";
			textFilePrefix = AMBULANCE_TEAM_PREFIX;
			break;
		}
		agentLogger = Logger.getLogger(agentTypeDisplayText + " "
				+ id.toString() + " logger");
		agentLogger.setLevel(Level.DEBUG);
		FileAppender fileAppender;
		try {
			fileAppender = new FileAppender(new PatternLayout(), LOGS_DIRECTORY
					+ getCurrentTimeStamp() + textFilePrefix + id.toString()
					+ ".log");
			agentLogger.removeAllAppenders();
			agentLogger.addAppender(fileAppender);
			agentLogger.debug("NPU-NPU-NPU-NPU-NPU-NPU-NPU-NPU-NPU-NPU-NPU-NPU");
			agentLogger.debug(agentTypeDisplayText + " " + id.toString()+ " connected");
			agentLogger.debug("NPU-NPU-NPU-NPU-NPU-NPU-NPU-NPU-NPU-NPU-NPU-NPU");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void debug(String message) {
			agentLogger.debug(message);
	}
	
	public static String getCurrentTimeStamp() {
		SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");// dd/MM/yyyy
		Date now = new Date();
		String strDate = sdfDate.format(now);
		return strDate;
	}

}