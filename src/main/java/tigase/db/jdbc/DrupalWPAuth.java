/*
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2007 "Artur Hefczyc" <artur.hefczyc@tigase.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 *
 * $Rev$
 * Last modified by $Author$
 * $Date$
 */

package tigase.db.jdbc;

//~--- non-JDK imports --------------------------------------------------------

import tigase.auth.SaslPLAIN;

import tigase.db.AuthorizationException;
import tigase.db.DBInitException;
import tigase.db.TigaseDBException;
import tigase.db.UserAuthRepository;
import tigase.db.UserExistsException;
import tigase.db.UserNotFoundException;

import tigase.util.Algorithms;
import tigase.util.Base64;

import tigase.xmpp.BareJID;

import static tigase.db.UserAuthRepository.*;

//~--- JDK imports ------------------------------------------------------------

import java.io.IOException;

import java.math.BigDecimal;

import java.security.NoSuchAlgorithmException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.AuthorizeCallback;
import javax.security.sasl.RealmCallback;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

//~--- classes ----------------------------------------------------------------

/**
 * Describe class DrupalWPAuth here.
 *
 *
 * Created: Sat Nov 11 22:22:04 2006
 *
 * @author <a href="mailto:artur.hefczyc@tigase.org">Artur Hefczyc</a>
 * @version $Rev$
 */
public class DrupalWPAuth implements UserAuthRepository {

	/**
	 * Private logger for class instancess.
	 */
	private static final Logger log = Logger.getLogger(DrupalWPAuth.class.getName());
	private static final String[] non_sasl_mechs = { "password" };
	private static final String[] sasl_mechs = { "PLAIN" };

	/** Field description */
	public static final String DRUPAL_USERS_TBL = "users";

	/** Field description */
	public static final String DRUPAL_NAME_FLD = "name";

	/** Field description */
	public static final String DRUPAL_PASS_FLD = "pass";

	/** Field description */
	public static final String DRUPAL_STATUS_FLD = "status";

	/** Field description */
	public static final int DRUPAL_OK_STATUS_VAL = 1;

	/** Field description */
	public static final String WP_USERS_TBL = "wp_users";

	/** Field description */
	public static final String WP_NAME_FLD = "user_login";

	/** Field description */
	public static final String WP_PASS_FLD = "user_pass";

	/** Field description */
	public static final String WP_STATUS_FLD = "status";

	/** Field description */
	public static final int WP_OK_STATUS_VAL = 0;

	//~--- fields ---------------------------------------------------------------

	/**
	 * Database active connection.
	 */
	private Connection conn = null;

	/**
	 * Prepared statement for testing whether database connection is still
	 * working. If not connection to database is recreated.
	 */
	private PreparedStatement conn_valid_st = null;

	/**
	 * Connection validation helper.
	 */
	private long connectionValidateInterval = 1000 * 60;

	/**
	 * Database connection string.
	 */
	private String db_conn = null;

	/**
	 * Connection validation helper.
	 */
	private long lastConnectionValidated = 0;

	// private PreparedStatement max_uid_st = null;
	private PreparedStatement pass_st = null;
	private PreparedStatement status_st = null;
	private PreparedStatement update_last_login_st = null;
	private PreparedStatement update_online_status = null;
	private PreparedStatement user_add_st = null;
	private String name_fld = DRUPAL_NAME_FLD;
	private String users_tbl = DRUPAL_USERS_TBL;
	private int status_val = DRUPAL_OK_STATUS_VAL;
	private String status_fld = DRUPAL_STATUS_FLD;
	private String pass_fld = DRUPAL_PASS_FLD;
	private boolean online_status = false;
	private boolean last_login = true;

	//~--- methods --------------------------------------------------------------

