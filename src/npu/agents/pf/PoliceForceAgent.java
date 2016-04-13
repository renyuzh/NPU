package npu.agents.pf;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import npu.agents.clustering.Cluster;
import npu.agents.clustering.ClustingMap;
import npu.agents.common.AbstractCommonAgent;
import npu.agents.communication.model.Message;
import npu.agents.communication.utils.ChangeSetUtil;
import npu.agents.communication.utils.CommUtils.MessageID;
import npu.agents.utils.KConstants;
import npu.agents.utils.Point;
import rescuecore2.messages.Command;
import rescuecore2.misc.Pair;
import rescuecore2.misc.geometry.GeometryTools2D;
import rescuecore2.misc.geometry.Line2D;
import rescuecore2.misc.geometry.Point2D;
import rescuecore2.misc.geometry.Vector2D;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.Blockade;
import rescuecore2.standard.entities.Civilian;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.PoliceForce;
import rescuecore2.standard.entities.Road;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.standard.messages.AKSpeak;
import rescuecore2.worldmodel.ChangeSet;
import rescuecore2.worldmodel.EntityID;

public class PoliceForceAgent extends AbstractCommonAgent<PoliceForce> {
	private int distance;
	private Cluster cluster;
	private Set<EntityID> roadsUnexplored = new HashSet<EntityID>();
	private Set<EntityID> roadsHasCleared = new HashSet<EntityID>();
	private List<EntityID> stuckedHumansPositions = new ArrayList<EntityID>();
	private List<EntityID> blockedHumansPositions = new ArrayList<EntityID>();
	private List<EntityID> refugeBlocked = new ArrayList<EntityID>();

	private Set<Message> messagesWillSend = new HashSet<Message>();
	private Map<EntityID, Set<EntityID>> roadsAroundRefuge;
	private List<EntityID> pathByPriotity;

	@Override
	protected void postConnect() {
		super.postConnect();
		try {
			ClustingMap.initMap(KConstants.countOfpf, 100, model);
			cluster = ClustingMap.assignAgentToCluster(me(), model);
			if (cluster == null) {
				System.out.println("该agent没有分配到cluster");
				return;
			}
			roadsUnexplored = cluster.getRoadsAroundCluster();
			roadsAroundRefuge = cluster.getRoadARoundRefuge();
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("聚类失败");
		}
		distance = configuration.getMaxCleardistance();
	}

	@Override
	protected void think(int time, ChangeSet changes, Collection<Command> heard) {
		if (time < configuration.getIgnoreAgentCommand())
			return;
		handleReceiveMessages(time, changes, heard);
		sendAllVoiceMessages(time);
		sendAllRadioMessages(time);
	}

	private void handleReceiveMessages(int time, ChangeSet changes, Collection<Command> heard) {
		if (time == configuration.getIgnoreAgentCommand()) {
			sendSubscribe(time, configuration.getPoliceChannel());// 注册了该通道的可以接收发往该通道的消息
			if (me().isHPDefined() && me().isBuriednessDefined() && me().getHP() > 0 && me().getBuriedness() > 0) {
				handleBuriedMe(time);
				return;
			}
			sendRest(time);
			return;
		}
		handleHeard(time, heard);
		seenChanges.handleChanges(model, changes);
		addBuildingInfoToMessageSend(time);
		addInjuredHumanInfoToMessageSend(time);
		updateClearedRoads();
		if (me().isHPDefined() && me().isBuriednessDefined() && me().getHP() > 0 && me().getBuriedness() > 0) {
			handleBuriedMe(time);
			return;
		}
		removeClearedRoads();
		addAllClearedRoadsBack();
		if (!refugeBlocked.isEmpty()) {
			distanceSort(refugeBlocked,me());
			pathByPriotity = search.getPath(me().getPosition(), refugeBlocked.get(0), null);
		} else if (!stuckedHumansPositions.isEmpty()) {
			distanceSort(stuckedHumansPositions,me());
			pathByPriotity = search.getPath(me().getPosition(), stuckedHumansPositions.get(0), null);
		} else if (!blockedHumansPositions.isEmpty()) {
			distanceSort(blockedHumansPositions,me());
			pathByPriotity = search.getPath(me().getPosition(), blockedHumansPositions.get(0), null);
		}
		if (clearBlockadesAroundRefuge(time, seenChanges)) {
			return;
		} else {
			basicClear(time, seenChanges);
		}
	}
	private void updateClearedRoads() {
		Set<EntityID> clearedRoadsIDs = seenChanges.getClearedRoads();
		for (EntityID clearedRoadID : clearedRoadsIDs) {
			if (stuckedHumansPositions.contains(clearedRoadID)) {
				stuckedHumansPositions.remove(clearedRoadID);
			}
			if (blockedHumansPositions.contains(clearedRoadID)) {
				blockedHumansPositions.remove(clearedRoadID);
			}
			if (refugeBlocked.contains(clearedRoadID)) {
				refugeBlocked.remove(clearedRoadID);
			}
		}
	}

