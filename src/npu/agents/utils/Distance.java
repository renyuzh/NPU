package npu.agents.utils;

import rescuecore2.misc.geometry.GeometryTools2D;
import rescuecore2.misc.geometry.Point2D;

public class Distance implements Comparable<Distance>{
	private Point2D source;
	private Point2D dest;
	private double distance;
	public Distance(Point2D source,Point2D dest) {
		this.source = source;
		this.dest = dest;
		this.distance = GeometryTools2D.getDistance(source,dest);
	}
	public Point2D getSource() {
		return source;
	}
	public Point2D getDest() {
		return dest;
	}
	public double getDistance() {
		return distance;
	}
	@Override
	public int compareTo(Distance o) {
		if(o.getDistance() > getDistance()) {
			return -1;
		}else
			return 1;
	}

}