	/**
	 * Describe <code>addUser</code> method here.
	 *
	 * @param user a <code>String</code> value
	 * @param password a <code>String</code> value
	 * @exception UserExistsException if an error occurs
	 * @exception TigaseDBException if an error occurs
	 */
	@Override
	public void addUser(BareJID user, final String password)
			throws UserExistsException, TigaseDBException {
		try {
			checkConnection();

			synchronized (user_add_st) {

				// long uid = getMaxUID() + 1;
				// user_add_st.setLong(1, uid);
				user_add_st.setString(1, user.getLocalpart());
				user_add_st.setString(2, Algorithms.hexDigest("", password, "MD5"));
				user_add_st.executeUpdate();
			}
		} catch (NoSuchAlgorithmException e) {
			throw new TigaseDBException("Password encoding algorithm is not supported.", e);
		} catch (SQLException e) {
			throw new UserExistsException("Error while adding user to repository, user exists?", e);
		}
	}

	/**
	 * Describe <code>digestAuth</code> method here.
	 *
	 * @param user a <code>String</code> value
	 * @param digest a <code>String</code> value
	 * @param id a <code>String</code> value
	 * @param alg a <code>String</code> value
	 * @return a <code>boolean</code> value
	 * @exception UserNotFoundException if an error occurs
	 * @exception TigaseDBException if an error occurs
	 * @exception AuthorizationException if an error occurs
	 */
	@Override
	public boolean digestAuth(BareJID user, final String digest, final String id,
			final String alg)
			throws UserNotFoundException, TigaseDBException, AuthorizationException {
		throw new AuthorizationException("Not supported.");
	}

	//~--- get methods ----------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	@Override
	public String getResourceUri() {
		return db_conn;
	}

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	@Override
	public long getUsersCount() {
		return -1;
	}

	/**
	 * Method description
	 *
	 *
	 * @param domain
	 *
	 * @return
	 */
	@Override
	public long getUsersCount(String domain) {
		return -1;
	}

	//~--- methods --------------------------------------------------------------

	/**
	 * Describe <code>initRepository</code> method here.
	 *
	 * @param connection_str a <code>String</code> value
	 * @param params
	 * @exception DBInitException if an error occurs
	 */
	@Override
	public void initRepository(final String connection_str, Map<String, String> params)
			throws DBInitException {
		db_conn = connection_str;

		if (db_conn.contains("online_status=true")) {
			online_status = true;
		}

		if (db_conn.contains("wp_mode=true")) {
			online_status = false;
			last_login = false;
			name_fld = WP_NAME_FLD;
			users_tbl = WP_USERS_TBL;
			status_val = WP_OK_STATUS_VAL;
			status_fld = WP_STATUS_FLD;
			pass_fld = WP_PASS_FLD;
			log.info("Initializing Wordpress repository: " + db_conn);
		} else {
			log.info("Initializing Drupal repository: " + db_conn);
		}

		try {
			initRepo();
		} catch (SQLException e) {
			conn = null;

			throw new DBInitException("Problem initializing jdbc connection: " + db_conn, e);
		}

		try {
			if (online_status) {
				Statement stmt = conn.createStatement();

				stmt.executeUpdate("update users set online_status = 0;");
				stmt.close();
				stmt = null;
			}
		} catch (SQLException e) {
			if (e.getMessage().contains("'online_status'")) {
				try {
					Statement stmt = conn.createStatement();

					stmt.executeUpdate("alter table users add online_status int default 0;");
					stmt.close();
					stmt = null;
				} catch (SQLException ex) {
					conn = null;

					throw new DBInitException("Problem initializing jdbc connection: " + db_conn, ex);
				}
			} else {
				conn = null;

				throw new DBInitException("Problem initializing jdbc connection: " + db_conn, e);
			}
		}
	}

	/**
	 * Method description
	 *
	 *
	 * @param user
	 *
	 * @throws TigaseDBException
	 * @throws UserNotFoundException
	 */
	@Override
	public void logout(BareJID user) throws UserNotFoundException, TigaseDBException {
		try {
			checkConnection();
			updateOnlineStatus(user, -1);
		} catch (SQLException e) {
			throw new TigaseDBException("Problem accessing repository.", e);
		}    // end of catch
	}

