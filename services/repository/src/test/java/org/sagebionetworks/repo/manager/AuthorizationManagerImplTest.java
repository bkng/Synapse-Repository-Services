package org.sagebionetworks.repo.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.repo.model.ACCESS_TYPE;
import org.sagebionetworks.repo.model.AccessControlList;
import org.sagebionetworks.repo.model.AuthorizationConstants;
import org.sagebionetworks.repo.model.EntityType;
import org.sagebionetworks.repo.model.Node;
import org.sagebionetworks.repo.model.ResourceAccess;
import org.sagebionetworks.repo.model.UserGroup;
import org.sagebionetworks.repo.model.UserInfo;
import org.sagebionetworks.repo.model.auth.UserEntityPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "classpath:test-context.xml" })
public class AuthorizationManagerImplTest {
	
	@Autowired
	AuthorizationManager authorizationManager;
	@Autowired
	NodeManager nodeManager;
	@Autowired
	private UserManager userManager;
	
	@Autowired
	PermissionsManager permissionsManager;
	
		
	private Collection<Node> nodeList = new ArrayList<Node>();
	private Node node = null;
	private Node nodeCreatedByTestUser = null;
	private Node childNode = null;
	private UserInfo userInfo = null;
	private UserInfo adminUser;
	private static final String TEST_USER = "test-user";
	
	private List<String> usersToDelete;
	
	private Node createDTO(String name, Long createdBy, Long modifiedBy, String parentId) {
		Node node = new Node();
		node.setName(name);
		node.setCreatedOn(new Date());
		node.setCreatedByPrincipalId(createdBy);
		node.setModifiedOn(new Date());
		node.setModifiedByPrincipalId(modifiedBy);
		node.setNodeType(EntityType.project.name());
		if (parentId!=null) node.setParentId(parentId);
		return node;
	}
	
	private Node createNode(String name, UserInfo creator, Long modifiedBy, String parentId) throws Exception {
		Node node = createDTO(name, Long.parseLong(creator.getIndividualGroup().getId()), modifiedBy, parentId);
		String nodeId = nodeManager.createNewNode(node, creator);
		assertNotNull(nodeId);
		node.setId(nodeId);
		return node;
	}

	@Before
	public void setUp() throws Exception {
		// userInfo
		userManager.setUserDAO(new TestUserDAO()); // could use Mockito here
		userInfo = userManager.getUserInfo("AuthorizationManagerImplTest.testuser@foo.bar");
		usersToDelete = new ArrayList<String>();
		usersToDelete.add(userInfo.getIndividualGroup().getId());
		usersToDelete.add(userInfo.getUser().getId());
		
		adminUser = userManager.getUserInfo(TestUserDAO.ADMIN_USER_NAME);
		usersToDelete.add(adminUser.getIndividualGroup().getId());
		usersToDelete.add(adminUser.getUser().getId());
		Random rand = new Random();
		// create a resource
		node = createNode("foo_"+rand.nextLong(), adminUser, 2L, null);
		nodeList.add(node);
				
		childNode = createNode("foo2_"+rand.nextLong(), adminUser, 4L, node.getId());

		Long testUserPrincipalId = Long.parseLong(userInfo.getIndividualGroup().getId());
		nodeCreatedByTestUser = createNode("bar_"+rand.nextLong(), userInfo, testUserPrincipalId, null);
		
		nodeList.add(nodeCreatedByTestUser);
	}

	@After
	public void tearDown() throws Exception {
		UserInfo adminUser = userManager.getUserInfo(TestUserDAO.ADMIN_USER_NAME);
		for (Node n : nodeList) nodeManager.delete(adminUser, n.getId());
		this.node=null;
		
		if(userManager != null && usersToDelete != null){
			for(String idToDelete: usersToDelete){
				userManager.deleteUser(idToDelete);
			}
		}
		
	}
	
