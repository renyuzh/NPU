package npu.agents.pf;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import npu.agents.clustering.Cluster;
import npu.agents.clustering.ClustingMap;
import npu.agents.common.AbstractCommonAgent;
import npu.agents.communication.utils.ChangeSetUtil;
import npu.agents.communication.utils.CommUtils.MessageID;
import npu.agents.communication.utils.MessageHandler;
import npu.agents.model.Message;
import npu.agents.model.Point;
import npu.agents.utils.DistanceSorter;
import npu.agents.utils.KConstants;
import rescuecore2.log.Logger;
import rescuecore2.messages.Command;
import rescuecore2.misc.geometry.GeometryTools2D;
import rescuecore2.misc.geometry.Line2D;
import rescuecore2.misc.geometry.Point2D;
import rescuecore2.misc.geometry.Vector2D;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.Blockade;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.Civilian;
import rescuecore2.standard.entities.Edge;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.PoliceForce;
import rescuecore2.standard.entities.Refuge;
import rescuecore2.standard.entities.Road;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.standard.messages.AKSpeak;
import rescuecore2.worldmodel.ChangeSet;
import rescuecore2.worldmodel.EntityID;

public class PoliceForceAgent extends AbstractCommonAgent<PoliceForce> {
	private int maxClearDistance;
	private Cluster cluster;
	private Set<EntityID> roadsUnexplored = new HashSet<EntityID>();
	private Set<EntityID> roadsExplored = new HashSet<EntityID>();
	private Set<EntityID> positionsBlockedCivilians = new HashSet<EntityID>();
	private Set<EntityID> positionsBlockedPlatoon = new HashSet<EntityID>();
	private Set<EntityID> positionsStuckedHuman = new HashSet<EntityID>();
	private Set<EntityID> positionsBlockdedMainRoadsTotally = new HashSet<EntityID>();
	private Set<EntityID> buildingsEntrancesHumanStuckedIn = new HashSet<EntityID>();
	private Set<EntityID> roadsInCluster;
	private Map<EntityID, Set<EntityID>> refugesEntrancesMap;
	private List<EntityID> pathByPriotity;

	private ChangeSetUtil seenChanges;
	private MessageHandler messageHandler;

	private EntityID[] prePosition = new EntityID[2];
	private EntityID nowPosition;

	@Override
	public void postConnect() {
		super.postConnect();
		try {
			ClustingMap.initMap(Math.min(KConstants.countOfpf, configuration.getPoliceForcesCount()), 100, model);
			cluster = ClustingMap.assignAgentToCluster(me(), model);
			if (cluster == null) {
				System.out.println("该agent没有分配到cluster");
				return;
			}
			roadsInCluster = cluster.getRoadsInCluster();
			// roadsUnexplored = cluster.getRoadsInCluster();这样两者都指向相同的地方了
			Iterator<EntityID> iter = roadsInCluster.iterator();
			while (iter.hasNext()) {
				roadsUnexplored.add(iter.next());
			}
			refugesEntrancesMap = cluster.getRoadARoundRefuge();
			maxClearDistance = configuration.getMaxCleardistance();
			seenChanges = new ChangeSetUtil();
			messageHandler = new MessageHandler(configuration);
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("聚类失败");
			return;
		}
	}

	@Override
	public void think(int time, ChangeSet changes, Collection<Command> heard) {
		if (time < configuration.getIgnoreAgentCommand())
			return;
		handleReceiveMessages(time, changes, heard);
		sendMessages(time);
	}

