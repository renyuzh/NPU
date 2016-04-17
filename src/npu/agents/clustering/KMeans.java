package npu.agents.clustering;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;

import npu.agents.utils.Point;
import rescuecore2.misc.geometry.GeometryTools2D;

public class KMeans {
	/**
	 * @param k
	 *            簇的个数
	 * @param iterTimes
	 *            迭代次数
	 * @param allPoints
	 *            需要聚类的数据点
	 * @return
	 */
	public  List<Cluster> getClusters(int k, int iterTimes, List<Point> allPoints) {
		Set<Point> firstCenters = initFirstCenters(k, allPoints);
		List<Cluster> firstClustersCount = prepareClustersCount(firstCenters);
		List<Cluster> firstClusters = addEachPointTocluster(firstCenters, firstClustersCount, allPoints);
		List<Cluster> lastClusters = updateClusters(firstClusters, iterTimes, allPoints);
		return updateCenterToPoint(lastClusters, allPoints);
	}

	/**
	 * 选择随机初始中心点
	 * 
	 * @param k
	 * @param allPoints
	 * @return
	 */
	private  Set<Point> initFirstCenters(int k, List<Point> allPoints) {
		System.out.println("initFirstCenters");
		Set<Point> centers = new HashSet<Point>();
		Random random = new Random();
		int seed = 0;
		while (centers.size() < k) {
			seed = random.nextInt(allPoints.size());
			centers.add(allPoints.get(seed));
		}
		System.out.println("init centers "+centers.size());
		return centers;
	}

	/**
	 * 更新中心点
	 * 
	 * @param originClusters
	 * @return
	 */
	private  Set<Point> updateCenters(List<Cluster> originClusters,List<Point> allPoints) {
		System.out.println("updateCenters");
		System.out.println(originClusters.size()+"originClusterSize");
		Set<Point> centerPoints = new HashSet<Point>();
		for (int i = 0; i < originClusters.size(); i++) {
			Cluster cluster = originClusters.get(i);
			List<Point> pointsInCluster = new ArrayList<>(cluster.getMembers());
			int size = pointsInCluster.size();
			double x = 0.0, y = 0.0;
			for (int j = 0; j < size; j++) {
				x += pointsInCluster.get(j).getX();
				y += pointsInCluster.get(j).getY();
			}
			Point newCenter = new Point(x / size, y / size);
			int seed = 0;
		    while(centerPoints.contains(newCenter)){
		    	seed = new Random().nextInt(allPoints.size());
				newCenter = allPoints.get(seed);
			}
			//很大可能会出现相同的中心点，这样以来就可能分得比指定K少的簇
			centerPoints.add(newCenter);
		}
		System.out.println(centerPoints.size()+"update centerPoints");
		return centerPoints;
	}

	/**
	 * 聚类的中心设置为给定数据集中确定的点
	 * 
	 * @param clusters
	 * @param allPoints
	 * @return
	 */
	private  List<Cluster> updateCenterToPoint(List<Cluster> clusters, List<Point> allPoints) {
		System.out.println("updateCenterToPoint");
		for (Cluster cluster : clusters) {
			double minDistance = Double.MAX_VALUE;
			boolean flag = false;
			Point tempPoint = null;
			Point source = cluster.getCenterPoint();
			for (Point point : allPoints) {
				if (source.equals(point)) {
					flag = false;
					break;
				}
				flag = true;
				Double distance = GeometryTools2D.getDistance(source, point);
				if (distance < minDistance) {
					minDistance = distance;
					tempPoint = point;
				}
			}
			if (flag) {
				cluster.setCenterPoint(tempPoint);
			}
		}
		return clusters;
	}

	/**
	 * 用给定中心点初始簇
	 * 
	 * @param centerPoints
	 * @return
	 */
	private  List<Cluster> prepareClustersCount(Set<Point> centerPoints) {
		System.out.println("prepareClustersCount");
		List<Cluster> clusters = new ArrayList<Cluster>();
		Iterator<Point> iter = centerPoints.iterator();
		while (iter.hasNext()) {
			Point next = iter.next();
			Cluster cluster = new Cluster(next);
			if (next.getId() != null) {
				cluster.addMember(next);// 第一次初始化时的随机中心点也是成员，之后计算出来的不带id的新中心点都不是成员
			}
			clusters.add(cluster);
		}
		System.out.println("prepareClustersCount "+clusters.size());
		return clusters;
	}

	/**
	 * 聚类，将每个点划分到离它最近的中心点
	 * 
	 * @param centerPoints
	 * @param clusters
	 * @param allPoints
	 * @return
	 */
	private  List<Cluster> addEachPointTocluster(Set<Point> centerPoints, List<Cluster> clusters,
			List<Point> allPoints) {
		System.out.println("addEachPointTocluster");
		Point[] centers = centerPoints.toArray(new Point[0]);
		// TreeSet<Distance> distancesToCenter = new TreeSet<Distance>();
		for (int i = 0; i < allPoints.size(); i++) {
			Double minDistance = Double.MAX_VALUE;
			Point tempSource = null;
			Point tempDest = null;
			boolean flag = false;
			for (int j = 0; j < centerPoints.size(); j++) {
				if (centerPoints.contains(allPoints.get(i)))// 虽然new的是新对象，但是重写的hashcode只用到了坐标
					break;
				Point source = allPoints.get(i);
				Point dest = centers[j];
				Double distance = GeometryTools2D.getDistance(source, dest);
				if (distance < minDistance) {
					minDistance = distance;
					tempSource = source;
					tempDest = dest;
					flag = true;
				}
			}
			if (flag) {
				for (int m = 0; m < centerPoints.size(); m++) {
					if (tempDest.equals(clusters.get(m).getCenterPoint()))
						clusters.get(m).addMember(tempSource);
				}
			}
		}
		System.out.println("addEachPointTocluster "+clusters.size());
		return clusters;
	}

	/**
	 * 迭代获取最新的簇
	 * 
	 * @param clusters
	 * @param iterTimes
	 * @param allPoints
	 * @return
	 */
	private  List<Cluster> updateClusters(List<Cluster> clusters, int iterTimes, List<Point> allPoints) {
		System.out.println("updateClusters");
		Set<Point> lastCenterPoints = new HashSet<Point>();
		for (int times = 0; times < iterTimes; times++) {
			System.out.print(times);
			Set<Point> centerPoints = updateCenters(clusters,allPoints);
			if (lastCenterPoints.containsAll(centerPoints))
				break;
			lastCenterPoints = centerPoints;
			clusters = addEachPointTocluster(centerPoints, prepareClustersCount(centerPoints), allPoints);
		}
		System.out.println(clusters.size());
		return clusters;
	}
}