	// test that removing a user from the ACL for their own node doesn't remove their access
	@Test
	public void testOwnership() throws Exception {
		String pIdString = userInfo.getIndividualGroup().getId();
		Long pId = Long.parseLong(pIdString);
		assertTrue(authorizationManager.canAccess(userInfo, nodeCreatedByTestUser.getId(), ACCESS_TYPE.READ));
		// remove user from ACL
		AccessControlList acl = permissionsManager.getACL(nodeCreatedByTestUser.getId(), userInfo);
		assertNotNull(acl);
		//acl = AuthorizationHelper.addToACL(acl, userInfo.getIndividualGroup(), ACCESS_TYPE.READ);
		Set<ResourceAccess> ras = acl.getResourceAccess();
		boolean foundit = false;
		for (ResourceAccess ra : ras) {
			Long raPId = ra.getPrincipalId();
			assertNotNull(raPId);
			if (raPId.equals(pId)) {
				foundit=true;
				ras.remove(ra);
				break;
			}
		}
		assertTrue(foundit);
		acl = permissionsManager.updateACL(acl, adminUser);

		assertTrue(authorizationManager.canAccess(userInfo, nodeCreatedByTestUser.getId(), ACCESS_TYPE.READ));
	}
	
	
	@Test
	public void testCanAccessAsIndividual() throws Exception {
		// test that a user can access something they've been given access to individually
		// no access yet
		assertFalse(authorizationManager.canAccess(userInfo, node.getId(), ACCESS_TYPE.READ));
		AccessControlList acl = permissionsManager.getACL(node.getId(), userInfo);
		assertNotNull(acl);
		acl = AuthorizationHelper.addToACL(acl, userInfo.getIndividualGroup(), ACCESS_TYPE.READ);
		acl = permissionsManager.updateACL(acl, adminUser);
		// now they should be able to access
		assertTrue(authorizationManager.canAccess(userInfo, node.getId(), ACCESS_TYPE.READ));
		// but they do not have a different kind of access
		assertFalse(authorizationManager.canAccess(userInfo, node.getId(), ACCESS_TYPE.DELETE));
	}
	
	@Test 
	public void testCanAccessGroup() throws Exception {
		// test that a user can access something accessible to a group they belong to
		boolean b = authorizationManager.canAccess(userInfo, node.getId(), ACCESS_TYPE.READ);
		// no access yet
		assertFalse(b);
		AccessControlList acl = permissionsManager.getACL(node.getId(), userInfo);
		assertNotNull(acl);
		UserGroup g = userManager.findGroup(TestUserDAO.TEST_GROUP_NAME, false);
		acl = AuthorizationHelper.addToACL(acl, g, ACCESS_TYPE.READ);
		acl = permissionsManager.updateACL(acl, adminUser);
		// now they should be able to access
		b = authorizationManager.canAccess(userInfo, node.getId(), ACCESS_TYPE.READ);
		assertTrue(b);
	}
	
	@Test 
	public void testCanAccessPublicGroup() throws Exception {
		// test that a user can access a Public resource
		boolean b = authorizationManager.canAccess(userInfo, node.getId(), ACCESS_TYPE.READ);
		// no access yet
		assertFalse(b);
		AccessControlList acl = permissionsManager.getACL(node.getId(), userInfo);
		assertNotNull(acl);
		UserGroup pg = userManager.findGroup(AuthorizationConstants.PUBLIC_GROUP_NAME, false);
		acl = AuthorizationHelper.addToACL(acl, pg, ACCESS_TYPE.READ);
		acl = permissionsManager.updateACL(acl, adminUser);
		// now they should be able to access
		b = authorizationManager.canAccess(userInfo, node.getId(), ACCESS_TYPE.READ);
		assertTrue(b);
	}
	
	@Test 
	public void testAnonymousCantAccessPublicGroup() throws Exception {
		AccessControlList acl = permissionsManager.getACL(node.getId(), userInfo);
		assertNotNull(acl);
		UserGroup pg = userManager.findGroup(AuthorizationConstants.PUBLIC_GROUP_NAME, false);
		acl = AuthorizationHelper.addToACL(acl, pg, ACCESS_TYPE.READ);
		acl = permissionsManager.updateACL(acl, adminUser);
		UserInfo anonInfo = userManager.getUserInfo(AuthorizationConstants.ANONYMOUS_USER_ID);
		boolean b = authorizationManager.canAccess(anonInfo, node.getId(), ACCESS_TYPE.READ);
		assertTrue(b);
	}
	
