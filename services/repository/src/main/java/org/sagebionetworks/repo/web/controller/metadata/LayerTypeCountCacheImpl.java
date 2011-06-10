package org.sagebionetworks.repo.web.controller.metadata;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.sagebionetworks.repo.model.DatastoreException;
import org.sagebionetworks.repo.model.InputDataLayer.LayerTypeNames;
import org.sagebionetworks.repo.model.NodeConstants;
import org.sagebionetworks.repo.model.NodeQueryDao;
import org.sagebionetworks.repo.model.NodeQueryResults;
import org.sagebionetworks.repo.model.ObjectType;
import org.sagebionetworks.repo.model.UserInfo;
import org.sagebionetworks.repo.model.query.BasicQuery;
import org.sagebionetworks.repo.model.query.Compartor;
import org.sagebionetworks.repo.model.query.CompoundId;
import org.sagebionetworks.repo.model.query.Expression;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

public class LayerTypeCountCacheImpl implements LayerTypeCountCache, InitializingBean {
	
	// The singleton map.
	static Map<String, Long> MAP = Collections.synchronizedMap(new HashMap<String, Long>());
	
	@Autowired
	NodeQueryDao nodeQueryDao;

	@Override
	public long getCountFor(String datasetId, LayerTypeNames layerType, UserInfo userInfo) throws DatastoreException {
		UserInfo.validateUserInfo(userInfo);
		// First determine if we have a value for this combination.
		String key = createKey(datasetId, layerType);
		Long count = MAP.get(key);
		if(count == null){
			BasicQuery query = createQuery(datasetId, layerType);
			// For now run this as an administrator
			UserInfo tempAdmin = new UserInfo(true);
			count = nodeQueryDao.executeCountQuery(query, tempAdmin);
			MAP.put(key, count);
		}
		return count;
	}

	@Override
	public void clearCacheFor(String datasetId, LayerTypeNames layerType) {
		String key = createKey(datasetId, layerType);
		// remove all data for this combination.
		MAP.remove(key);
	}
	
	@Override
	public void clearCacheFor(String datasetId) {
		// Remove each layer type
		LayerTypeNames[] types = LayerTypeNames.values();
		for(LayerTypeNames layerType: types){
			clearCacheFor(datasetId, layerType);
		}
	}
	
	/**
	 * Create a key.
	 * @param datasetId
	 * @param layerType
	 * @return
	 */
	public static String createKey(String datasetId, LayerTypeNames layerType){
		if(datasetId == null) throw new IllegalArgumentException("datasetId cannot be null");
		if(layerType == null) throw new IllegalArgumentException("LayerType cannot be null");
		return datasetId+layerType.name();
	}
	
	/**
	 * Create a query that selects the layer children of this dataset.
	 * @param parentId
	 * @return
	 */
	public static BasicQuery createChildrenLayerQuery(String parentId){
		BasicQuery query = new BasicQuery();
		// We want all children
		query.setFrom(ObjectType.layer);
		query.addExpression(new Expression(new CompoundId(null, NodeConstants.COL_PARENT_ID), Compartor.EQUALS, Long.parseLong(parentId)));
		return query;
	}
	
	/**
	 * Build a query to find the Clinical layers of the given dataset.
	 * @param datasetId
	 * @return
	 */
	public static BasicQuery createQuery(String datasetId, LayerTypeNames type){
		// Start with the children query
		BasicQuery query = createChildrenLayerQuery(datasetId);
		// Now add a filter for the clinical layer type.
		query.addExpression(new Expression(new CompoundId(null, NodeConstants.COLUMN_LAYER_TYPE), Compartor.EQUALS, type.name()));
		return query;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		// Warm up the cache on startup.
		UserInfo tempAdmin = new UserInfo(true);
		BasicQuery query = new BasicQuery();
		query.setFrom(ObjectType.dataset);
		query.setLimit(Long.MAX_VALUE);
		query.setOffset(0);
		try{
			// Look at all datasets
			NodeQueryResults results = nodeQueryDao.executeQuery(query, tempAdmin);
			if(results != null){
				List<String> datasetIds = results.getResultIds();
				if(datasetIds != null){
					for(String datasetId: datasetIds){
						for(LayerTypeNames type: LayerTypeNames.values()){
							this.getCountFor(datasetId, type, tempAdmin);
						}
					}
				}
			}
		}catch(Throwable e){
			// Skip the warmup if there is a problem.
		}
		
	}
}