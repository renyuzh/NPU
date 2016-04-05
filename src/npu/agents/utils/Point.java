package npu.agents.utils;

import java.util.Set;

import rescuecore2.worldmodel.EntityID;

public class Point extends rescuecore2.misc.geometry.Point2D implements Comparable<Point>{
	private EntityID  id;
	private Set<Point> neighbours;
	private Point father;
	private int costF;
	private int costG;
	private int costH;
	private boolean canPass;
	public Point getFather() {
		return father;
	}
	public void setFather(Point father) {
		this.father = father;
	}
	public Set<Point> getNeighbours() {
		return neighbours;
	}
	public void setNeighbours(Set<Point> neighbours) {
		this.neighbours = neighbours;
	}
	public Point(double x, double y) {
		super(x, y);
	}
	public EntityID getId() {
		return id;
	}
	public void setId(EntityID id) {
		this.id = id;
	}
	public void setCostF(int costF) {
		this.costF = costF;
	}
	public void setCostG(int costG) {
		this.costG = costG;
	}
	public void setCostH(int costH) {
		this.costH = costH;
	}
	public int getCostF() {
		return costF;
	}
	public int getCostG() {
		return costG;
	}
	public int getCostH() {
		return costH;
	}
	public int getDistance(Point current) {
		return (int)(Math.abs(this.getX()-current.getX())+Math.abs(this.getY()-current.getY()));
	}
	@Override
	public int compareTo(Point p) {
		return (int)(this.getCostF() - p.getCostF());
	}
	public void setCanPass(boolean b) {
		this.canPass = b;
	}
	public boolean isCanPass() {
		return canPass;
	}
}