	@Test
	public void testCanAccessAsAnonymous() throws Exception {
		UserInfo anonInfo = userManager.getUserInfo(AuthorizationConstants.ANONYMOUS_USER_ID);
		AccessControlList acl = permissionsManager.getACL(node.getId(), userInfo);
		assertNotNull(acl);
		// give some other group access
		UserGroup g = userManager.findGroup(TestUserDAO.TEST_GROUP_NAME, false);
		acl = AuthorizationHelper.addToACL(acl, g, ACCESS_TYPE.READ);
		acl = permissionsManager.updateACL(acl, adminUser);
		// anonymous does not have access
		boolean b = authorizationManager.canAccess(anonInfo, node.getId(), ACCESS_TYPE.READ);
		assertFalse(b);
	}
	
	@Test 
	public void testCanAccessAdmin() throws Exception {
		// test that an admin can access anything
		UserInfo adminInfo = userManager.getUserInfo(TestUserDAO.ADMIN_USER_NAME);
		// test that admin can access anything
		boolean b = authorizationManager.canAccess(adminInfo, node.getId(), ACCESS_TYPE.READ);
		assertTrue(b);
	}
	
	@Test
	public void testCanAccessInherited() throws Exception {		
		// no access yet to parent
		assertFalse(authorizationManager.canAccess(userInfo, node.getId(), ACCESS_TYPE.READ));

		AccessControlList acl = permissionsManager.getACL(node.getId(), userInfo);
		assertNotNull(acl);
		acl = AuthorizationHelper.addToACL(acl, userInfo.getIndividualGroup(), ACCESS_TYPE.READ);
		acl = permissionsManager.updateACL(acl, adminUser);
		// now they should be able to access
		assertTrue(authorizationManager.canAccess(userInfo, node.getId(), ACCESS_TYPE.READ));
		// and the child as well
		assertTrue(authorizationManager.canAccess(userInfo, childNode.getId(), ACCESS_TYPE.READ));
		
		UserEntityPermissions uep = authorizationManager.getUserPermissionsForEntity(userInfo,  node.getId());
		assertEquals(true, uep.getCanView());
		uep = authorizationManager.getUserPermissionsForEntity(userInfo,  childNode.getId());
		assertEquals(true, uep.getCanView());
		assertFalse(uep.getCanEnableInheritance());
		assertEquals(node.getCreatedByPrincipalId(), uep.getOwnerPrincipalId());
		
		acl = AuthorizationHelper.addToACL(acl, userInfo.getIndividualGroup(), ACCESS_TYPE.CHANGE_PERMISSIONS);
		acl = permissionsManager.updateACL(acl, adminUser);
		assertFalse(uep.getCanEnableInheritance());
		
	}

	// test lack of access to something that doesn't inherit its permissions, whose parent you CAN access
	@Test
	public void testCantAccessNotInherited() throws Exception {		
		// no access yet to parent
		assertFalse(authorizationManager.canAccess(userInfo, node.getId(), ACCESS_TYPE.READ));

		AccessControlList acl = permissionsManager.getACL(node.getId(), userInfo);
		assertNotNull(acl);
		acl.setId(childNode.getId());
		UserInfo adminInfo = userManager.getUserInfo(TestUserDAO.ADMIN_USER_NAME);
		permissionsManager.overrideInheritance(acl, adminInfo); // must do as admin!
		// permissions haven't changed (yet)
		assertFalse(authorizationManager.canAccess(userInfo, node.getId(), ACCESS_TYPE.READ));
		assertFalse(authorizationManager.canAccess(userInfo, childNode.getId(), ACCESS_TYPE.READ));
		
		UserEntityPermissions uep = authorizationManager.getUserPermissionsForEntity(userInfo,  node.getId());
		assertEquals(false, uep.getCanView());
		uep = authorizationManager.getUserPermissionsForEntity(userInfo,  childNode.getId());
		assertEquals(false, uep.getCanView());
		assertFalse(uep.getCanEnableInheritance());
		assertEquals(node.getCreatedByPrincipalId(), uep.getOwnerPrincipalId());
		
		// get a new copy of parent ACL
		acl = permissionsManager.getACL(node.getId(), userInfo);
		acl = AuthorizationHelper.addToACL(acl, userInfo.getIndividualGroup(), ACCESS_TYPE.READ);
		acl = permissionsManager.updateACL(acl, adminUser);
		// should be able to access parent but not child
		assertTrue(authorizationManager.canAccess(userInfo, node.getId(), ACCESS_TYPE.READ));
		assertFalse(authorizationManager.canAccess(userInfo, childNode.getId(), ACCESS_TYPE.READ));
		
		uep = authorizationManager.getUserPermissionsForEntity(userInfo,  node.getId());
		assertEquals(true, uep.getCanView());
		uep = authorizationManager.getUserPermissionsForEntity(userInfo,  childNode.getId());
		assertEquals(false, uep.getCanView());
	}
	