	private void handleReceiveMessages(int time, ChangeSet changes, Collection<Command> heard) {
		if (time == configuration.getIgnoreAgentCommand()) {
			sendSubscribe(time, configuration.getPoliceChannel());
			if (me().isHPDefined() && me().isBuriednessDefined() && me().getHP() > 0 && me().getBuriedness() > 0) {
				callForATHelp(time, MessageID.PLATOON_BURIED);
			}
			return;
		}
		handleHeard(time, heard);
		seenChanges.handleChanges(model, changes);
		/*
		 * addBuildingInfoToMessageSend(time);
		 * addInjuredHumanInfoToMessageSend(time);
		 */
		messageHandler.reportBuildingInfo(time, seenChanges);
		messageHandler.reportInjuredHumanInfo(time, seenChanges);
		updateClearedRoads();
		if (me().isHPDefined() && me().isBuriednessDefined() && me().getHP() > 0 && me().getBuriedness() > 0) {
			callForATHelp(time, MessageID.PLATOON_BURIED);
			System.out.println(me().getID() + " is buried,hp is" + me().getHP());
			return;
		}
		if (me().isHPDefined() && me().isDamageDefined() && me().getHP() > 0 && me().getDamage() > 0) {
			if (canMoveToRefuge(time, me(), refugesEntrancesMap)) {
			} else {
				callForATHelp(time, MessageID.PLATOON_BURIED);
			}
			System.out.println(me().getID() + " is buried,hp is" + me().getHP() + ",damage is" + me().getDamage());
		}
		List<EntityID> templist = new ArrayList<EntityID>();
		if (!buildingsEntrancesHumanStuckedIn.isEmpty()) {
			templist.addAll(buildingsEntrancesHumanStuckedIn);
			distanceSort(templist, me());
			pathByPriotity = search.getPath(me().getPosition(), templist.get(0), null);
		} else if (!positionsStuckedHuman.isEmpty()) {
			templist.addAll(positionsStuckedHuman);
			distanceSort(templist, me());
			pathByPriotity = search.getPath(me().getPosition(), templist.get(0), null);
		} else if (!positionsBlockdedMainRoadsTotally.isEmpty()) {
			templist.addAll(positionsBlockdedMainRoadsTotally);
			distanceSort(templist, me());
			pathByPriotity = search.getPath(me().getPosition(), templist.get(0), null);
		} else if (!positionsBlockedPlatoon.isEmpty()) {
			templist.addAll(positionsBlockedPlatoon);
			distanceSort(templist, me());
			pathByPriotity = search.getPath(me().getPosition(), templist.get(0), null);
		} else if (!positionsBlockedCivilians.isEmpty()) {
			templist.addAll(positionsBlockedCivilians);
			distanceSort(templist, me());
			pathByPriotity = search.getPath(me().getPosition(), templist.get(0), null);
		}
		basicClear(time, seenChanges);
	}

