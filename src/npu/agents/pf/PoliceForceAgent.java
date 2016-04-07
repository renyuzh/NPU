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
import rescuecore2.messages.Command;
import rescuecore2.misc.geometry.GeometryTools2D;
import rescuecore2.misc.geometry.Line2D;
import rescuecore2.misc.geometry.Point2D;
import rescuecore2.misc.geometry.Vector2D;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.Blockade;
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
	private Set<EntityID> roadsInMyCluster = new HashSet<EntityID>();
	private Set<EntityID> roadsHasCleared = new HashSet<EntityID>();
	private Set<EntityID> roadsID;
	private List<EntityID> firingBuildings = new ArrayList<EntityID>();
	private List<EntityID> stuckingHumans = new ArrayList<EntityID>();
	private List<EntityID> nearbyStuckingHumans = new ArrayList<EntityID>();
	private List<EntityID> nearbyfiringBuildings = new ArrayList<EntityID>();
	private List<EntityID> refugeStuck = new ArrayList<EntityID>();

	private Set<Message> messagesSend = new HashSet<Message>();

	private EntityID[] prePosition = {};
	private EntityID nowPosition;

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

			// 将该簇内的道路拷贝一份
			roadsID = cluster.getRoadsAroundCluster();
			Iterator<EntityID> iter = roadsID.iterator();
			while (iter.hasNext()) {
				roadsInMyCluster.add(iter.next());
			}

			roadsAroundRefuge = cluster.getRoadARoundRefuge();
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("聚类失败");
		}
		distance = configuration.getMaxCleardistance();
	}

	@Override
	protected void think(int time, ChangeSet changes, Collection<Command> heard) {
		// model.merge(changes);
		if (time < configuration.getIgnoreAgentCommand())
			return;
		if (time == configuration.getIgnoreAgentCommand()) {
			sendSubscribe(time, configuration.getPoliceChannel());// 注册了该通道的都可以接收到
		}
		handleHeared(heard);
		ChangeSetUtil seenChanges = new ChangeSetUtil();
		seenChanges.handleChanges(model, changes);
		addFireInfo(seenChanges.getFireBuildings(), time);
		addInjuredHumanInfo(seenChanges.getInjuredHuman(), time);
		prePosition[1] = prePosition[0];
		prePosition[0] = nowPosition;
		nowPosition = me().getPosition();
		if (me().getHP() > 0 && (me().getBuriedness() > 0 || me().getDamage() > 0)) {
			handleInjuredMe(time);
			return;
		}
		removeClearedRoads();
		addAllClearedRoadsBack();
		removeFinishedTask(nowPosition);
		if (!refugeStuck.isEmpty()) {
			pathByPriotity = search.getPath(nowPosition, refugeStuck.get(0));
		} else if (!stuckingHumans.isEmpty()) {
			pathByPriotity = search.getPath(nowPosition, stuckingHumans.get(0));
		} else if (!firingBuildings.isEmpty()) {
			pathByPriotity = search.getPath(nowPosition, firingBuildings.get(0));
		}
		if (clearAllRoadsAroundRefuge(time, seenChanges))
			;
		else {
			basicClear(time, seenChanges);
		}
	}

	private void removeFinishedTask(EntityID nowPosition2) {
		if (refugeStuck.contains(nowPosition))
			refugeStuck.remove(nowPosition);
		if (stuckingHumans.contains(nowPosition))
			stuckingHumans.remove(nowPosition);
		if (firingBuildings.contains(nowPosition))
			firingBuildings.remove(nowPosition);
	}

	private boolean clearAllRoadsAroundRefuge(int time, ChangeSetUtil seenChanges) {
		if (clearNearBlockes(time))
			return true;
		Set<Blockade> seenBlockades = seenChanges.getSeenBlockadesAroundRefuge(roadsAroundRefuge);
		if (moveToSeenBlockade(time, seenBlockades))
			return true;
		return false;
	}

	private void handleHeared(Collection<Command> heards) {
		for (Command heard : heards) {
			if (!(heard instanceof AKSpeak))
				continue;
			AKSpeak info = (AKSpeak) heard;
			String content = new String(info.getContent());
			if (content.isEmpty())
				continue;
			String[] heardInfo = content.split(",");
			String typeOfhearedInfo = heardInfo[0];
			int id = Integer.parseInt(typeOfhearedInfo);
			MessageID messageID = MessageID.values()[id];
			EntityID entityID = info.getAgentID();
			if (configuration.getRadioChannels().contains(info.getChannel()))
				handleRadio(entityID, messageID);
			else
				handleVoice(entityID, messageID);
			if (messageID.equals(MessageID.REFUGE_STUCK)) {
				refugeStuck.add(new EntityID(Integer.parseInt(heardInfo[1])));
			}
		}
	}

	private void handleVoice(EntityID voice, MessageID messageID) {
		switch (messageID) {
		case HUMAN_STUCK:
			nearbyStuckingHumans.add(voice);
			break;
		case BUILDING_ON_FIRE:
			nearbyfiringBuildings.add(voice);
			break;
		default:
			break;
		}
	}

	private void handleRadio(EntityID radio, MessageID messageID) {
		switch (messageID) {
		case HUMAN_STUCK:
			stuckingHumans.add(radio);
			break;
		case BUILDING_ON_FIRE:
			firingBuildings.add(radio);
			break;
		default:
			break;
		}
	}

	public void addFireInfo(Set<EntityID> fireBuildings, int time) {
		for (EntityID buidingID : fireBuildings) {
			Message message = new Message(MessageID.BUILDING_ON_FIRE, buidingID, time, configuration.getFireChannel());
			messagesSend.add(message);
		}
	}

	public void addInjuredHumanInfo(Set<EntityID> injuredHuman, int time) {
		for (EntityID humanID : injuredHuman) {
			Message message = new Message(MessageID.INJURED_HUMAN, humanID, time, configuration.getAmbulanceChannel());
			messagesSend.add(message);
		}
	}

	public void handleInjuredMe(int time) {
		Message message = new Message(MessageID.PF_INJURED, me().getID(), time, configuration.getAmbulanceChannel());
		messagesSend.add(message);
		sendRest(time);
	}

	public boolean isStucked() {
		if (prePosition[0] == prePosition[1] && prePosition[0] == nowPosition)
			return true;
		else
			return false;
	}

	private void removeClearedRoads() {
		EntityID pos = me().getPosition();
		if (roadsInMyCluster.contains(pos)) {
			StandardEntity entity = model.getEntity(pos);
			Road r = (Road) entity;
			if (r.isBlockadesDefined() && r.getBlockades().isEmpty()) {
				roadsInMyCluster.remove(pos);
				roadsHasCleared.add(pos);
				System.out.println("removeClearedRoads");
			}
		}
	}

	private void addAllClearedRoadsBack() {
		if (roadsInMyCluster.size() == 0) {
			Iterator<EntityID> roadsID = roadsHasCleared.iterator();
			while (roadsID.hasNext()) {
				roadsInMyCluster.add(roadsID.next());
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
				sendMove(time, randomWalkAroundCluster(roadsID));
				System.out.println();
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
		List<EntityID> nearPath = search.getPath(me().getPosition(), getBlockedArea(blockades));
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
		if (roadsInMyCluster.size() != 0) {
			EntityID[] roadsID = roadsInMyCluster.toArray(new EntityID[0]);
			List<EntityID> path = search.getPath(me().getPosition(), roadsID[0]);
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
		double minDistance = Integer.MAX_VALUE;
		int x = me().getX();
		int y = me().getY();
		EntityID closestRoad = null;
		for (Blockade blockade : seenBlockades) {
			EntityID positionID = blockade.getPosition();
			StandardEntity positionEntity = model.getEntity(positionID);
			if (!(positionEntity instanceof Road))
				continue;
			double distance = findDistanceTo(blockade, x, y);
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
			double d = findDistanceTo(b, x, y);
			if (maxDistance < 0 && d != 0)
				System.out.println("移动到能看到的最短距离" + d);
			if (d < maxDistance) {
				System.out.println("距路障最短距离" + d);
				return b;
			}
		}
		return null;
	}

	private double findDistanceTo(Blockade b, int x, int y) {
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
		return best;
	}

	@Override
	protected EnumSet<StandardEntityURN> getRequestedEntityURNsEnum() {
		return EnumSet.of(StandardEntityURN.POLICE_FORCE);
	}

}
