package org.sagebionetworks.repo.manager.trash;

import org.sagebionetworks.repo.model.DatastoreException;
import org.sagebionetworks.repo.model.QueryResults;
import org.sagebionetworks.repo.model.TrashedEntity;
import org.sagebionetworks.repo.model.UnauthorizedException;
import org.sagebionetworks.repo.model.UserInfo;
import org.sagebionetworks.repo.web.NotFoundException;

/**
 * Manages entities in the trash can.
 *
 * @author Eric Wu
 */
public interface TrashManager {

	/**
	 * Moves an entity and its descendants to the trash can.
	 */
	void moveToTrash(UserInfo userInfo, String nodeId) throws NotFoundException, DatastoreException, UnauthorizedException;

	/**
	 * Moves an entity and its descendants out of the trash can. If the new parent is not given (null),
	 * will restore to the original parent before the delete.
	 */
	void restoreFromTrash(UserInfo userInfo, String nodeId, String newParentId) throws NotFoundException, DatastoreException, UnauthorizedException;

	/**
	 * Retrieves entities (in the trash can) deleted by the specified user.
	 */
	QueryResults<TrashedEntity> viewTrash(UserInfo userInfo, Integer offset, Integer limit);
}