	/**
	 * Describe <code>otherAuth</code> method here.
	 *
	 * @param props a <code>Map</code> value
	 * @return a <code>boolean</code> value
	 * @exception UserNotFoundException if an error occurs
	 * @exception TigaseDBException if an error occurs
	 * @exception AuthorizationException if an error occurs
	 */
	@Override
	public boolean otherAuth(final Map<String, Object> props)
			throws UserNotFoundException, TigaseDBException, AuthorizationException {
		String proto = (String) props.get(PROTOCOL_KEY);

		if (proto.equals(PROTOCOL_VAL_SASL)) {
			String mech = (String) props.get(MACHANISM_KEY);

			try {
				if (mech.equals("PLAIN")) {
					boolean login_ok = saslAuth(props);

					if (login_ok) {
						BareJID user = (BareJID) props.get(USER_ID_KEY);

						updateLastLogin(user);
						updateOnlineStatus(user, 1);

						if (log.isLoggable(Level.FINEST)) {
							log.finest("User authenticated: " + user);
						}
					} else {
						if (log.isLoggable(Level.FINEST)) {
							log.finest("User NOT authenticated");
						}
					}

					return login_ok;
				}    // end of if (mech.equals("PLAIN"))
			} catch (Exception e) {
				if (log.isLoggable(Level.FINEST)) {
					log.log(Level.FINEST, "OTHER authentication error: ", e);
				}

				throw new AuthorizationException("Sasl exception.", e);
			}      // end of try-catch

			throw new AuthorizationException("Mechanism is not supported: " + mech);
		}        // end of if (proto.equals(PROTOCOL_VAL_SASL))

		throw new AuthorizationException("Protocol is not supported: " + proto);
	}

	/**
	 * Describe <code>plainAuth</code> method here.
	 *
	 * @param user a <code>String</code> value
	 * @param password a <code>String</code> value
	 * @return a <code>boolean</code> value
	 *
	 * @throws AuthorizationException
	 * @exception UserNotFoundException if an error occurs
	 * @exception TigaseDBException if an error occurs
	 */
	@Override
	public boolean plainAuth(BareJID user, final String password)
			throws UserNotFoundException, TigaseDBException, AuthorizationException {
		try {
			checkConnection();

			if ( !isActive(user)) {
				throw new AuthorizationException("User account has been blocked.");
			}    // end of if (!isActive(user))

			String enc_passwd = Algorithms.hexDigest("", password, "MD5");
			String db_password = getPassword(user);
			boolean login_ok = db_password.equals(enc_passwd);

			if (login_ok) {
				updateLastLogin(user);
				updateOnlineStatus(user, 1);

				if (log.isLoggable(Level.FINEST)) {
					log.finest("User authenticated: " + user);
				}
			} else {
				if (log.isLoggable(Level.FINEST)) {
					log.finest("User NOT authenticated: " + user);
				}
			}

			return login_ok;
		} catch (NoSuchAlgorithmException e) {
			throw new AuthorizationException("Password encoding algorithm is not supported.", e);
		} catch (SQLException e) {
			throw new TigaseDBException("Problem accessing repository.", e);
		}      // end of catch
	}

	// Implementation of tigase.db.UserAuthRepository

	/**
	 * Describe <code>queryAuth</code> method here.
	 *
	 * @param authProps a <code>Map</code> value
	 */
	@Override
	public void queryAuth(final Map<String, Object> authProps) {
		String protocol = (String) authProps.get(PROTOCOL_KEY);

		if (protocol.equals(PROTOCOL_VAL_NONSASL)) {
			authProps.put(RESULT_KEY, non_sasl_mechs);
		}    // end of if (protocol.equals(PROTOCOL_VAL_NONSASL))

		if (protocol.equals(PROTOCOL_VAL_SASL)) {
			authProps.put(RESULT_KEY, sasl_mechs);
		}    // end of if (protocol.equals(PROTOCOL_VAL_NONSASL))
	}

