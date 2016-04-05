package npu.test;

import java.util.HashSet;
import java.util.Set;

import npu.agents.utils.Point;
import rescuecore2.worldmodel.EntityID;

public class TestPoint {

	public static void main(String[] args) {
		Set<Point> s = new HashSet<Point>();
		Point p = new Point(2.1,2.1);
		EntityID id = new EntityID(222);
		p.setId(id);
		s.add(p);
		Point p1 = new Point(2.1,2.1);
		System.out.println(s.contains(p1));
	}

}