	@Test
	public void testCreate() throws Exception {
		// make an object on which you have READ and WRITE permission
		AccessControlList acl = permissionsManager.getACL(node.getId(), userInfo);
		assertNotNull(acl);
		acl = AuthorizationHelper.addToACL(acl, userInfo.getIndividualGroup(), ACCESS_TYPE.READ);
		acl = permissionsManager.updateACL(acl, adminUser);
		
		acl = permissionsManager.getACL(node.getId(), userInfo);
		acl = AuthorizationHelper.addToACL(acl, userInfo.getIndividualGroup(), ACCESS_TYPE.UPDATE);
		acl = permissionsManager.updateACL(acl, adminUser);
		// now they should be able to access
		assertTrue(authorizationManager.canAccess(userInfo, node.getId(), ACCESS_TYPE.READ));				
		// but can't add a child 
		Node child = createDTO("child", 10L, 11L, node.getId());
		assertFalse(authorizationManager.canCreate(userInfo, child));
		
		// but give them create access to the parent
		acl = permissionsManager.getACL(node.getId(), userInfo);
		acl = AuthorizationHelper.addToACL(acl, userInfo.getIndividualGroup(), ACCESS_TYPE.CREATE);
		acl = permissionsManager.updateACL(acl, adminUser);
		// now it can
		assertTrue(authorizationManager.canCreate(userInfo, child));
		
	}

	@Test
	public void testCreateSpecialUsers() throws Exception {
		// admin always has access 
		UserInfo adminInfo = userManager.getUserInfo(TestUserDAO.ADMIN_USER_NAME);
		Node child = createDTO("child", 10L, 11L, node.getId());
		assertTrue(authorizationManager.canCreate(adminInfo, child));

		// allow some access
		AccessControlList acl = permissionsManager.getACL(node.getId(), userInfo);
		assertNotNull(acl);
		acl = AuthorizationHelper.addToACL(acl, userInfo.getIndividualGroup(), ACCESS_TYPE.CREATE);
		acl = permissionsManager.updateACL(acl, adminUser);
		// now they should be able to access
		assertTrue(authorizationManager.canCreate(userInfo, child));
		
		// but anonymous cannot
		UserInfo anonInfo = userManager.getUserInfo(AuthorizationConstants.ANONYMOUS_USER_ID);
		assertFalse(authorizationManager.canCreate(anonInfo, child));
	}

	@Test
	public void testCreateNoParent() throws Exception {
	
		// try to create node with no parent.  should fail
		Node orphan = createDTO("orphan", 10L, 11L, null);
		assertFalse(authorizationManager.canCreate(userInfo, orphan));
		
		// admin creates a node with no parent.  should work
		UserInfo adminInfo = userManager.getUserInfo(TestUserDAO.ADMIN_USER_NAME);
		assertTrue(authorizationManager.canCreate(adminInfo, orphan));

	}
	