	/**
	 * Describe <code>removeUser</code> method here.
	 *
	 * @param user a <code>String</code> value
	 * @exception UserNotFoundException if an error occurs
	 * @exception TigaseDBException if an error occurs
	 */
	@Override
	public void removeUser(BareJID user) throws UserNotFoundException, TigaseDBException {
		throw new TigaseDBException("Removing user is not supported.");
	}

	/**
	 * Describe <code>updatePassword</code> method here.
	 *
	 * @param user a <code>String</code> value
	 * @param password a <code>String</code> value
	 * @exception TigaseDBException if an error occurs
	 * @throws UserNotFoundException
	 */
	@Override
	public void updatePassword(BareJID user, final String password)
			throws UserNotFoundException, TigaseDBException {
		throw new TigaseDBException("Updatin user password is not supported.");
	}

	/**
	 * <code>checkConnection</code> method checks database connection before any
	 * query. For some database servers (or JDBC drivers) it happens the connection
	 * is dropped if not in use for a long time or after certain timeout passes.
	 * This method allows us to detect the problem and reinitialize database
	 * connection.
	 *
	 * @return a <code>boolean</code> value if the database connection is working.
	 * @exception SQLException if an error occurs on database query.
	 */
	private boolean checkConnection() throws SQLException {
		try {
			synchronized (conn_valid_st) {
				long tmp = System.currentTimeMillis();

				if ((tmp - lastConnectionValidated) >= connectionValidateInterval) {
					conn_valid_st.executeQuery();
					lastConnectionValidated = tmp;
				}    // end of if ()
			}
		} catch (Exception e) {
			initRepo();
		}        // end of try-catch

		return true;
	}

	//~--- get methods ----------------------------------------------------------

//private long getMaxUID() throws SQLException {
//  ResultSet rs = null;
//
//  try {
//    synchronized (max_uid_st) {
//      rs = max_uid_st.executeQuery();
//
//      if (rs.next()) {
//        BigDecimal max_uid = rs.getBigDecimal(1);
//
//        // System.out.println("MAX UID = " + max_uid.longValue());
//        return max_uid.longValue();
//      } else {
//
//        // System.out.println("MAX UID = -1!!!!");
//        return -1;
//      }    // end of else
//    }
//  } finally {
//    release(null, rs);
//  }
//}
	private String getPassword(BareJID user) throws SQLException, UserNotFoundException {
		ResultSet rs = null;

		try {
			checkConnection();

			synchronized (pass_st) {
				pass_st.setString(1, user.getLocalpart());
				rs = pass_st.executeQuery();

				if (rs.next()) {
					return rs.getString(1);
				} else {
					throw new UserNotFoundException("User does not exist: " + user);
				}    // end of if (isnext) else
			}
		} finally {
			release(null, rs);
		}
	}

	//~--- methods --------------------------------------------------------------

	/**
	 * <code>initPreparedStatements</code> method initializes internal
	 * database connection variables such as prepared statements.
	 *
	 * @exception SQLException if an error occurs on database query.
	 */
	private void initPreparedStatements() throws SQLException {
		String query = "select " + pass_fld + " from " + users_tbl + " where " + name_fld + " = ?";

		pass_st = conn.prepareStatement(query);
		query = "select " + status_fld + " from " + users_tbl + " where " + name_fld + " = ?";
		status_st = conn.prepareStatement(query);
		query = "insert into " + users_tbl + " (" + name_fld + ", " + pass_fld + ", " + status_fld
				+ ")" + " values (?, ?, " + status_val + ");";
		user_add_st = conn.prepareStatement(query);

//  query = "select max(uid) from " + users_tbl;
//  max_uid_st = conn.prepareStatement(query);
		query = "select 1;";
		conn_valid_st = conn.prepareStatement(query);
		query = "update " + users_tbl + " set access=?, login=? where " + name_fld + " = ?";
		update_last_login_st = conn.prepareStatement(query);
		query = "update " + users_tbl + " set online_status=online_status+? where " + name_fld
				+ " = ?";
		update_online_status = conn.prepareStatement(query);
	}