	private void sendAllRadioMessages(int time) {
		for (Message message : messagesWillSend) {
			String data = message.getMessageID().ordinal() + "," + message.getPositionID().getValue();
			sendSpeak(time, message.getChannel(), data.getBytes());
		}
		messagesWillSend.clear();
	}

	private void sendAllVoiceMessages(int time) {
		for (Message message : messagesWillSend) {
			String data = message.getMessageID().ordinal() + "," + message.getPositionID().getValue();
			sendSpeak(time, message.getChannel(), data.getBytes());
		}
		messagesWillSend.clear();
	}

	// 看到的，肯定只有一个refuge
	private boolean clearBlockadesAroundRefuge(int time, ChangeSetUtil seenChanges) {
		if (clearNearBlockes(time))
			return true;
		Set<Blockade> seenBlockades = seenChanges.getSeenBlockadesAroundRefuge(roadsAroundRefuge);
		if (moveToSeenBlockade(time, seenBlockades))
			return true;
		return false;
	}

	private void handleHeard(int time, Collection<Command> heards) {
		for (Command heard : heards) {
			if (!(heard instanceof AKSpeak))
				continue;
			AKSpeak info = (AKSpeak) heard;
			EntityID agentID = info.getAgentID();
			String content = new String(info.getContent());
			if (content.isEmpty()) {
				System.out.println("pf of " + me().getID() + " heard dropped message from " + info.getAgentID()
						+ " from " + info.getChannel());
				continue;
			}
			//居民发出求救信息，要么受伤，要么被困
			if (content.equalsIgnoreCase("Ouch") || content.equalsIgnoreCase("Help")) {
				Civilian civilian = (Civilian) model.getEntity(agentID);
				EntityID civilianPositionID = civilian.getPosition();
				if (civilian.isHPDefined() && civilian.isBuriednessDefined() && civilian.isDamageDefined()
						&& civilian.getHP() > 0 && (civilian.getBuriedness() > 0 || civilian.getDamage() > 0)) {
					Message message = new Message(MessageID.CIVILIAN_INJURED, civilianPositionID, time,
							configuration.getAmbulanceChannel());
					messagesWillSend.add(message);
				} else {
					if (!stuckedHumansPositions.contains(civilianPositionID) && roadsUnexplored.contains(civilianPositionID))
						stuckedHumansPositions.add(civilianPositionID);
				}
				System.out.println("pf of " + me().getID() + "handle cicilian help");
				continue;
			}
			String[] heardInfo = content.split(",");
			String typeOfhearedInfo = heardInfo[0];
			String positionOfInfo = heardInfo[1];
			int id = Integer.parseInt(typeOfhearedInfo);
			int postion = Integer.parseInt(positionOfInfo);
			EntityID postionID = new EntityID(postion);
			MessageID messageID = MessageID.values()[id];
			if(messageID.equals(MessageID.ROAD_CLEARED)){
				roadsUnexplored.remove(postionID);
				stuckedHumansPositions.remove(postionID);
				blockedHumansPositions.remove(postionID);
				refugeBlocked.remove(postionID);
				continue;
			}
			if (!roadsUnexplored.contains(postionID))
				continue;
			if (stuckedHumansPositions.contains(postionID) || blockedHumansPositions.contains(postionID))
				continue;
			if (blockedHumansPositions.contains(postionID))
				continue;
			if (roadsAroundRefuge.values().contains(postionID) && !refugeBlocked.contains(postionID)) {
				refugeBlocked.add(postionID);
			}
			if (configuration.getRadioChannels().contains(info.getChannel()))
				handleRadio(postionID, messageID);
			else
				handleVoice(postionID, messageID);
		}
	}