	@Test
	public void testGetUserPermissionsForEntity() throws Exception{
		UserInfo adminInfo = userManager.getUserInfo(TestUserDAO.ADMIN_USER_NAME);
		assertTrue(adminInfo.isAdmin());
		// the admin user can do it all
		UserEntityPermissions uep = authorizationManager.getUserPermissionsForEntity(adminInfo,  node.getId());
		assertNotNull(uep);
		assertEquals(true, uep.getCanAddChild());
		assertEquals(true, uep.getCanChangePermissions());
		assertEquals(true, uep.getCanDelete());
		assertEquals(true, uep.getCanEdit());
		assertEquals(true, uep.getCanView());
		assertEquals(true, uep.getCanDownload());
		
		// the user cannot do anything
		uep = authorizationManager.getUserPermissionsForEntity(userInfo,  node.getId());
		assertEquals(false, uep.getCanAddChild());
		assertEquals(false, uep.getCanChangePermissions());
		assertEquals(false, uep.getCanDelete());
		assertEquals(false, uep.getCanEdit());
		assertEquals(false, uep.getCanView());
		assertEquals(true, uep.getCanDownload()); // can't read but CAN download, which is controlled separately
		assertEquals(false, uep.getCanEnableInheritance());
		assertEquals(node.getCreatedByPrincipalId(), uep.getOwnerPrincipalId());
		
		// Let the user read.
		AccessControlList acl = permissionsManager.getACL(node.getId(), userInfo);
		assertNotNull(acl);
		acl = AuthorizationHelper.addToACL(acl, userInfo.getIndividualGroup(), ACCESS_TYPE.READ);
		acl = permissionsManager.updateACL(acl, adminUser);
		
		uep = authorizationManager.getUserPermissionsForEntity(userInfo,  node.getId());
		assertEquals(false, uep.getCanAddChild());
		assertEquals(false, uep.getCanChangePermissions());
		assertEquals(false, uep.getCanDelete());
		assertEquals(false, uep.getCanEdit());
		assertEquals(true, uep.getCanView());
		assertEquals(true, uep.getCanDownload()); // can't read but CAN download, which is controlled separately
		
		// Let the user update.
		acl = permissionsManager.getACL(node.getId(), userInfo);
		assertNotNull(acl);
		acl = AuthorizationHelper.addToACL(acl, userInfo.getIndividualGroup(), ACCESS_TYPE.UPDATE);
		acl = permissionsManager.updateACL(acl, adminUser);
		
		uep = authorizationManager.getUserPermissionsForEntity(userInfo,  node.getId());
		assertEquals(false, uep.getCanAddChild());
		assertEquals(false, uep.getCanChangePermissions());
		assertEquals(false, uep.getCanDelete());
		assertEquals(true, uep.getCanEdit());
		assertEquals(true, uep.getCanView());
		assertEquals(true, uep.getCanDownload()); // can't read but CAN download, which is controlled separately
		
		// Let the user delete.
		acl = permissionsManager.getACL(node.getId(), userInfo);
		assertNotNull(acl);
		acl = AuthorizationHelper.addToACL(acl, userInfo.getIndividualGroup(), ACCESS_TYPE.DELETE);
		acl = permissionsManager.updateACL(acl, adminUser);
		
		uep = authorizationManager.getUserPermissionsForEntity(userInfo,  node.getId());
		assertEquals(false, uep.getCanAddChild());
		assertEquals(false, uep.getCanChangePermissions());
		assertEquals(true, uep.getCanDelete());
		assertEquals(true, uep.getCanEdit());
		assertEquals(true, uep.getCanView());
		assertEquals(true, uep.getCanDownload()); // can't read but CAN download, which is controlled separately
		
		// Let the user change permissions.
		acl = permissionsManager.getACL(node.getId(), userInfo);
		assertNotNull(acl);
		acl = AuthorizationHelper.addToACL(acl, userInfo.getIndividualGroup(), ACCESS_TYPE.CHANGE_PERMISSIONS);
		acl = permissionsManager.updateACL(acl, adminUser);
		
		uep = authorizationManager.getUserPermissionsForEntity(userInfo,  node.getId());
		assertEquals(false, uep.getCanAddChild());
		assertEquals(true, uep.getCanChangePermissions());
		assertEquals(true, uep.getCanDelete());
		assertEquals(true, uep.getCanEdit());
		assertEquals(true, uep.getCanView());
		assertEquals(true, uep.getCanDownload()); // can't read but CAN download, which is controlled separately
		
		// Let the user change create.
		acl = permissionsManager.getACL(node.getId(), userInfo);
		assertNotNull(acl);
		acl = AuthorizationHelper.addToACL(acl, userInfo.getIndividualGroup(), ACCESS_TYPE.CREATE);
		acl = permissionsManager.updateACL(acl, adminUser);
		
		uep = authorizationManager.getUserPermissionsForEntity(userInfo,  node.getId());
		assertEquals(true, uep.getCanAddChild());
		assertEquals(true, uep.getCanChangePermissions());
		assertEquals(true, uep.getCanDelete());
		assertEquals(true, uep.getCanEdit());
		assertEquals(true, uep.getCanView());
		assertEquals(true, uep.getCanDownload()); // can't read but CAN download, which is controlled separately
	}

}