	/**
	 * <code>initRepo</code> method initializes database connection
	 * and data repository.
	 *
	 * @exception SQLException if an error occurs on database query.
	 */
	private void initRepo() throws SQLException {
		synchronized (db_conn) {
			conn = DriverManager.getConnection(db_conn);
			initPreparedStatements();
		}
	}

	//~--- get methods ----------------------------------------------------------

	private boolean isActive(BareJID user) throws SQLException, UserNotFoundException {
		ResultSet rs = null;

		try {
			synchronized (status_st) {
				status_st.setString(1, user.getLocalpart());
				rs = status_st.executeQuery();

				if (rs.next()) {
					return (rs.getInt(1) == status_val);
				} else {
					throw new UserNotFoundException("User does not exist: " + user);
				}    // end of if (isnext) else
			}
		} finally {
			release(null, rs);
		}
	}

	//~--- methods --------------------------------------------------------------

	private void release(Statement stmt, ResultSet rs) {
		if (rs != null) {
			try {
				rs.close();
			} catch (SQLException sqlEx) {}
		}

		if (stmt != null) {
			try {
				stmt.close();
			} catch (SQLException sqlEx) {}
		}
	}

	private boolean saslAuth(final Map<String, Object> props) throws AuthorizationException {
		try {
			SaslServer ss = (SaslServer) props.get("SaslServer");

			if (ss == null) {
				Map<String, String> sasl_props = new TreeMap<String, String>();

				sasl_props.put(Sasl.QOP, "auth");
				sasl_props.put(SaslPLAIN.ENCRYPTION_KEY, SaslPLAIN.ENCRYPTION_MD5);
				ss = Sasl.createSaslServer((String) props.get(MACHANISM_KEY), "xmpp",
						(String) props.get(SERVER_NAME_KEY), sasl_props, new SaslCallbackHandler(props));
				props.put("SaslServer", ss);
			}    // end of if (ss == null)

			String data_str = (String) props.get(DATA_KEY);
			byte[] in_data = ((data_str != null) ? Base64.decode(data_str) : new byte[0]);
			byte[] challenge = ss.evaluateResponse(in_data);

			if (log.isLoggable(Level.FINEST)) {
				log.finest("challenge: " + ((challenge != null) ? new String(challenge) : "null"));
			}

			String challenge_str = (((challenge != null) && (challenge.length > 0))
				? Base64.encode(challenge) : null);

			props.put(RESULT_KEY, challenge_str);

			if (ss.isComplete()) {
				return true;
			} else {
				return false;
			}    // end of if (ss.isComplete()) else
		} catch (SaslException e) {
			if (log.isLoggable(Level.FINEST)) {
				log.log(Level.FINEST, "SASL authentication error: ", e);
			}

			throw new AuthorizationException("Sasl exception.", e);
		} catch (Exception e) {
			if (log.isLoggable(Level.FINEST)) {
				log.log(Level.FINEST, "SASL authentication error: ", e);
			}

			throw new AuthorizationException("Sasl exception.", e);
		}      // end of try-catch
	}

	private void updateLastLogin(BareJID user) throws TigaseDBException {
		if (last_login) {
			try {
				synchronized (update_last_login_st) {
					BigDecimal bd = new BigDecimal((System.currentTimeMillis() / 1000));

					update_last_login_st.setBigDecimal(1, bd);
					update_last_login_st.setBigDecimal(2, bd);
					update_last_login_st.setString(3, user.getLocalpart());
					update_last_login_st.executeUpdate();
				}
			} catch (SQLException e) {
				throw new TigaseDBException("Error accessing repository.", e);
			}    // end of try-catch
		}
	}