	private void handleVoice(EntityID postionID, MessageID messageID) {
		System.out.println("pf of " + me().getID() + " is handle voice ");
		switch (messageID) {
		case HUMAN_STUCKED:
			stuckedHumansPositions.add(postionID);
			break;
		case HUMAN_BLOCKED:
			blockedHumansPositions.add(postionID);
			break;
		default:
			break;
		}
	}

	private void handleRadio(EntityID postionID, MessageID messageID) {
		System.out.println("pf of " + me().getID() + " is handle radio ");
		switch (messageID) {
		case HUMAN_STUCKED:
			stuckedHumansPositions.add(postionID);
			break;
		case HUMAN_BLOCKED:
			blockedHumansPositions.add(postionID);
			break;
		default:
			break;
		}
	}

	public void addBuildingInfoToMessageSend(int time) {
		for (EntityID unburntBuidingID : seenChanges.getBuildingsUnburnt()) {
			Message message = new Message(MessageID.BUILDING_UNBURNT, unburntBuidingID, time,
					configuration.getFireChannel());
			messagesWillSend.add(message);
		}
		for (EntityID extinguishBuildingID : seenChanges.getBuildingsExtinguished()) {
			Message message = new Message(MessageID.BUILDING_EXTINGUISHED, extinguishBuildingID, time,
					configuration.getFireChannel());
			messagesWillSend.add(message);
		}
		for (EntityID warmBuidingID : seenChanges.getBuildingsIsWarm()) {
			Message message = new Message(MessageID.BUILDING_UNBURNT, warmBuidingID, time,
					configuration.getFireChannel());
			messagesWillSend.add(message);
		}
		for (EntityID onFireBuildingID : seenChanges.getBuildingsOnFire()) {
			Message message = new Message(MessageID.BUILDING_EXTINGUISHED, onFireBuildingID, time,
					configuration.getFireChannel());
			messagesWillSend.add(message);
		}
	}

	public void addInjuredHumanInfoToMessageSend(int time) {
		for (Human human : seenChanges.getBuriedHuman()) {
			Message message = new Message(MessageID.HUMAN_BURIED, human.getPosition(), time,
					configuration.getAmbulanceChannel());
			messagesWillSend.add(message);
		}
	}

	public void handleInjuredMe(int time) {
		Message message = new Message(MessageID.PF_INJURED, me().getID(), time, configuration.getAmbulanceChannel());
		messagesWillSend.add(message);
		sendRest(time);
	}

	public void handleBuriedMe(int time) {
		Message message = new Message(MessageID.PF_BURIED, me().getID(), time, configuration.getAmbulanceChannel());
		messagesWillSend.add(message);
		sendRest(time);
		System.out.println("pf of " + me().getID() + "is buried at time " + time);
	}

	private void removeClearedRoads() {
		EntityID pos = me().getPosition();
		if (roadsUnexplored.contains(pos)) {
			StandardEntity entity = model.getEntity(pos);
			Road r = (Road) entity;
			if (r.isBlockadesDefined() && r.getBlockades().isEmpty()) {
				roadsUnexplored.remove(pos);
				roadsHasCleared.add(pos);
				System.out.println("removeClearedRoads");
			}
		}
	}

	private void addAllClearedRoadsBack() {
		if (roadsUnexplored.size() == 0) {
			Iterator<EntityID> roadsID = roadsHasCleared.iterator();
			while (roadsID.hasNext()) {
				roadsUnexplored.add(roadsID.next());
			}
			roadsHasCleared.clear();
			System.out.println("addAllClearedRoadsBack");
		}
	}

	private void basicClear(int time, ChangeSetUtil seenChanges) {
		if (clearNearBlockes(time))
			return;
		if (moveToSeenBlockade(time, seenChanges.getSeenBlockades())) {
			return;
		} else if (pathByPriotity != null) {
			sendMove(time, pathByPriotity);
			pathByPriotity = null;
			return;
		} else {
			boolean traverslOk = traversalRoads(time);
			if (!traverslOk) {
				sendMove(time, randomWalkAroundCluster(roadsUnexplored));
			}
			return;
		}
	}

