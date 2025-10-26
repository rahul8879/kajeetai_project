package com.kajeet.sentinel.activation.dao.impl;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import com.kajeet.sentinel.activation.model.CarrierBearerPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.stereotype.Component;

import com.kajeet.sentinel.activation.dao.ActivationDao;
import com.kajeet.sentinel.activation.model.ActivationInventoryInfo;
import com.kajeet.sentinel.activation.model.ActivationTransactionDTO;
import com.kajeet.sentinel.activation.model.ActivationVerizonBusinessPlan;
import com.kajeet.sentinel.config.Constants;
import com.kajeet.sentinel.model.CatalystResult;

import static com.kajeet.sentinel.activation.enumeration.Carriers.KCN;
import static com.kajeet.sentinel.activation.enumeration.Carriers.KPN;

@Component
public class ActivationDaoImpl implements ActivationDao{
	
	private static final Logger log = LoggerFactory.getLogger(ActivationDaoImpl.class);
	private JdbcTemplate jdbcTemplate;
	private NamedParameterJdbcTemplate namedParameterJdbcTemplate;
	private String dbUser;
	private SimpleJdbcCall bulkActivateVerizon;
	private SimpleJdbcCall bulkActivateVerizonPriority;
	private SimpleJdbcCall bulkActivateATT;
	private SimpleJdbcCall bulkActivateATTFirstNet;
	private SimpleJdbcCall bulkActivateATTFirstNetExtendedPrimary;
	private SimpleJdbcCall bulkActivateTmo; //NetCracker
	private SimpleJdbcCall bulkActivateTmoControlCenter;
	private SimpleJdbcCall bulkActivateUSC;
	private SimpleJdbcCall bulkActivateKajeetPrivateLTE;
	private SimpleJdbcCall bulkActivateBellCanada;

	
	public ActivationDaoImpl (JdbcTemplate jdbcTemplate, Constants constants) {
		this.jdbcTemplate = jdbcTemplate;
		this.namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate.getDataSource());
	    this.dbUser = constants.getKjdbDbUser();
	    this.bulkActivateVerizon = new SimpleJdbcCall(jdbcTemplate).withProcedureName("bulk_activate_verizon_kj4_json")
                .withSchemaName(dbUser);
	    this.bulkActivateVerizonPriority = new SimpleJdbcCall(jdbcTemplate).withProcedureName("bulk_activate_verizon_ts_3rdp")
                .withSchemaName(dbUser);
	    this.bulkActivateATT = new SimpleJdbcCall(jdbcTemplate).withProcedureName("bulk_activate_kjatt1_json")
                .withSchemaName(dbUser);
	    this.bulkActivateATTFirstNet = new SimpleJdbcCall(jdbcTemplate).withProcedureName("bulk_activate_attfn_3rdp")
                .withSchemaName(dbUser);
	    this.bulkActivateATTFirstNetExtendedPrimary = new SimpleJdbcCall(jdbcTemplate).withProcedureName("bulk_activate_attfne_3rdp")
                .withSchemaName(dbUser);
	    this.bulkActivateTmo = new SimpleJdbcCall(jdbcTemplate).withProcedureName("bulk_activate_tmo_kj1_json")
                .withSchemaName(dbUser);
	    this.bulkActivateUSC = new SimpleJdbcCall(jdbcTemplate).withProcedureName("bulk_activate_usc_json")
                .withSchemaName(dbUser);
	    this.bulkActivateKajeetPrivateLTE = new SimpleJdbcCall(jdbcTemplate).withProcedureName("bulk_activate_kpw_json")
                .withSchemaName(dbUser);
		this.bulkActivateBellCanada = new SimpleJdbcCall(jdbcTemplate).withProcedureName("bulk_activate_bell_json")
										.withSchemaName(dbUser);
		 this.bulkActivateTmoControlCenter = new SimpleJdbcCall(jdbcTemplate).withProcedureName("bulk_activate_multi_carrier1_json")
	                .withSchemaName(dbUser);
	}

	@Override
	public ActivationInventoryInfo getActivationInventoryInfoByCarrier(String carrier, String businessType) {
		
		MapSqlParameterSource parameters = new MapSqlParameterSource();
		parameters.addValue("carrier", carrier);
		parameters.addValue("businessType", businessType.toUpperCase());

		try {
			String sql = "select SKU,PLAN_ID from self_activation_inventory_combined where carrier=:carrier AND corp_business_type=:businessType";
			
			return namedParameterJdbcTemplate.queryForObject(sql, parameters,
					new BeanPropertyRowMapper<>(ActivationInventoryInfo.class));

		} catch (Exception e) {
			log.error("Could not get activation inventory info for carrier: {}.  Exception: {}", carrier, e);
			return null;
		}
		
	}

	@Override
	public ActivationInventoryInfo getActivationInventoryOfPlteNonRuckus(String carrier) {
		
		MapSqlParameterSource parameters = new MapSqlParameterSource();
		parameters.addValue("carrier", carrier);
		
		if (KCN.getDescription().equalsIgnoreCase(carrier)) {
			ActivationInventoryInfo activationInventoryInfo = new ActivationInventoryInfo();
			activationInventoryInfo.setSku(null);
			activationInventoryInfo.setPlanId("KCNCISCO");
			return activationInventoryInfo;
		}
		if (KPN.getDescription().equalsIgnoreCase(carrier)) {
			ActivationInventoryInfo activationInventoryInfo = new ActivationInventoryInfo();
			activationInventoryInfo.setSku(null);
			activationInventoryInfo.setPlanId("KJPENTE");
			return activationInventoryInfo;
		} else {

			try {
				String sql = "select SKU,PLAN_ID from self_activation_inventory_combined where carrier = :carrier and corp_business_type = 'KPW'";

				return namedParameterJdbcTemplate.queryForObject(sql, parameters,
						new BeanPropertyRowMapper<>(ActivationInventoryInfo.class));

			} catch (Exception e) {
				log.error("Could not get PLTE activation inventory info for carrier: {}.  Exception: ", carrier, e);
				return null;
			}
		}
	}

	@Override
	public ActivationInventoryInfo getActivationInventoryInfoByCarrier3rdParty(String carrier) {
		
		MapSqlParameterSource parameters = new MapSqlParameterSource();
		parameters.addValue("carrier", carrier);
		
		try {
			String sql = "select SKU,\n"
					+ "		PLAN_ID,\n"
					+ "		EAST_IP_POOL,\n"
					+ "		WEST_IP_POOL,\n"
					+ "		EAST_COMMUNICATION_PLAN,\n"
					+ "		WEST_COMMUNICATION_PLAN,\n"
					+ "		SUB_TYPE from SELF_ACTIVATION_INVENTORY_3RD_PARTY where carrier = :carrier ";
			
			return namedParameterJdbcTemplate.queryForObject(sql, parameters, new BeanPropertyRowMapper<>(ActivationInventoryInfo.class));

		} catch (Exception e) {
			log.error("Could not third party activation inventory info for carrier: {}.  Exception: {}", carrier, e);
			return null;
		}
	}

	@Override
	public Long submitVerizonActivationRequest(String activationJson, String corpId, String logUserId,
			String catalystUserId) {

		SqlParameterSource params = setActivationInParams(activationJson, corpId, logUserId, catalystUserId);

		Map<String, Object> output = this.bulkActivateVerizon.execute(params);

		return parseActivationResponse(corpId, output);
		
	}

	@Override
	public Long submitVerizonPriorityActivationRequest(String activationJson, String corpId, String logUserId,
			String catalystUserId) {

		SqlParameterSource params = setActivationInParams(activationJson, corpId, logUserId, catalystUserId);

		Map<String, Object> output = this.bulkActivateVerizonPriority.execute(params);

		return parseActivationResponse(corpId, output);
		
	}

	@Override
	public Long submitTMOActivationRequest(String activationJson, String corpId, String logUserId,
			String catalystUserId) {

		SqlParameterSource params = setActivationInParams(activationJson, corpId, logUserId, catalystUserId);

		Map<String, Object> output = this.bulkActivateTmo.execute(params);

		return parseActivationResponse(corpId, output);
		
	}
	
	

	@Override
	public Long submitTMOControlCenterActivationRequest(String activationJson, String corpId, String logUserId,
			String catalystUserId) {

		SqlParameterSource params = setActivationInParams(activationJson, corpId, logUserId, catalystUserId);

		Map<String, Object> output = this.bulkActivateTmoControlCenter.execute(params);

		return parseActivationResponse(corpId, output);
		
	}
	

	@Override
	public Long submitATTActivationRequest(String activationJson, String corpId, String logUserId,
			String catalystUserId) {

		SqlParameterSource params = setActivationInParams(activationJson, corpId, logUserId, catalystUserId);

		Map<String, Object> output = this.bulkActivateATT.execute(params);

		return parseActivationResponse(corpId, output);
		
	}

	@Override
	public Long submitAttFirstNetActivationRequest(String activationJson, String corpId, String logUserId,
			String catalystUserId) {

		SqlParameterSource params = setActivationInParams(activationJson, corpId, logUserId, catalystUserId);

		Map<String, Object> output = this.bulkActivateATTFirstNet.execute(params);

		return parseActivationResponse(corpId, output);
		
	}

	@Override
	public Long submitAttFirstNetExtendedPrimaryActivationRequest(String activationJson, String corpId,
			String logUserId, String catalystUserId) {

		SqlParameterSource params = setActivationInParams(activationJson, corpId, logUserId, catalystUserId);

		Map<String, Object> output = this.bulkActivateATTFirstNetExtendedPrimary.execute(params);

		return parseActivationResponse(corpId, output);
		
	}

	@Override
	public Long submitUSCActivationRequest(String activationJson, String corpId, String logUserId,
			String catalystUserId) {

		SqlParameterSource params = setActivationInParams(activationJson, corpId, logUserId, catalystUserId);

		Map<String, Object> output = this.bulkActivateUSC.execute(params);

		return parseActivationResponse(corpId, output);
		
	}

	@Override
	public Long submitKNEActivationRequest(String activationJson, String corpId, String logUserId,
			String catalystUserId) {

		SqlParameterSource params = setActivationInParams(activationJson, corpId, logUserId, catalystUserId);

		Map<String, Object> output = this.bulkActivateKajeetPrivateLTE.execute(params);

		return parseActivationResponse(corpId, output);
		
	}

	@Override
	public Long submitBellCanadaActivationRequest(String activationJson, String corpId, String logUserId,
									String catalystUserId) {

		SqlParameterSource params = setActivationInParams(activationJson, corpId, logUserId, catalystUserId);

		Map<String, Object> output = this.bulkActivateBellCanada.execute(params);

		return parseActivationResponse(corpId, output);

	}
	
	private SqlParameterSource setActivationInParams(String activationJson, String corpId, String logUserId,
			String catalystUserId) {
		SqlParameterSource params = new MapSqlParameterSource().addValue("p_json", activationJson)
				.addValue("p_corp_id", corpId).addValue("p_log_user_id", logUserId)
				.addValue("p_catalyst_user_id", catalystUserId);
		return params;
	}
	
	private long parseActivationResponse(String corpId, Map<String, Object> output) {
		Integer resultCode = ((BigDecimal) output.get(CatalystResult.P_RESULT_CODE)).intValue();
		long transactionId = 0;
		if (resultCode != 0) {
			log.error("Error submitting activation request for Corp: {} .Error code: {} .Catalyst Error Description: {}",
											corpId, resultCode, output.get(CatalystResult.P_PROBLEM_DESC));
		} else {
			log.info("Activation submission was successful for corpId: {}, TransactionId: {}" , corpId, output.get("P_SET_TRANSACTION_ID"));
			transactionId = ((BigDecimal) output.get("P_SET_TRANSACTION_ID")).longValue();
		}
		return transactionId;
	}

	@Deprecated
	@Override
	public List<String> getKNECarrierListForActivation() {
		
		try {
			String sql = "select distinct(carrier) from SELF_ACTIVATION_INVENTORY_COMBINED WHERE corp_business_type = 'KPW' order by CARRIER";

			return jdbcTemplate.queryForList(sql, String.class);

		} catch (Exception e) {
			log.error("Could not get carrier list info for KNE.  Exception: {}", e);
			return null;
		}
		
	}

	@Override
	public List<String> getCarrierListForActivation(Boolean isVerizonBI, String businessType, Boolean isEsimEnabled) {
		MapSqlParameterSource parameters = new MapSqlParameterSource();
		parameters.addValue("businessType", businessType.toUpperCase());
		try {
			String sql = "select distinct(carrier) from self_activation_inventory_combined \n where corp_business_type = :businessType ";
					
					if(!isVerizonBI) {
						sql += "AND CARRIER != 'Verizon BI'\n";
					}
					if ( isEsimEnabled) {
						sql += "AND HAS_ESIM_EXPRESS_ENABLED = 'Y'\n";
					}
					
					sql += " order by CARRIER";

			return namedParameterJdbcTemplate.queryForList(sql, parameters, String.class);

		} catch (Exception e) {
			log.error("Could not get carrier list. Exception: {}", e);
			return null;
		}
		
	}

	@Override
	public List<String> getCarrierListForActivationFirstResponder(Boolean isVerizonBI) {
		
		try {
			String sql = "select distinct(carrier) from SELF_ACTIVATION_INVENTORY_3RD_PARTY \n";
					
					if(!isVerizonBI) {
						sql += "where NOT CARRIER = 'Verizon BI'\n";
					}
					
					sql += "order by CARRIER";

			return jdbcTemplate.queryForList(sql,
					String.class);

		} catch (Exception e) {
			log.error("Could not get carrier list for First Responder.  Exception: {}", e);
			return null;
		}
		
	}

	@Deprecated
	@Override
	public List<ActivationTransactionDTO> getActivationTransactionHistory(String corpId, String sortDir,
			String userTimezone) {
		
		MapSqlParameterSource parameters = new MapSqlParameterSource();
		parameters.addValue("corpId", corpId);
		validateUserTimeZone(userTimezone);
		userTimezone = "'" + userTimezone + "'"; // move quotes to the query to spare some RAM?
		
		try {
			String sql = "SELECT  set_transaction_id, line_count, completed_count, failed_count, pending_count, derived_status, \n"
					+ "TO_CHAR(FROM_TZ(cast(DATE_ENTERED as timestamp), substr(entered_timestamp, -6)) AT TIME ZONE \n"
					+ userTimezone
					+ ",'MM/dd/yyyy HH:MI:SS AM') transaction_timestamp FROM " + dbUser + ".kj4_activation_set \n"
					+ "WHERE corp_id IN (SELECT CORP_ID FROM " + dbUser + ".KJ4_CORP_VIEW START WITH CORP_ID = :corpId \n"
					+ "CONNECT BY PRIOR CORP_ID=PARENT_CORP_ID) order by entered_timestamp " + sortDir;
			
			return namedParameterJdbcTemplate.query(sql, parameters, (resultSet, i) -> {
				ActivationTransactionDTO activationTransactionDTO = new ActivationTransactionDTO();
				activationTransactionDTO.setTransactionId(resultSet.getString("set_transaction_id"));
				activationTransactionDTO.setStatus(resultSet.getString("derived_status"));
				activationTransactionDTO.setTransactionStartTimestamp(resultSet.getString("transaction_timestamp"));
				activationTransactionDTO.setTotalLines(resultSet.getInt("line_count"));
				activationTransactionDTO.setSuccessLines(resultSet.getInt("completed_count"));
				activationTransactionDTO.setFailedLines(resultSet.getInt("failed_count"));
				activationTransactionDTO.setPendingLines(resultSet.getInt("pending_count"));
				return activationTransactionDTO;
				
			});
		} catch (Exception e) {
			log.error("Could not get activation transaction history for corp: {}.  Exception: {}", corpId, e);
			return Collections.emptyList();
		}

	}
	
	@Override
	public List<ActivationTransactionDTO> getAllActivationHistoryDetails(String corpId, String sortDir,
			String userTimezone) {
		
		MapSqlParameterSource parameters = new MapSqlParameterSource();
		parameters.addValue("corpId", corpId);
		validateUserTimeZone(userTimezone);
		userTimezone = "'" + userTimezone + "'"; // move quotes to the query to spare some RAM?
		
		//query to get entire activation history with individual line level activation details for export only
		
		try {
			String sql = "SELECT asv.set_transaction_id, asv.line_count, asv.completed_count, asv.failed_count, asv.derived_status as transaction_status, \n"
					+ " TO_CHAR(FROM_TZ(cast(asv.DATE_ENTERED as timestamp), substr(asv.entered_timestamp, -6)) AT TIME ZONE \n"
					+ userTimezone
					+ ", 'MM/dd/yyyy HH:MI:SS AM') transaction_timestamp, \n"
					+ " asd.carrier, asd.device_group, cv.corp_description, asd.filter_group, asd.service_zip_code, \n"
					+ "asd.imei, asd.iccid, asd.mdn, nvl(kjserv5.nickname,'N/A') as nickname, asd.ip, asd.derived_status as line_activation_status \n"
					+ "			FROM " + dbUser + ".kj4_activation_set_view2 asv\n"
					+ "			JOIN " + dbUser + ".kj4_activation_set_details asd ON asv.set_transaction_id = asd.set_transaction_id \n"
					+ "			LEFT JOIN " + dbUser + ".KJ4_SERVICE_VIEW5 kjserv5 ON kjserv5.ACTIV_NO = asd.ACTIV_NO AND kjserv5.CUST_NO = asd.CUST_NO,\n"
					+ "		    (SELECT CORP_ID, CORP_DESCRIPTION\n"
					+ "			FROM " + dbUser + ".KJ4_CORP_VIEW\n"
					+ "			START WITH CORP_ID = :corpId\n"
					+ "		  	CONNECT BY PRIOR CORP_ID=PARENT_CORP_ID) cv\n"
					+ "			WHERE asd.device_group = cv.corp_id \n"
					+ "			ORDER BY asv.entered_timestamp " + sortDir;
			
			return namedParameterJdbcTemplate.query(sql, parameters, (resultSet, i) -> {
				ActivationTransactionDTO activationTransactionDTO = new ActivationTransactionDTO();
				activationTransactionDTO.setTransactionId(resultSet.getString("set_transaction_id"));
				activationTransactionDTO.setTotalLines(resultSet.getInt("line_count"));
				activationTransactionDTO.setSuccessLines(resultSet.getInt("completed_count"));
				activationTransactionDTO.setFailedLines(resultSet.getInt("failed_count"));
				activationTransactionDTO.setStatus(resultSet.getString("transaction_status"));
				activationTransactionDTO.setTransactionStartTimestamp(resultSet.getString("transaction_timestamp"));
				activationTransactionDTO.setCarrier(resultSet.getString("carrier"));
				activationTransactionDTO.setCorpId(resultSet.getString("device_group"));
				activationTransactionDTO.setCorpDescription(resultSet.getString("corp_description"));
				activationTransactionDTO.setFilterGroup(resultSet.getString("filter_group"));
				activationTransactionDTO.setZipCode(resultSet.getString("service_zip_code"));
				activationTransactionDTO.setImei(resultSet.getString("imei"));
				activationTransactionDTO.setIccid(resultSet.getString("iccid"));
				activationTransactionDTO.setMdn(resultSet.getString("mdn"));
				activationTransactionDTO.setNickname(resultSet.getString("nickname"));
				activationTransactionDTO.setIp(resultSet.getString("ip"));
				activationTransactionDTO.setLineActivationStatus(resultSet.getString("line_activation_status"));
				
				return activationTransactionDTO;
				
			});
		} catch (Exception e) {
			log.error("Could not get activation transaction history for corp: {}.  Exception: {}", corpId, e);
			return Collections.emptyList();
		}

	}
	
	@Override
	public List<ActivationTransactionDTO> getRecentActivationTransactions(String corpId, Integer offset, Integer limit,
			String sortDir, String userTimezone) {

		MapSqlParameterSource parameters = new MapSqlParameterSource();
		parameters.addValue("corpId", corpId);
		parameters.addValue("offset", offset);
		parameters.addValue("limit", limit);
		validateUserTimeZone(userTimezone);
		userTimezone = "'" + userTimezone + "'"; // move quotes to the query to spare some RAM?

		// query will fetch last 180 days of activation transactions with pagination
		try {
			String sql = "SELECT  set_transaction_id, line_count, completed_count, failed_count, pending_count, derived_status, \n"
					+ "TO_CHAR(FROM_TZ(cast(DATE_ENTERED as timestamp), substr(entered_timestamp, -6)) AT TIME ZONE \n"
					+ userTimezone + ",'MM/dd/yyyy HH:MI:SS AM') transaction_timestamp FROM " + dbUser
					+ ".kj4_activation_set_view2 \n" + "WHERE corp_id IN (SELECT CORP_ID FROM " + dbUser
					+ ".KJ4_CORP_VIEW START WITH CORP_ID = :corpId \n" + "CONNECT BY PRIOR CORP_ID=PARENT_CORP_ID) \n"
					+ "AND date_entered >= SYSDATE - 180 \n"
					+ "ORDER BY entered_timestamp " + sortDir
					+ "\n OFFSET :offset ROWS FETCH FIRST :limit ROWS ONLY";

			return namedParameterJdbcTemplate.query(sql, parameters, (resultSet, i) -> {
				ActivationTransactionDTO activationTransactionDTO = new ActivationTransactionDTO();
				activationTransactionDTO.setTransactionId(resultSet.getString("set_transaction_id"));
				activationTransactionDTO.setStatus(resultSet.getString("derived_status"));
				activationTransactionDTO.setTransactionStartTimestamp(resultSet.getString("transaction_timestamp"));
				activationTransactionDTO.setTotalLines(resultSet.getInt("line_count"));
				activationTransactionDTO.setSuccessLines(resultSet.getInt("completed_count"));
				activationTransactionDTO.setFailedLines(resultSet.getInt("failed_count"));
				activationTransactionDTO.setPendingLines(resultSet.getInt("pending_count"));
				return activationTransactionDTO;

			});
		} catch (Exception e) {
			log.error("Could not get recent activation transactions for corp: {}.  Exception: {}", corpId, e);
			return Collections.emptyList();
		}

	}
	
	@Override
	public List<ActivationTransactionDTO> getActivationTransactionDetails(String transactionId, String corpId,
			String userTimezone) {
		
		MapSqlParameterSource parameters = new MapSqlParameterSource();
		parameters.addValue("corpId", corpId);
		validateUserTimeZone(userTimezone);
		userTimezone = "'" + userTimezone + "'";
		parameters.addValue("transactionId", transactionId);
		
		try {
			String sql = "SELECT set_transaction_id, device_group, cv.corp_description, service_zip_code, filter_group,\n"
					+ "			carrier, TO_CHAR(FROM_TZ(cast(DATE_ENTERED as timestamp), substr(entered_timestamp, -6)) AT TIME ZONE \n"
					+ userTimezone
					+ ", 'MM/dd/yyyy HH:MI:SS AM') transaction_timestamp, \n"
					+ "			act.imei, act.iccid, derived_status, act.mdn, ip, nvl(nickname,'N/A') as nickname\n"
					+ "			FROM " + dbUser + ".kj4_activation_set_details act\n"
					+ "			LEFT JOIN " + dbUser + ".KJ4_SERVICE_VIEW5 kjserv5 ON kjserv5.ACTIV_NO = act.ACTIV_NO AND kjserv5.CUST_NO = act.CUST_NO,\n"
					+ "		    (SELECT CORP_ID, CORP_DESCRIPTION\n"
					+ "			FROM " + dbUser + ".KJ4_CORP_VIEW\n"
					+ "			START WITH CORP_ID = :corpId\n"
					+ "		  	CONNECT BY PRIOR CORP_ID=PARENT_CORP_ID) cv\n"
					+ "			WHERE act.device_group = cv.corp_id AND act.set_transaction_id = :transactionId";
			

			return namedParameterJdbcTemplate.query(sql, parameters, (resultSet, i) -> {
					ActivationTransactionDTO activationTransactionDTO = new ActivationTransactionDTO();
					activationTransactionDTO.setTransactionId(resultSet.getString("set_transaction_id"));
					activationTransactionDTO.setCorpId(resultSet.getString("device_group"));
					activationTransactionDTO.setCorpDescription(resultSet.getString("corp_description"));
					activationTransactionDTO.setZipCode(resultSet.getString("service_zip_code"));
					activationTransactionDTO.setFilterGroup(resultSet.getString("filter_group"));
					activationTransactionDTO.setCarrier(resultSet.getString("carrier"));
					activationTransactionDTO.setTransactionStartTimestamp(resultSet.getString("transaction_timestamp"));
					activationTransactionDTO.setImei(resultSet.getString("imei"));
					activationTransactionDTO.setIccid(resultSet.getString("iccid"));
					activationTransactionDTO.setStatus(resultSet.getString("derived_status"));
					activationTransactionDTO.setMdn(resultSet.getString("mdn"));
					activationTransactionDTO.setIp(resultSet.getString("ip"));
					activationTransactionDTO.setNickname(resultSet.getString("nickname"));
					return activationTransactionDTO;
			});
		} catch (Exception e) {
			log.error("Could not get activation transaction details for corp: {} and transactionId: {}.  Exception: {}", corpId, transactionId, e);
			return Collections.emptyList();
		}
		
	}
	
    private void validateUserTimeZone(String userTimezone){
        if (!Arrays.asList(TimeZone.getAvailableIDs()).contains(userTimezone)) {
        	log.error("Invalid userTimezone: {}", userTimezone);
        	//Set a default timezone 
        	userTimezone = "US/Eastern"; // not used. Bug?
        }
    }

	@Override
	public String getCarrierAccountId(String corpId, String carrier) {
		
		MapSqlParameterSource parameters = new MapSqlParameterSource();
		parameters.addValue("corpId", corpId);
		parameters.addValue("carrier", carrier);
		
		try {
			String sql = "SELECT carrier_account_id FROM corp_carrier_account WHERE carrier = :carrier AND corp_id = :corpId ";
			
			return namedParameterJdbcTemplate.queryForObject(sql, parameters,
					String.class);

		} catch (Exception e) {
			log.error("Could not get accountId for carrier: {} and corp: {}.  Exception: {}", carrier, corpId, e);
			return null;
		}
		
	}

	@Override
	public List<CarrierBearerPath> getCarrierBearerPaths(String businessType) {
		MapSqlParameterSource parameters = new MapSqlParameterSource();
		parameters.addValue("businessType", businessType);
		try {
			String sql = "SELECT carrier_friendly_name as carrier_name, bearer_path FROM " + dbUser + ".kj4_carrier_business_types_view1\n " +
					"WHERE business_type = :businessType";

			return namedParameterJdbcTemplate.query(sql, parameters,
					new BeanPropertyRowMapper<>(CarrierBearerPath.class));

		} catch (Exception e) {
			log.error("Could not get carrier bearer paths for business type: {}", businessType, e);
			return Collections.emptyList();
		}
	}

	@Override
	public Integer getRecentActivationHistoryTotalCount(String corpId) {
		MapSqlParameterSource parameters = new MapSqlParameterSource();
		parameters.addValue("corpId", corpId);
		try {
			String sql = "SELECT COUNT(*) FROM " + dbUser + ".KJ4_ACTIVATION_SET_VIEW2 \n"
					+ "WHERE CORP_ID IN (SELECT CORP_ID FROM " + dbUser + ".KJ4_CORP_VIEW START WITH CORP_ID = :corpId \n"
					+ "CONNECT BY PRIOR CORP_ID=PARENT_CORP_ID) and date_entered >= sysdate - 180";

			return namedParameterJdbcTemplate.queryForObject(sql, parameters, Integer.class);

		} catch (Exception e) {
			log.error("Could not last 180 days of activation history count for corpId: {}", corpId, e);
			return 0;
		}
	}

	@Override
	public List<ActivationVerizonBusinessPlan> getBusinessInternetPlans() {

		List<ActivationVerizonBusinessPlan> activationVerizonBusinessPlanList = null;
		try {
			String sql = "SELECT PLAN_ID,"
					+ "PLAN_DESC,"
                    + "FRIENDLY_NAME,"
					+ "WH_PLAN_ID,"
					+ "CARRIER  FROM  " + dbUser + ".KJ4_VERIZON_TS_3RDP_PLANS_VIEW kvtpv";
				activationVerizonBusinessPlanList = namedParameterJdbcTemplate.query(sql, (resultSet, i) -> {
				ActivationVerizonBusinessPlan activationVerizonBusinessPlan = new ActivationVerizonBusinessPlan();
				activationVerizonBusinessPlan.setPlanDescriptionFull(resultSet.getString("PLAN_DESC"));
                activationVerizonBusinessPlan.setPlanDescription(resultSet.getString("FRIENDLY_NAME"));
				activationVerizonBusinessPlan.setPlanId(resultSet.getString("PLAN_ID"));
				activationVerizonBusinessPlan.setWhPlanId(resultSet.getString("WH_PLAN_ID"));
				activationVerizonBusinessPlan.setCarrier(resultSet.getString("CARRIER"));
				return activationVerizonBusinessPlan;

			});
			return activationVerizonBusinessPlanList;

		} catch (Exception e) {
			log.error("Could not get Business Plans. Exception: {}", e);
			return activationVerizonBusinessPlanList;
		}
	}

}