	private void updateClearedRoads() {
		Set<EntityID> clearedRoadsIDs = seenChanges.getClearedRoads();
		roadsExplored.addAll(clearedRoadsIDs);
		for (EntityID clearedRoadID : roadsExplored) {
			roadsUnexplored.remove(clearedRoadID);
			positionsBlockedCivilians.remove(clearedRoadID);
			positionsBlockedPlatoon.remove(clearedRoadID);
			positionsBlockdedMainRoadsTotally.remove(clearedRoadID);
		}
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
			// 居民发出voice呼救信息，要么受伤，要么被困
			if (content.equalsIgnoreCase("Ouch") || content.equalsIgnoreCase("Help")) {
				Civilian civilian = (Civilian) model.getEntity(agentID);
				EntityID civilianPositionID = civilian.getPosition();
				if (civilian.isHPDefined() && civilian.isBuriednessDefined() && civilian.isDamageDefined()
						&& civilian.getHP() > 0 && (civilian.getBuriedness() > 0 || civilian.getDamage() > 0)) {
					Message message = new Message(MessageID.CIVILIAN_INJURED, civilianPositionID, time,
							configuration.getAmbulanceChannel());
					messageHandler.addMessage(message);
				} else {
					if (roadsInCluster.contains(civilianPositionID))
						positionsBlockedCivilians.add(civilianPositionID);
				}
				System.out.println("pf of " + me().getID() + "heard civilian help");
				continue;
			}
			String[] heardInfo = content.split(",");
			String typeOfhearedInfo = heardInfo[0];
			String positionOfInfo = heardInfo[1];
			int id = Integer.parseInt(typeOfhearedInfo);
			int postion = Integer.parseInt(positionOfInfo);
			EntityID postionID = new EntityID(postion);
			MessageID messageID = MessageID.values()[id];
			if (!roadsInCluster.contains(postionID))
				continue;
			handleMessage(postionID, messageID);
		}
	}

	private void handleMessage(EntityID positionID, MessageID messageID) {
		System.out.println("pf of " + me().getID() + " is handling heard message ");
		switch (messageID) {
		case ROAD_CLEARED:
			roadsExplored.add(positionID);
			break;
		case CIVILIAN_BLOCKED:
			positionsBlockedCivilians.add(positionID);
			break;
		case PLATOON_BLOCKED:
			positionsBlockedPlatoon.add(positionID);
			break;
		case HUMAN_STUCKED:
			positionsStuckedHuman.add(positionID);
			break;
		case MAIN_ROAD_BLOCKED_TOTALLY:
			positionsBlockdedMainRoadsTotally.add(positionID);
			break;
		case ENTRANCE_BLOCKED_TOTALLY:
			buildingsEntrancesHumanStuckedIn.add(positionID);
			break;
		default:
			break;
		}
	}

	private void basicClear(int time, ChangeSetUtil seenChanges) {
		if (clearNearBlockes(time)) {
			System.out.println();
			return;
		}
		if (moveToSeenBlockade(time)) {
			System.out.println();
			return;
		} else if (pathByPriotity != null) {
			sendMove(time, pathByPriotity);
			pathByPriotity = null;
			System.out.println();
			return;
		} else {
			boolean traverslOk = traversalRoads(time);
			System.out.println();
			if (!traverslOk) {
				List<EntityID> path = randomWalk(roadsInCluster);
				if (path != null) {
					System.out.println(me().getID() + " random walk in cluster");
					System.out.println();
					sendMove(time, path);
				}
				System.out.println();
			}
			return;
		}
	}

	private boolean clearNearBlockes(int time) {
		Blockade target = getTargetBlockade();
		if (target != null) {
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
			System.out.println(me().getID() + "Clearing blockade " + target);
			return true;
		}
		return false;
	}

	private boolean moveToSeenBlockade(int time) {
		Blockade target = getSeenFarBlockade();
		if (target == null) {
			System.out.println(me().getID() + " 没有看到任何路障");
			return false;
		}
		Point2D closest = findClosestPoint(target, me().getX(), me().getY());
		List<EntityID> path = new ArrayList<EntityID>();
		if (target.getPosition().equals(me().getPosition())) {
			System.out.println("blockade " + target.getID() + "and human " + me().getID() + " is at same position "
					+ me().getPosition());
			path.add(me().getPosition());
			sendMove(time, path, (int) closest.getX(), (int) closest.getY());
			return true;
		} else {
			path = search.getPath(me().getPosition(), target.getPosition(), null);
			if (path != null) {
				if (somethingWrong()) {
					path.clear();
					path.add(nowPosition);
					Area entity = (Area) model.getEntity(nowPosition);
					if (entity instanceof Road) {
						Road road = (Road) entity;
						boolean flag = false;
						int x = 0, y = 0;
						for (Edge edge : road.getEdges()) {
							if (edge.isPassable()) {
								StandardEntity neirEntity = model.getEntity(edge.getNeighbour());
								if (neirEntity instanceof Building) {
									flag = true;
									continue;
								} else {
									x = (edge.getStartX() + edge.getEndX()) / 2;
									y = (edge.getStartY() + edge.getEndY()) / 2;
								}
							}
						}
						if (flag && x != 0 && y != 0) {
							sendMove(time, path, x, y);
							System.out.println(me().getID() + "往边的中间移动");
							return true;
						}
					}
				}
				sendMove(time, path, (int) closest.getX(), (int) closest.getY());
				System.out.println(me().getID() + " 移动到视力所及的不在同一条路上的有路障的地方");
				return true;
			}
		}
		return false;
	}

	private boolean traversalRoads(int time) {
		if (roadsUnexplored.size() == 0) {
			resetAllRoadsUnexplored();
		}
		if (roadsUnexplored.size() != 0) {
			List<EntityID> roadsIDs = new ArrayList<EntityID>(roadsUnexplored);
			System.out.println("roadsUnexplored has " + roadsIDs.size());
			distanceSort(roadsIDs, me());
			List<EntityID> path = search.getPath(me().getPosition(), roadsIDs.get(0), null);
			if (path != null) {
				System.out.println(me().getID() + " 遍历道路,非随机");
				roadsUnexplored.removeAll(path);
				Road r = (Road) model.getEntity(roadsIDs.get(0));
				sendMove(time, path, r.getX(), r.getY());
				return true;
			}
		}
		return false;
	}

	private void resetAllRoadsUnexplored() {
		Iterator<EntityID> roadsID = roadsInCluster.iterator();
		while (roadsID.hasNext()) {
			EntityID roadID = roadsID.next();
			System.out.println("add roads in cluster back " + roadID);
			roadsUnexplored.add(roadID);
		}
		System.out.println(me().getID() + " reset all roads unexplored " + roadsUnexplored.size());
	}

	private Blockade getSeenFarBlockade() {
		Area location = (Area) location();
		if (!(location instanceof Road))
			return null;
		Blockade result = getTargetBlockade(location, -1);
		if (result != null) {
			System.out.println(me().getID() + " 移动到同一条路 " + result.getID() + " 看到但清理不到的路障 ");
			return result;
		}
		for (EntityID next : location.getNeighbours()) {
			location = (Area) model.getEntity(next);
			if (!(location instanceof Road))
				continue;
			result = getTargetBlockade(location, -1);
			if (result != null) {
				System.out.println(me().getID() + " 移动到另一条路上 " + result.getID() + " 看到但清理不到的路障 ");
				return result;
			}
		}
		List<Blockade> otherBlockades = new ArrayList<Blockade>(seenChanges.getSeenBlockades());
		Collections.sort(otherBlockades, new DistanceSorter(location(), model));
		if (!otherBlockades.isEmpty()) {
			System.out.println(me().getID() + " 在当前路和和它的邻近路都没有看到路障，在其它地方看到了");
			return otherBlockades.get(0);
		}
		return null;
	}

	private Blockade getTargetBlockade() {
		Area location = (Area) location();
		if (!(location instanceof Road))
			return null;
		Blockade result = getTargetBlockade(location, maxClearDistance);
		if (result != null) {
			return result;
		}
		for (EntityID next : location.getNeighbours()) {
			location = (Area) model.getEntity(next);
			if (!(location instanceof Road))
				continue;
			result = getTargetBlockade(location, maxClearDistance);
			if (result != null) {
				System.out.println(me().getID() + " 能清理到的路障在另一条路上");
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
			if (maxDistance < 0) {
				System.out.println(me().getID() + " 看到但清理不到的路障 " + b.getID() + " 最短距离 " + d);
				return b;
			}
			if (d <= maxDistance) {
				System.out.println(me().getID() + " 距能清理的路障 " + b.getID() + " 最短距离" + d);
				return b;
			}
		}
		return null;
	}

	private int findDistanceTo(Blockade b, int x, int y) {
		List<Line2D> lines = GeometryTools2D.pointsToLines(GeometryTools2D.vertexArrayToPoints(b.getApexes()), true);
		double best = Double.MAX_VALUE;
		Point2D origin = new Point2D(x, y);
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

	private Point2D findClosestPoint(Blockade b, int x, int y) {
		List<Line2D> lines = GeometryTools2D.pointsToLines(GeometryTools2D.vertexArrayToPoints(b.getApexes()), true);
		double best = Double.MAX_VALUE;
		Point2D origin = new Point2D(x, y);
		Point2D closest = null;
		// 算距离最近的点
		for (Line2D next : lines) {
			Point2D near = GeometryTools2D.getClosestPointOnSegment(next, origin);
			double d = GeometryTools2D.getDistance(origin, near);
			if (d < best) {
				best = d;
				closest = near;
			}
		}
		return closest;
	}

	@Override
	public EnumSet<StandardEntityURN> getRequestedEntityURNsEnum() {
		return EnumSet.of(StandardEntityURN.POLICE_FORCE);
	}

	public void callForATHelp(int time, MessageID messageID) {
		Message message = new Message(messageID, me().getID(), time, configuration.getAmbulanceChannel());
		messageHandler.addMessage(message);
		sendRest(time);
		System.out.println(messageID.name() + " of " + me().getID() + " at " + time);
	}

	public boolean canMoveToRefuge(int time, Human me, Map<EntityID, Set<EntityID>> refugesEntrancesMap) {
		Set<EntityID> refugeIDsInCluster = refugesEntrancesMap.keySet();
		if (!refugeIDsInCluster.isEmpty()) {
			List<EntityID> refugeIDs = new ArrayList<EntityID>(refugeIDsInCluster);
			distanceSort(refugeIDs, me);
			EntityID dest = ((EntityID[]) getEntrancesOfRefuges().get(refugeIDs.get(0)).toArray())[0];
			List<EntityID> path = search.getPath(me.getPosition(), dest, null);
			path.add(refugeIDs.get(0));
			if (path != null) {
				Refuge refuge = (Refuge) model.getEntity(refugeIDs.get(0));
				Logger.info("Moving to refuge");
				sendMove(time, path, refuge.getX(), refuge.getY());
				System.out.println(me().getID() + "of at move to refuge for damage");
				return true;
			}
		}
		return false;
	}

	public void sendMessages(int time) {
		sendAllVoiceMessages(time);
		sendAllRadioMessages(time);
		messageHandler.getMessagesWillSend().clear();
	}

	private void sendAllRadioMessages(int time) {
		for (Message message : messageHandler.getMessagesWillSend()) {
			String data = message.getMessageID().ordinal() + "," + message.getPositionID().getValue();
			sendSpeak(time, message.getChannel(), data.getBytes());
		}
	}

	private void sendAllVoiceMessages(int time) {
		for (Message message : messageHandler.getMessagesWillSend()) {
			String data = message.getMessageID().ordinal() + "," + message.getPositionID().getValue();
			sendSpeak(time, message.getChannel(), data.getBytes());
		}
	}

	private boolean somethingWrong() {
		prePosition[1] = prePosition[0];
		prePosition[0] = nowPosition;
		nowPosition = me().getPosition();
		if (prePosition[0] != null && prePosition[1] != null && nowPosition != null
				&& nowPosition.equals(prePosition[0]) && prePosition[0].equals(prePosition[1])) {
			prePosition[0] = null;
			prePosition[1] = null;
			return true;
		}
		return false;
	}
}