	private boolean clearNearBlockes(int time) {
		Blockade target = getTargetBlockade();
		if (target != null) {
			sendSpeak(time, configuration.getPoliceChannel(), ("Clearing " + target).getBytes());
			List<Line2D> lines = GeometryTools2D.pointsToLines(GeometryTools2D.vertexArrayToPoints(target.getApexes()),
					true);
			double best = Double.MAX_VALUE;
			Point2D bestPoint = null;
			Point2D origin = new Point(me().getX(), me().getY());
			for (Line2D next : lines) {
				Point2D closest = GeometryTools2D.getClosestPointOnSegment(next, origin);
				double d = GeometryTools2D.getDistance(origin, closest);
				if (d < best) {
					best = d;
					bestPoint = closest;
				}
			}
			Vector2D v = bestPoint.minus(new Point(me().getX(), me().getY()));
			v = v.normalised().scale(1000000);
			sendClear(time, (int) Math.ceil((me().getX() + v.getX())), (int) Math.ceil((me().getY() + v.getY())));
			System.out.println("Clearing blockade " + target);
			return true;
		}
		return false;
	}

	private boolean moveToSeenBlockade(int time, Set<Blockade> blockades) {
		List<EntityID> nearPath = search.getPath(me().getPosition(), getBlockedArea(blockades), null);
		if (nearPath != null) {
			Road r = (Road) model.getEntity(nearPath.get(nearPath.size() - 1));
			Blockade b = getTargetBlockade(r, -1);
			if (b != null) {
				sendMove(time, nearPath, b.getX(), b.getY());
				System.out.println("移动到视力所及的有路障的地方");
				return true;
			}
		}
		return false;
	}

	private boolean traversalRoads(int time) {
		if (roadsUnexplored.size() != 0) {
			EntityID[] roadsID = roadsUnexplored.toArray(new EntityID[0]);
			List<EntityID> path = search.getPath(me().getPosition(), roadsID[0], null);
			if (path != null) {
				System.out.println("遍历道路");
				System.out.println();
				Road r = (Road) model.getEntity(roadsID[0]);
				sendMove(time, path, r.getX(), r.getY());
				return true;
			}
		}
		return false;
	}

	public EntityID getBlockedArea(Set<Blockade> seenBlockades) {
		int minDistance = Integer.MAX_VALUE;
		int x = me().getX();
		int y = me().getY();
		EntityID closestRoad = null;
		for (Blockade blockade : seenBlockades) {
			EntityID positionID = blockade.getPosition();
			StandardEntity positionEntity = model.getEntity(positionID);
			if (!(positionEntity instanceof Road))
				continue;
			int distance = findDistanceTo(blockade, x, y);
			if (distance < minDistance) {
				minDistance = distance;
				closestRoad = positionID;
			}
		}
		return closestRoad;
	}

	private Blockade getTargetBlockade() {
		Area location = (Area) location();
		Blockade result = getTargetBlockade(location, distance);
		if (result != null) {
			return result;
		}
		for (EntityID next : location.getNeighbours()) {
			location = (Area) model.getEntity(next);
			result = getTargetBlockade(location, distance);
			if (result != null) {
				return result;
			}
		}
		return null;
	}

	// 获得目标路障
	private Blockade getTargetBlockade(Area area, int maxDistance) {
		if (area == null || !area.isBlockadesDefined()) {
			return null;
		}
		List<EntityID> ids = area.getBlockades();
		int x = me().getX();
		int y = me().getY();
		for (EntityID next : ids) {
			Blockade b = (Blockade) model.getEntity(next);
			int d = findDistanceTo(b, x, y);
			if (maxDistance < 0 && d != 0)
				System.out.println("移动到能看到的最短距离" + d);
			if (d < maxDistance) {
				System.out.println("距路障最短距离" + d);
				return b;
			}
		}
		return null;
	}

	private int findDistanceTo(Blockade b, int x, int y) {
		List<Line2D> lines = GeometryTools2D.pointsToLines(GeometryTools2D.vertexArrayToPoints(b.getApexes()), true);
		double best = Double.MAX_VALUE;
		Point origin = new Point(x, y);
		// 算距离最近的点
		for (Line2D next : lines) {
			Point2D closest = GeometryTools2D.getClosestPointOnSegment(next, origin);
			double d = GeometryTools2D.getDistance(origin, closest);
			if (d < best) {
				best = d;
			}
		}
		return (int) best;
	}
	@Override
	protected EnumSet<StandardEntityURN> getRequestedEntityURNsEnum() {
		return EnumSet.of(StandardEntityURN.POLICE_FORCE);
	}

}