	private void updateOnlineStatus(BareJID user, int status) throws TigaseDBException {
		if (online_status) {
			try {
				synchronized (update_online_status) {
					update_online_status.setInt(1, status);
					update_online_status.setString(2, user.getLocalpart());
					update_online_status.executeUpdate();
				}
			} catch (SQLException e) {
				throw new TigaseDBException("Error accessing repository.", e);
			}    // end of try-catch
		}
	}

	//~--- inner classes --------------------------------------------------------

	private class SaslCallbackHandler implements CallbackHandler {
		private Map<String, Object> options = null;

		//~--- constructors -------------------------------------------------------

		private SaslCallbackHandler(final Map<String, Object> options) {
			this.options = options;
		}

		//~--- methods ------------------------------------------------------------

		// Implementation of javax.security.auth.callback.CallbackHandler

		/**
		 * Describe <code>handle</code> method here.
		 *
		 * @param callbacks a <code>Callback[]</code> value
		 * @exception IOException if an error occurs
		 * @exception UnsupportedCallbackException if an error occurs
		 */
		@Override
		public void handle(final Callback[] callbacks)
				throws IOException, UnsupportedCallbackException {
			BareJID jid = null;

			for (int i = 0; i < callbacks.length; i++) {
				if (log.isLoggable(Level.FINEST)) {
					log.finest("Callback: " + callbacks[i].getClass().getSimpleName());
				}

				if (callbacks[i] instanceof RealmCallback) {
					RealmCallback rc = (RealmCallback) callbacks[i];
					String realm = (String) options.get(REALM_KEY);

					if (realm != null) {
						rc.setText(realm);
					}        // end of if (realm == null)

					if (log.isLoggable(Level.FINEST)) {
						log.finest("RealmCallback: " + realm);
					}
				} else {
					if (callbacks[i] instanceof NameCallback) {
						NameCallback nc = (NameCallback) callbacks[i];
						String user_name = nc.getName();

						if (user_name == null) {
							user_name = nc.getDefaultName();
						}      // end of if (name == null)

						jid = BareJID.bareJIDInstanceNS(user_name, (String) options.get(REALM_KEY));
						options.put(USER_ID_KEY, jid);

						if (log.isLoggable(Level.FINEST)) {
							log.finest("NameCallback: " + user_name);
						}
					} else {
						if (callbacks[i] instanceof PasswordCallback) {
							PasswordCallback pc = (PasswordCallback) callbacks[i];

							try {
								String passwd = getPassword(jid);

								pc.setPassword(passwd.toCharArray());

								if (log.isLoggable(Level.FINEST)) {
									log.finest("PasswordCallback: " + passwd);
								}
							} catch (Exception e) {
								throw new IOException("Password retrieving problem.", e);
							}    // end of try-catch
						} else {
							if (callbacks[i] instanceof AuthorizeCallback) {
								AuthorizeCallback authCallback = ((AuthorizeCallback) callbacks[i]);
								String authenId = authCallback.getAuthenticationID();
								String authorId = authCallback.getAuthorizationID();

								if (log.isLoggable(Level.FINEST)) {
									log.finest("AuthorizeCallback: authenId: " + authenId);
									log.finest("AuthorizeCallback: authorId: " + authorId);
								}

								// if (authenId.equals(authorId)) {
								authCallback.setAuthorized(true);

								// }    // end of if (authenId.equals(authorId))
							} else {
								throw new UnsupportedCallbackException(callbacks[i], "Unrecognized Callback");
							}
						}
					}
				}
			}
		}
	}
}    // DrupalWPAuth


//~ Formatted in Sun Code Convention


//~ Formatted by Jindent --- http://www.jindent.com