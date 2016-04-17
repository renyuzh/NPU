package npu.agents.pf;

import java.util.ArrayList;
import java.util.Collection;
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
import rescuecore2.log.Logger;
import rescuecore2.messages.Command;
import rescuecore2.misc.geometry.GeometryTools2D;
import rescuecore2.misc.geometry.Line2D;
import rescuecore2.misc.geometry.Point2D;
import rescuecore2.misc.geometry.Vector2D;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.Blockade;
import rescuecore2.standard.entities.Civilian;
import rescuecore2.standard.entities.PoliceForce;
import rescuecore2.standard.entities.Refuge;
import rescuecore2.standard.entities.Road;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.standard.messages.AKSpeak;
import rescuecore2.worldmodel.ChangeSet;
import rescuecore2.worldmodel.EntityID;

public class PoliceForceAgent extends AbstractCommonAgent<PoliceForce> {
	private int distance;
	private Cluster cluster;
	private Set<EntityID> roadsUnexplored;
	private Set<EntityID> roadsExplored = new HashSet<EntityID>();
	private Set<EntityID> positionsBlockedCivilians = new HashSet<EntityID>();
	private Set<EntityID> positionsBlockedPlatoon = new HashSet<EntityID>();
	// private Set<EntityID> positionsBlockedRefuge = new HashSet<EntityID>();
	private Set<EntityID> positionsStuckedHuman = new HashSet<EntityID>();
	private Set<EntityID> positionsBlockdedRoadsTotally = new HashSet<EntityID>();
	private Set<EntityID> roadsInCluster;
	private Set<EntityID> buildingsInCluster;
	private Map<EntityID, Set<EntityID>> refugesEntrancesMap;
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
			buildingsInCluster = cluster.getBuildingsIDs();
			roadsInCluster = cluster.getRoadsInCluster();
			roadsUnexplored = cluster.getRoadsInCluster();
			refugesEntrancesMap = cluster.getRoadARoundRefuge();
			distance = configuration.getMaxCleardistance();
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("聚类失败");
		}
	}

	@Override
	protected void think(int time, ChangeSet changes, Collection<Command> heard) {
		if (time < configuration.getIgnoreAgentCommand())
			return;
		handleReceiveMessages(time, changes, heard);
		sendMessages(time);
	}

	private void handleReceiveMessages(int time, ChangeSet changes, Collection<Command> heard) {
		if (time == configuration.getIgnoreAgentCommand()) {
			sendSubscribe(time, configuration.getPoliceChannel());// 注册了该通道的可以接收发往该通道的消息
			if (me().isHPDefined() && me().isBuriednessDefined() && me().getHP() > 0 && me().getBuriedness() > 0) {
				callForATHelp(time, MessageID.PLATOON_BURIED);
			}
			return;
		}
		removeClearedRoads();
		addAllClearedRoadsBack();
		handleHeard(time, heard);
		seenChanges.handleChanges(model, changes);
		addBuildingInfoToMessageSend(time);
		addInjuredHumanInfoToMessageSend(time);
		updateClearedRoads();
		if (me().isHPDefined() && me().isBuriednessDefined() && me().getHP() > 0 && me().getBuriedness() > 0) {
			callForATHelp(time, MessageID.PLATOON_BURIED);
			System.out.println(me().getID() + " is buried,hp is" + me().getHP());
			return;
		}
		if (me().isHPDefined() && me().isDamageDefined() && me().getHP() > 0 && me().getDamage() > 0) {
			if (canMoveToRefuge(time,me(),refugesEntrancesMap)) {
			} else {
				callForATHelp(time, MessageID.PLATOON_BURIED);
			}
			System.out.println(me().getID() + " is buried,hp is" + me().getHP() + ",damage is" + me().getDamage());
		}
		List<EntityID> templist = new ArrayList<EntityID>();
		if (!positionsBlockdedRoadsTotally.isEmpty()) {
			templist.addAll(positionsBlockdedRoadsTotally);
			distanceSort(templist, me());
			pathByPriotity = search.getPath(me().getPosition(), templist.get(0), null);
		} /*
			 * else if (!positionsBlockedRefuge.isEmpty()) {
			 * templist.addAll(positionsBlockedRefuge); distanceSort(templist,
			 * me()); pathByPriotity = search.getPath(me().getPosition(),
			 * templist.get(0), null); }
			 */else if (!positionsStuckedHuman.isEmpty()) {
			templist.addAll(positionsStuckedHuman);
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
		/*
		 * if (clearBlockadesAroundRefuge(time, seenChanges)) { return; } else {
		 */
		basicClear(time, seenChanges);
		// }
	}

	private void updateClearedRoads() {
		Set<EntityID> clearedRoadsIDs = seenChanges.getClearedRoads();
		clearedRoadsIDs.addAll(roadsExplored);
		for (EntityID clearedRoadID : clearedRoadsIDs) {
			roadsUnexplored.remove(clearedRoadID);
			positionsBlockedCivilians.remove(clearedRoadID);
			positionsBlockedPlatoon.remove(clearedRoadID);
			// positionsBlockedRefuge.remove(clearedRoadID);
			positionsBlockdedRoadsTotally.remove(clearedRoadID);
		}
	}

	// 看到的，肯定只有一个refuge
	/*
	 * private boolean clearBlockadesAroundRefuge(int time, ChangeSetUtil
	 * seenChanges) { if (clearNearBlockes(time)) return true; Set<Blockade>
	 * seenBlockades =
	 * seenChanges.getSeenBlockadesAroundRefuge(refugesEntrancesMap); if
	 * (moveToSeenBlockade(time, seenBlockades)) { System.out.println(
	 * "clear blockades around refuge totally"); return true; } return false; }
	 */

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
					messagesWillSend.add(message);
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
		/*
		 * case REFUGE_BLOCKED: positionsBlockedRefuge.add(positionID);
		 */
		case ROAD_BLOCKED_TOTALLY:
			positionsBlockdedRoadsTotally.add(positionID);
			break;
		default:
			break;
		}
	}

	private void removeClearedRoads() {
		EntityID pos = me().getPosition();
		if (roadsUnexplored.contains(pos)) {
			StandardEntity entity = model.getEntity(pos);
			Road r = (Road) entity;
			if (r.isBlockadesDefined() && r.getBlockades().isEmpty()) {
				roadsUnexplored.remove(pos);
				roadsExplored.add(pos);
				System.out.println(me().getID() + " removeClearedRoads");
			}
		}
	}

	private void addAllClearedRoadsBack() {
		if (roadsUnexplored.size() == 0) {
			Iterator<EntityID> roadsID = roadsExplored.iterator();
			while (roadsID.hasNext()) {
				roadsUnexplored.add(roadsID.next());
			}
			roadsExplored.clear();
			System.out.println(me().getID() + " addAllClearedRoadsBack");
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
				List<EntityID> path = randomWalk(roadsInCluster);
				System.out.println(me().getID()+" random walk in cluster");
				if(path != null )
					sendMove(time, path);
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

	private boolean moveToSeenBlockade(int time, Set<Blockade> blockades) {
		List<EntityID> nearPath = search.getPath(me().getPosition(), getBlockedArea(blockades), null);
		if (nearPath != null) {
			Road r = (Road) model.getEntity(nearPath.get(nearPath.size() - 1));
			Blockade b = getTargetBlockade(r, -1);
			if (b != null) {
				sendMove(time, nearPath, b.getX(), b.getY());
				System.out.println(me().getID() + " 移动到视力所及的有路障的地方");
				return true;
			}
		}
		return false;
	}

	private boolean traversalRoads(int time) {
		if (roadsUnexplored.size() != 0) {
			List<EntityID> roadsIDs = new ArrayList<EntityID>(roadsUnexplored);
			distanceSort(roadsIDs, me());
			List<EntityID> path = search.getPath(me().getPosition(), roadsIDs.get(0), null);
			if (path != null) {
				System.out.println(me().getID() + " 遍历道路,非随机");
				System.out.println();
				Road r = (Road) model.getEntity(roadsIDs.get(0));
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
			if (maxDistance < 0 && d != 0) {
				System.out.println(me().getID() + " 移动到能看到的最短距离" + d);
				return b;
			}
			if (d < maxDistance) {
				System.out.println(me().getID() + " 距路障最短距离" + d);
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
