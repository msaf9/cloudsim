package coms.project.monitor;

import java.util.HashMap;
import java.util.Map;

/**
 * AccessControl implements a basic role-based access control (RBAC) system:
 * - Maps user IDs to roles (e.g., "BROKER").
 * - Authorizes actions based on role and requested operation.
 */
public class AccessControl {
	private Map<Integer, String> roles = new HashMap<>();

	/**
	 * Registers a user with a specific role.
	 *
	 * @param id   User ID
	 * @param role Role assigned to the user (e.g., "BROKER")
	 */
	public void registerUser(int id, String role) {
		roles.put(id, role);
	}

	/**
	 * Checks if a user is allowed to perform a given action.
	 * - Currently allows only users with role "BROKER" to perform "SUBMIT".
	 *
	 * @param id     User ID
	 * @param action Action to authorize (e.g., "SUBMIT")
	 * @return true if allowed, false otherwise
	 */
	public boolean isAllowed(int id, String action) {
		return "BROKER".equals(roles.get(id)) && "SUBMIT".equals(action);
	}
}
