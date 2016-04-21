package npu.agents.communication.utils;

import java.util.List;

import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.Road;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardWorldModel;
import rescuecore2.worldmodel.EntityID;

public class MessageCompressUtil {
	public static StandardWorldModel model;
	public static List<StandardEntity> allRoads;
	public static List<StandardEntity> allBuildings;
	public static List<StandardEntity> allEntities;
	public static void init(StandardWorldModel world,List<StandardEntity> list1,List<StandardEntity> list2,List<StandardEntity> list3){
		model = world;
		allRoads = list1;
		allBuildings = list2;
		allEntities = list3;
	}
	public static int getAreaIndex(EntityID id){
		return binarySearch(allEntities,model.getEntity(id));
	}
	public static int getRoadIndex(EntityID id){
		return binarySearch(allRoads,model.getEntity(id));
	}
	public static int getBuildingIndex(EntityID id){
		return binarySearch(allBuildings,model.getEntity(id));
	}
	public static Road getRoadByIndex(int index){
		StandardEntity entity = allRoads.get(index);
		if(entity instanceof Road)
			return (Road)entity;
		return null;
	}
	public static Building getBuildingByIndex(int index){
		StandardEntity entity = allRoads.get(index);
		if(entity instanceof Building)
			return (Building)entity;
		return null;
	}
	public static Area getAreaByIndex(int index){
		StandardEntity entity = allEntities.get(index);
		if(entity instanceof Area)
			return (Area)entity;
		return null;
	}
	public static int binarySearch(List<StandardEntity> entities,
			StandardEntity standardEntity) {
		if (entities == null || entities.size() == 0)
			return standardEntity.getID().getValue();
		StandardEntity[] array = entities.toArray(new StandardEntity[entities
				.size()]);
		return binarySearchUtil(array, array.length,standardEntity);
	}
	public static int binarySearchUtil(StandardEntity[] a, int len, StandardEntity goal)
	{
	    int low = 0;
	    int high = len -1;
	    while (low <= high)
	    {
	        int middle = (high - low) / 2 + low; // 直接使用(high + low) / 2 可能导致溢出
	        if (a[middle].getID().getValue() == goal.getID().getValue())
	            return middle;
	        //在左半边
	        else if (a[middle].getID().getValue() > goal.getID().getValue())
	            high = middle - 1;
	        //在右半边
	        else
	            low = middle + 1;
	    }
	    //没找到
	    return -1;
	}
}
