package com.kajeet.sentinel.activation.service.impl;

import static com.kajeet.sentinel.util.PhoneUtil.validateGenericIccid;
import static com.kajeet.sentinel.util.PhoneUtil.validateImei;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.kajeet.sentinel.account.settings.service.ProvisioningGatewayService;
import com.kajeet.sentinel.activation.enumeration.ActivationLocationEnum;
import com.kajeet.sentinel.activation.enumeration.Carriers;
import com.kajeet.sentinel.activation.model.*;
import com.kajeet.sentinel.devices.model.KempInventoryAllocationRequest;
import com.kajeet.sentinel.devices.model.KempInventoryAllocationResponse;
import com.kajeet.sentinel.devices.model.KempUpdateRequest;
import com.kajeet.sentinel.devices.service.KempManager;
import com.kajeet.sentinel.settings.model.Settings;
import com.kajeet.sentinel.user.model.User;
import com.kajeet.sentinel.account.enumeration.AccessObjectEnum;
import com.kajeet.sentinel.account.model.CorpAccessControl;
import com.kajeet.sentinel.devicegroup.HierarchyManager;
import com.kajeet.sentinel.user.manager.UserManager;
import com.kajeet.sentinel.webfilter.WebFilteringManager;
import datadog.trace.api.interceptor.MutableSpan;
import io.opentracing.Span;
import io.opentracing.util.GlobalTracer;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kajeet.sentinel.util.consts.Constants;
import com.kajeet.sentinel.devicegroup.model.CorpSettings;
import com.kajeet.sentinel.devicegroup.model.Organization;
import com.kajeet.sentinel.devices.model.KempEsimInventoryCount;
import com.kajeet.sentinel.activation.dao.ActivationDao;
import com.kajeet.sentinel.activation.service.ActivationService;
import com.kajeet.sentinel.auth.model.SentinelPrincipal;
import com.kajeet.sentinel.config.TextConstants;
import com.kajeet.sentinel.exception.BadRequest;
import com.kajeet.sentinel.exception.ForbiddenException;
import com.kajeet.sentinel.exception.RecordNotFoundException;
import com.kajeet.sentinel.exception.SystemException;
import com.kajeet.sentinel.profile.service.CorpManager;
import com.kajeet.sentinel.service.common.UserAccessService;

@Service
public class ActivationServiceImpl implements ActivationService {

	@Autowired
	private UserAccessService userAccessService;

	@Autowired
	private HierarchyManager hierarchyManager;

	@Autowired
	private WebFilteringManager webFilteringManagerProxy;

	@Autowired
	private CorpManager corpManager;

	@Autowired
	private UserManager userManager;
	
	@Autowired 
	private ActivationDao activationDao;

	@Autowired
	private ProvisioningGatewayService provisioningGatewayService;
	
	@Autowired
	private KempManager kempManager;

	@Value("${activation.carrierIpPool.edu}")
	private String carrierIpPoolEdu;
	@Value("${activation.carrierIpPool.enterprise}")
	private String carrierIpPoolEnterprise;
	@Value("${activation.carrierIpPool.wbu}")
	private String carrierIpPoolWbu;
	@Value("${activation.carrierIpPool.loc}")
	private String carrierIpPoolLoc;
	@Value("${activation.carrierIpPool.plte}")
	private String carrierIpPoolPlte;
	
	@Value("${activation.carrierIpPool.verizon.edu}")
	private String carrierIpPoolVerizonBIEDU;
	@Value("${activation.carrierIpPool.verizon.ent}")
	private String carrierIpPoolVerizonBIENT;
	
	@Value("${activation.max.rows:2000}")
	private int maxActivationRowsCount;

	@Value("${verizon.sku.default}")
	private String verizonCarrierSku;

	@Value("${verizon.priority.sku.default}")
	private String verizonPriorityCarrierSku;

	@Value("${cisco.demo.corps}")
	private String ciscoDemoCorps;

	@Value("${pente.demo.corps}")
	private String penteDemoCorps;

	public static final String INVALID_FILTER_GROUP = "Invalid filterGroup. FilterGroup [%s] is not allowed.";

	private static final String ACTIVATION_USER_NAME = TextConstants.CATALYST_USER_ID + " (%s)";
	
	private static final String CONTROL_CENTER = "ControlCenter";

	private static final Logger log = LoggerFactory.getLogger(ActivationServiceImpl.class);
	private static final ObjectMapper mapper = new ObjectMapper();

	private void createSpan(String carrier, String deviceGroup, String filterGroup, String activationLocation, List<ActivationDetailsDto> activationInfoList) {
		final Span span = GlobalTracer.get().activeSpan();
		Runnable r = () -> {

			if(span instanceof MutableSpan) {
				MutableSpan mutableSpan = ((MutableSpan)span).getLocalRootSpan();
				mutableSpan.setTag("IMEI", activationInfoList.stream().map(ActivationDetailsDto::getImei).collect(Collectors.joining(",")));
				mutableSpan.setTag("ICCID", activationInfoList.stream().map(ActivationDetailsDto::getIccid).collect(Collectors.joining(",")));
				mutableSpan.setTag("CARRIER", carrier);
				mutableSpan.setTag("DEVICE_GROUP", deviceGroup);
				mutableSpan.setTag("FILTER_GROUP", filterGroup);
				mutableSpan.setTag("ACTIVATION_LOCATION", activationLocation);
			} else {
				log.info("Span is not Mutable or null");
			}
		};
		new Thread(r).start();

	}

	@Override
	public SmartSimActivationResponse submitSmartSimActivationRequest(SmartSimActivationRequest smartSimActivationRequest, SentinelPrincipal principal) throws ForbiddenException {
		if(CollectionUtils.isEmpty(smartSimActivationRequest.getActivationLines())) {
			throw new BadRequest("Activation lines list is empty");
		}
		for (SmartSimActivationLine activationLine : smartSimActivationRequest.getActivationLines()) {
			this.validateActivationLineForSmartSim(activationLine, principal);
		}

		smartSimActivationRequest.setDbKey("U1RVQg=="); //STUB
		smartSimActivationRequest.setKeyUserId(principal.getName());
		smartSimActivationRequest.setOcaVersion(2);
		smartSimActivationRequest.setKeyDeviceGroup(smartSimActivationRequest.getActivationLines().get(0).getServiceDetails().getDeviceGroup());

		SmartSimActivationResponse response = provisioningGatewayService.submitSmartSimActivationRequest(smartSimActivationRequest);
		if (response!= null && StringUtils.isNotBlank(response.getTransactionId())) {
			return response;
		} else {
			throw new BadRequest("Error in activation");
		}
	}

	@Override
	public ActivationResponse submitESimActivationRequest(ActivationRequestInfo esimActivationRequest, SentinelPrincipal principal) {
		if(CollectionUtils.isEmpty(esimActivationRequest.getActivationLines())) {
			throw new BadRequest("Activation lines list is empty");
		}

		List<String> carriers = this.getCarriersForESim(principal);
		if(!carriers.contains(esimActivationRequest.getCarrier())) {
			throw new BadRequest("Carrier is not valid");
		}

		String masterCorp = this.getMasterCorp(esimActivationRequest.getDeviceGroup());
		KempEsimInventoryCount inventoryCount = this.getKempEsimInventoryCount(esimActivationRequest.getCarrier(), masterCorp);
		int maxLineCount = inventoryCount.getTotalAvailableESimCount() < inventoryCount.getMaxDefaultCount()?
				inventoryCount.getTotalAvailableESimCount():inventoryCount.getMaxDefaultCount();

		if(maxLineCount == 0) {
			throw new BadRequest("No more eSIMs available at this time. Please contact your administrator for assistance." );
		}

		if(esimActivationRequest.getActivationLines().size() > maxLineCount) {
			throw new BadRequest("Activation lines count exceeds max allowed eSIM count");
		}

		KempInventoryAllocationRequest kempRequest = new KempInventoryAllocationRequest();
		kempRequest.setCarrier(esimActivationRequest.getCarrier());
		kempRequest.setEsimRequestCount(esimActivationRequest.getActivationLines().size());
		kempRequest.setCorpId(masterCorp);
		List<KempInventoryAllocationResponse> kempResponse = kempManager.allocateKempInventory(kempRequest);

		if(kempResponse == null || kempResponse.size() < esimActivationRequest.getActivationLines().size()) {
			throw new BadRequest("Error allocating ICCIDs for activation");
		}

		for(int index = 0; index < esimActivationRequest.getActivationLines().size(); index++) {
			esimActivationRequest.getActivationLines().get(index).setIccid(kempResponse.get(index).getIccid());
		}
		Long transactionId = null;
		boolean kempInventoryStatusUpdatedToAvailable = false;
		try {
			transactionId = this.submitActivationRequest(esimActivationRequest, principal);
		} catch (Exception e) {
			log.error("Error submitting activation", e);
			updateKempInventoryStatus(esimActivationRequest);
			kempInventoryStatusUpdatedToAvailable = true;
		}
		if (transactionId != null && transactionId != 0) {
			return new ActivationResponse(transactionId);
		} else {
			if (!kempInventoryStatusUpdatedToAvailable) { //scenario when actual activation failed due to parsing error or without any exception with transactionId=0
				updateKempInventoryStatus(esimActivationRequest);
			}
			throw new BadRequest("Error in activation");
		}
	}

	private void updateKempInventoryStatus(ActivationRequestInfo esimActivationRequest) {
		esimActivationRequest.getActivationLines().forEach(activationLine -> {
			KempUpdateRequest kempUpdateRequest = new KempUpdateRequest();
			kempUpdateRequest.setIccid(activationLine.getIccid());
			kempUpdateRequest.setSource("Sentinel");
			kempUpdateRequest.setStatus("Available");
			kempManager.updateKempInventory(kempUpdateRequest);
			log.debug("Rolling back ICCID {}", activationLine.getIccid());
		});
	}

	private String getMasterCorp(String corpId) {
		Organization topLevelOrganization = hierarchyManager.getTopLevelOrganization(corpId);
		return topLevelOrganization == null ? corpId : topLevelOrganization.getCorpId();
	}

	private void validateActivationLineForSmartSim(SmartSimActivationLine activationLine, SentinelPrincipal principal) throws ForbiddenException {
		if(activationLine == null || activationLine.getServiceDetails() == null || activationLine.getServiceDetails().getServiceAddress() == null) {
			throw new BadRequest("Invalid activation line format");
		}

		if(StringUtils.isBlank(activationLine.getSimID())) {
			throw new BadRequest("Invalid Sim ID");
		}

		if(activationLine.getSimID().length() < 21 && !Carriers.validateCarriers(activationLine.getServiceDetails().getCarrier())) {
			throw new BadRequest("Invalid Carrier!");
		}
		userAccessService.checkCorpBelongsToUser(activationLine.getServiceDetails().getDeviceGroup(), principal);
		if(!principal.getCorpId().equalsIgnoreCase(activationLine.getServiceDetails().getDeviceGroup())) {
			SentinelPrincipal childrenPrincipal = new SentinelPrincipal(principal.getName(), principal.getCredentials(), principal.getAuthorities(), activationLine.getServiceDetails().getDeviceGroup());
			userAccessService.checkFeatureAccess(childrenPrincipal, AccessObjectEnum.ACTIVATION);
		}
		Carriers carrier = Carriers.convertCarriers(activationLine.getServiceDetails().getCarrier());
		if(carrier != null) {
			activationLine.getServiceDetails().setCarrier(Carriers.convertCarriers(activationLine.getServiceDetails().getCarrier()).name());
		}
		this.validateFilterGroups(principal, activationLine.getServiceDetails().getFilterGroup());
		if(StringUtils.isBlank(activationLine.getServiceDetails().getServiceAddress().getServiceZipCode())) {
			throw new BadRequest("Invalid Zip address");
		}

		activationLine.setVersion(2);
	}

	@Override
	public Long submitActivationRequest(ActivationRequestInfo activationRequestInfo, SentinelPrincipal principal)
									throws RecordNotFoundException, SystemException, ForbiddenException {

		log.info("Inside submitActivationRequest for corpId: {}", principal.getCorpId());

		List<ActivationLine> activationLines = activationRequestInfo.getActivationLines();

		validateNumberOfActivationLines(activationLines);

		if(!Carriers.validateCarriers(activationRequestInfo.getCarrier())) {
			throw new BadRequest("Invalid Carrier!");
		}

		userAccessService.isCorpExistAndAllowed(activationRequestInfo.getDeviceGroup(), false);
		userAccessService.checkCorpBelongsToUser(activationRequestInfo.getDeviceGroup(), principal);
		
		boolean isVerizon = Carriers.Verizon.getDescription().equalsIgnoreCase(activationRequestInfo.getCarrier());
		boolean isTMO = Carriers.TMO.getDescription().equalsIgnoreCase(activationRequestInfo.getCarrier());
		boolean isVerizonPriority = Carriers.Verizon_Priority.getDescription().equalsIgnoreCase(activationRequestInfo.getCarrier());
		boolean isAttFirstNet = Carriers.ATT_FirstNet.getDescription().equalsIgnoreCase(activationRequestInfo.getCarrier());
		boolean isAttFirstNetExtendPrimary = Carriers.ATT_FirstNet_Extended_Primary.getDescription().equalsIgnoreCase(activationRequestInfo.getCarrier());
		boolean isVerizonBI = Carriers.Verizon_BI.getDescription().equalsIgnoreCase(activationRequestInfo.getCarrier());
		boolean isKajeetCiscoNetwork = Carriers.KCN.getDescription().equalsIgnoreCase(activationRequestInfo.getCarrier());
		boolean isKajeetPenteNetwork = Carriers.KPN.getDescription().equalsIgnoreCase(activationRequestInfo.getCarrier());

		if(isVerizonBI) {
			userAccessService.checkFeatureAccess(principal, AccessObjectEnum.VERIZON_BUSINESS_INTERNET_PLAN);
		}

		String businessType = hierarchyManager.getBusinessTypeByCorpId(principal.getCorpId());
		boolean isKPW = Constants.TYPE_KPW.equalsIgnoreCase(businessType);
		if (isVerizon || isTMO || isVerizonPriority  || isAttFirstNetExtendPrimary || isVerizonBI) {
			validateUSZipCode(activationRequestInfo.getServiceZipCode());
		}

		if(isVerizonPriority) {
			activationRequestInfo.validateVerizonPriority(activationRequestInfo);
		}

		if (isAttFirstNet || isAttFirstNetExtendPrimary) {
			validateAttFirstNetFields(activationRequestInfo);
		}

		//NS filterGroups do not apply for Non Bearer carriers
		if(!getNonBearerCarriers(businessType).contains(activationRequestInfo.getCarrier())){
			validateFilterGroups(principal, activationRequestInfo.getFilterGroup());
		} 

		boolean isSuccess = corpManager.addNetsweeperGroupId(activationRequestInfo.getFilterGroup());

		if (!isSuccess) {
			log.error("Unable to Add/Update filter group: {} to Catalyst database.", activationRequestInfo.getFilterGroup());
		}

		CorpSettings corpSettings = corpManager.getCorpSetting(activationRequestInfo.getDeviceGroup());

		ActivationInventoryInfo activationInventoryInfo;
		
		String firstResponder = corpSettings.getFirstResponder();
		if("I".equals(firstResponder)) {
			firstResponder = corpManager.getFirstResponderByHierarchy(activationRequestInfo.getDeviceGroup());
		}

		if("N".equalsIgnoreCase(firstResponder) && !isKPW) {
			log.info("Choosing case 'N' and !kpw '{}'", activationRequestInfo.getCarrier());
			activationInventoryInfo = activationDao.getActivationInventoryInfoByCarrier(activationRequestInfo.getCarrier(), businessType);
		} else if(isKPW) {
			log.info("Choosing case kpw '{}'", activationRequestInfo.getCarrier());
			activationInventoryInfo = activationDao.getActivationInventoryOfPlteNonRuckus(activationRequestInfo.getCarrier());
		} else {
			log.info("Choosing everything else case for carrier '{}'", activationRequestInfo.getCarrier());
			activationInventoryInfo = getActivationInventoryInfoByCarrier3rdParty(activationRequestInfo.getCarrier());
			activationInventoryInfo.setSubTypeList(StringUtils.isNotBlank(activationInventoryInfo.getSubType()) ? Arrays.asList(StringUtils.split(activationInventoryInfo.getSubType(), ",")): null);
		}

		if (activationInventoryInfo == null) {
			log.error("ActivationInventoryInfo not found for carrier: {}", activationRequestInfo.getCarrier());
			throw new BadRequest("carrier not found: " + activationRequestInfo.getCarrier());
		}

		String carrierIpPool;

		if(!StringUtils.isBlank(corpSettings.getCarrierIpPool())) {
			carrierIpPool = corpSettings.getCarrierIpPool();
		} else {
			carrierIpPool = getCarrierIpPool(principal.getCorpId(), isVerizonBI);
		}

		String sku = null;
		if(isVerizon || isVerizonPriority || isVerizonBI) {
			CarrierSku carrierSku = hierarchyManager.getHierarchyCarrierSku(activationRequestInfo.getDeviceGroup());

			if(carrierSku != null && !StringUtils.isBlank(carrierSku.getSku())) {
				sku = carrierSku.getSku();
			} else {
				sku = (isVerizon || isVerizonBI) ? verizonCarrierSku : verizonPriorityCarrierSku;
			}
		}

		if((isAttFirstNet || isAttFirstNetExtendPrimary) && activationRequestInfo.getSubType() != null &&
										!activationInventoryInfo.getSubTypeList().contains(activationRequestInfo.getSubType())) {
			throw new BadRequest("Invalid Subtype!");
		}

		log.info("carrierIpPool:{}", carrierIpPool);
		log.info("carrierSku:{}", sku);
		String leadId = null;
		String customCorpRatePlan = null;
		if (isVerizon || isVerizonBI) {
			CorpAccessControl corpAccessControl = corpManager.getCorpAccessControlByHierarchyLeadId(activationRequestInfo.getDeviceGroup(), AccessObjectEnum.PRM_ACTIVATION);
			if(corpAccessControl != null && Constants.YES.equals(corpAccessControl.getEnabled())) {
				leadId = corpManager.getLeadIdByCorpId(corpAccessControl.getCorpId());
				log.info("PRM_ACTIVATION LeadId:{} for corpId:{}", leadId, corpAccessControl.getCorpId());
			}
			// get custom corp rate plan
			customCorpRatePlan = corpManager.getRatePlanByHierarchy(activationRequestInfo.getDeviceGroup());
			log.info("CustomCorpRatePlan:{} for corpId:{}", customCorpRatePlan, activationRequestInfo.getDeviceGroup());
		}

		Carriers carrierEnum = Carriers.convertCarriers(activationRequestInfo.getCarrier());
		String tmoInstance = null;
		switch (carrierEnum) {
			case ATT:
				customCorpRatePlan = corpManager.getAttRatePlanByHierarchy(activationRequestInfo.getDeviceGroup());
				log.info("AT&T CustomCorpRatePlan:{} for corpId:{}", customCorpRatePlan, activationRequestInfo.getDeviceGroup());
				break;
			case TMO:
			{
				// Use TMO_INSTANCE from master corp CorpSettings to determine which TMO activation method to use
				String masterCorpId = getMasterCorp(activationRequestInfo.getDeviceGroup());
				CorpSettings masterCorpSettings = corpManager.getCorpSetting(masterCorpId);
				 tmoInstance = masterCorpSettings.getTmoInstance();
					if (CONTROL_CENTER.equalsIgnoreCase(tmoInstance)) {
						customCorpRatePlan = null;
					} else {
						customCorpRatePlan = corpManager.getTmoRatePlanByHierarchy(activationRequestInfo.getDeviceGroup());
					}
				
				log.info("TMO CustomCorpRatePlan:{} for corpId:{}", customCorpRatePlan, activationRequestInfo.getDeviceGroup());
				break;
			}
			case Verizon_Priority:
				customCorpRatePlan = corpManager.getVerizonPriorityPlanByHierarchy(activationRequestInfo.getDeviceGroup());
				log.info("Verizon_Priority CustomCorpRatePlan:{} for corpId:{}", customCorpRatePlan, activationRequestInfo.getDeviceGroup());
				break;
			case ATT_FirstNet:
				customCorpRatePlan = corpManager.getATTFirstNetPlanByHierarchy(activationRequestInfo.getDeviceGroup());
				log.info("ATT_FirstNet CustomCorpRatePlan:{} for corpId:{}", customCorpRatePlan, activationRequestInfo.getDeviceGroup());
				break;
			case ATT_FirstNet_Extended_Primary:
				customCorpRatePlan = corpManager.getATTFirstNetExtendedPrimaryPlanByHierarchy(activationRequestInfo.getDeviceGroup());
				log.info("ATT_FirstNet_Extended_Primary CustomCorpRatePlan:{} for corpId:{}", customCorpRatePlan, activationRequestInfo.getDeviceGroup());
				break;
			case US_Cellular:
				customCorpRatePlan = corpManager.getUSCellularPlanByHierarchy(activationRequestInfo.getDeviceGroup());
				log.info("US Cellular CustomCorpRatePlan:{} for corpId:{}", customCorpRatePlan, activationRequestInfo.getDeviceGroup());
				break;
			case KJPLTE:
				customCorpRatePlan = corpManager.getKNEPlanByHierarchy(activationRequestInfo.getDeviceGroup());
				log.info("KPNE CustomCorpRatePlan:{} for corpId:{}", customCorpRatePlan, activationRequestInfo.getDeviceGroup());
				break;
			case KCN:
				customCorpRatePlan = "KCNCISCO";
				log.info("KCN CustomCorpRatePlan:{} for corpId:{}", customCorpRatePlan, activationRequestInfo.getDeviceGroup());
				break;
			case KPN:
				customCorpRatePlan = "KJPENTE" ;
				log.info("KPN CustomCorpRatePlan:{} for corpId:{}", customCorpRatePlan, activationRequestInfo.getDeviceGroup());
				break;	
			case BELLCANADA:
				customCorpRatePlan = corpManager.getUBellCanadaPlanByHierarchy(activationRequestInfo.getDeviceGroup());
				log.info("Bell Canada CustomCorpRatePlan:{} for corpId:{}", customCorpRatePlan, activationRequestInfo.getDeviceGroup());
				break;
			default:
				break;
		}


		ActivationInputDto activationInputDto = new ActivationInputDto();
		List<ActivationDetailsDto> activationInfoList = new ArrayList<>();
		Map<String, String> iccidImeiMap = new HashMap<>();

		for (ActivationLine activationLine : activationLines) {
			ActivationDetailsDto activationInfo = new ActivationDetailsDto();
			if (validateIccidImei(activationLine.getImei(), activationLine.getIccid(), iccidImeiMap)) {
				activationInfo.setIccid(activationLine.getIccid());
				activationInfo.setImei(activationLine.getImei());
				activationInfo.setFilterGroup(activationRequestInfo.getFilterGroup());
				activationInfo.setDeviceGroup(activationRequestInfo.getDeviceGroup());
				activationInfo.setImeiItemId(activationInventoryInfo.getSku());
				if (StringUtils.isNotBlank(activationLine.getNickname())) {
					activationInfo.setNickname(activationLine.getNickname());
				}

				if (isVerizon) {
					if (StringUtils.isNotBlank(customCorpRatePlan)) {
						activationInfo.setWhPlanId(customCorpRatePlan);
					} else {
						activationInfo.setWhPlanId(activationInventoryInfo.getPlanId());
					}
					setVerizonGlobalFields(activationRequestInfo, carrierIpPool, sku, leadId, activationInfo);
				} 
				else if(isVerizonBI) {
					activationInfo.setPlanId(validateVerizonBusinessPlan( activationRequestInfo));
					activationInfo.setWhPlanId(activationInfo.getPlanId());
					activationInfo.setCarrierAccountId(getCarrierAccountId("ALL CORPS", activationRequestInfo.getCarrier()));
					setVerizonGlobalFields(activationRequestInfo, carrierIpPool, sku, leadId, activationInfo);
				}
				else if (isAttFirstNet || isAttFirstNetExtendPrimary) {
					setupCommonFields(activationRequestInfo, sku, activationInfo);

					activationInfo.setAttFirstNet_AgencyEndUserName(activationRequestInfo.getAgencyEndUserName());
					activationInfo.setAttFirstNet_Address(activationRequestInfo.getBillingAddress());
					activationInfo.setAttFirstNet_State(activationRequestInfo.getBillingState());
					activationInfo.setAttFirstNet_City(activationRequestInfo.getBillingCity());
					activationInfo.setAttFirstNet_SubType(activationRequestInfo.getSubType());
					activationInfo.setAttFirstNet_Zipcode(activationRequestInfo.getServiceZipCode());
					activationInfo.setBssRatePlanId(customCorpRatePlan);

					if (isSuccess) {
						activationInfo.setAttFirstNet_netsweeper_group_id(activationRequestInfo.getFilterGroup());
					} else {
						activationInfo.setAttFirstNet_netsweeper_group_id("preset");
					}

					if (ActivationLocationEnum.EAST.getLocation()
													.equalsIgnoreCase(activationRequestInfo.getActivationLocation())) {
						activationInfo.setAttFirstNet_communication_plan_id(activationInventoryInfo.getEastCommunicationPlan());
					} else if (ActivationLocationEnum.WEST.getLocation().equalsIgnoreCase(activationRequestInfo.getActivationLocation())) {
						activationInfo.setAttFirstNet_communication_plan_id(activationInventoryInfo.getWestCommunicationPlan());
					}

				} else {
					if (isTMO) {
						activationInfo.setZipCode(activationRequestInfo.getServiceZipCode());

						if( CONTROL_CENTER.equalsIgnoreCase(tmoInstance)) {
							activationInfo.setCarrierAccountNo(Carriers.TMOCC1.getDescription());
						}
					} else if (isVerizonPriority) {
						setupCommonFields(activationRequestInfo, sku, activationInfo);
						if (ActivationLocationEnum.EAST.getLocation().equalsIgnoreCase(activationRequestInfo
														.getActivationLocation())) {
							activationInfo.setCarrierIpPool(activationInventoryInfo.getEastIpPool());
						} else if (ActivationLocationEnum.WEST.getLocation().equalsIgnoreCase(activationRequestInfo.getActivationLocation())) {
							activationInfo.setCarrierIpPool(activationInventoryInfo.getWestIpPool());
						}
					}


					activationInfo.setPlanId(customCorpRatePlan);
					if (isKPW) {
						switch (carrierEnum) {
						case KJPLTE:
							activationInfo.setNetwork("PLTE");
							activationRequestInfo.setCarrier("KJPLTE");
							break;
						case KCN:
							activationInfo.setNetwork("KCN");
							activationRequestInfo.setCarrier("KCN");
							break;
						case KPN:
							activationInfo.setNetwork("KPN");
							activationRequestInfo.setCarrier("KPN");
						default:
							break;
						}
						
						activationInfo.setBssRatePlanId(customCorpRatePlan);
						activationInfo.setCarrier(activationRequestInfo.getCarrier());
						activationInfo.setPlanId(null);
					}
				}

				activationInfoList.add(activationInfo);
			}
		}
		createSpan(activationRequestInfo.getCarrier(), activationRequestInfo.getDeviceGroup(), activationRequestInfo.getFilterGroup(), activationRequestInfo.getActivationLocation(), activationInfoList);
		activationInputDto.setArray(activationInfoList);
		String activationJsonString;
		try {
			activationJsonString = mapper.writeValueAsString(activationInputDto);
		} catch (JsonProcessingException e) {
			log.error("Error mapping Activation request info to json string");
			throw new SystemException("An error occurred. Please contact support");
		}

		log.info("activationJsonString:{}", activationJsonString);
		User user = userManager.getUser(principal);

		String activationUserName = String.format(ACTIVATION_USER_NAME, user.getEmail());


		switch (carrierEnum) {
			case Verizon:
				return activationDao.submitVerizonActivationRequest(activationJsonString, principal.getCorpId(),
												principal.getName(), activationUserName);
			case Verizon_Priority:
			case Verizon_BI:
				return activationDao.submitVerizonPriorityActivationRequest(activationJsonString, principal.getCorpId(),
												principal.getName(), activationUserName);
			case TMO:
			{
				
				if(CONTROL_CENTER.equalsIgnoreCase(tmoInstance)) {
					log.info("Submitting TMO Control Center activation request corpId {}, UserName {}, activation UserName {}.",principal.getCorpId(),
							principal.getName(), activationUserName);
					// check if corp is demo corp for cisco or pente
					return activationDao.submitTMOControlCenterActivationRequest(activationJsonString, principal.getCorpId(),
												principal.getName(), activationUserName);
				} else { //netcracker
					log.info("Submitting TMO Netcracker activation request corpId {}, UserName {}, activation UserName {}.",principal.getCorpId(),
							principal.getName(), activationUserName);
					return activationDao.submitTMOActivationRequest(activationJsonString, principal.getCorpId(),
												principal.getName(), activationUserName);
				}
			}
			
			case ATT_FirstNet:
				return activationDao.submitAttFirstNetActivationRequest(activationJsonString, principal.getCorpId(),
												principal.getName(), activationUserName);
			case ATT_FirstNet_Extended_Primary:
				return activationDao.submitAttFirstNetExtendedPrimaryActivationRequest(activationJsonString, principal.getCorpId(),
												principal.getName(), activationUserName);
			case US_Cellular:
				return activationDao.submitUSCActivationRequest(activationJsonString, principal.getCorpId(),
												principal.getName(), activationUserName);
			case KJPLTE:
				return activationDao.submitKNEActivationRequest(activationJsonString, principal.getCorpId(),
												principal.getName(), activationUserName);
			case KCN:
				return activationDao.submitKNEActivationRequest(activationJsonString, principal.getCorpId(),
												principal.getName(), activationUserName);
			case KPN:
				return activationDao.submitKNEActivationRequest(activationJsonString, principal.getCorpId(),
												principal.getName(), activationUserName);	
			case BELLCANADA:
				return activationDao.submitBellCanadaActivationRequest(activationJsonString, principal.getCorpId(),
												principal.getName(), activationUserName);
			default:
				return activationDao.submitATTActivationRequest(activationJsonString, principal.getCorpId(),
												principal.getName(), activationUserName);
		}
	}

	private void setVerizonGlobalFields(ActivationRequestInfo activationRequestInfo, String carrierIpPool, String sku,
			String leadId, ActivationDetailsDto activationInfo) {
		activationInfo.setCarrierIpPool(carrierIpPool);
		activationInfo.setZipCode(activationRequestInfo.getServiceZipCode());
		activationInfo.setLeadId(leadId);
		activationInfo.setSkuNumber(sku);
	}

	@Deprecated
	private String validatePlanID(ActivationInventoryInfo activationInventoryInfo,ActivationRequestInfo activationRequestInfo) {
	
	if((activationInventoryInfo.getPlanId() == null || activationRequestInfo.getPlanId() == null) ) {
		log.error("ActivationInventoryInfo: Invalid PlanId: I'ts null");
		throw new BadRequest("ActivationInventoryInfo: Invalid PlanId: I'ts null");
	}
	if(!activationInventoryInfo.getPlanId().contains(activationRequestInfo.getPlanId())) {
		log.error("ActivationInventoryInfo: Invalid PlanId: {}", activationRequestInfo.getPlanId());
		throw new BadRequest("Invalid PlanId: " + activationRequestInfo.getPlanId());
	}
	
	return activationRequestInfo.getPlanId();
		
	}
	
	
	private String validateVerizonBusinessPlan(ActivationRequestInfo activationRequestInfo) {
		
		List<ActivationVerizonBusinessPlan> activationVerizonBusinessPlanList = getBusinessInternetPlans().stream()
				.filter(plan -> Constants.VERTSVBI.equalsIgnoreCase(plan.getCarrier())).collect(Collectors.toList());
		if(( activationRequestInfo.getPlanId() == null) ) {
			log.error("ActivationInventoryInfo: Invalid PlanId: I'ts null");
			throw new BadRequest("ActivationInventoryInfo: Invalid PlanId: I'ts null");
		}
		
		boolean exists = activationVerizonBusinessPlanList.stream()
		            .anyMatch(plan -> plan.getPlanId().equalsIgnoreCase(activationRequestInfo.getPlanId()));  
		           

		    if (!exists) {
		        log.error("ActivationInventoryInfo: Invalid PlanId: {}", activationRequestInfo.getPlanId());
		        throw new BadRequest("Invalid PlanId: " + activationRequestInfo.getPlanId());
		    }

		return activationRequestInfo.getPlanId();
			
		}

	private void setupCommonFields(ActivationRequestInfo activationRequestInfo, String sku,
									ActivationDetailsDto activationInfo) throws SystemException {
		Organization parentCorpDetails = hierarchyManager.getTopLevelOrganization(activationRequestInfo.getDeviceGroup());
		String corpId = parentCorpDetails.getCorpId();
		String carrierAccountID = getCarrierAccountId(corpId, activationRequestInfo.getCarrier());

		if(carrierAccountID == null) {
			throw new SystemException("Could not get carrier account id for topmost corp " + corpId);
		}
		activationInfo.setCarrierAccountId(carrierAccountID);
		activationInfo.setSkuNumber(sku);
		activationInfo.setAgencyEndUserName(activationRequestInfo.getAgencyEndUserName());
		activationInfo.setBillingAddress(activationRequestInfo.getBillingAddress());
		activationInfo.setBillingState(activationRequestInfo.getBillingState());
		activationInfo.setBillingCity(activationRequestInfo.getBillingCity());
		activationInfo.setSubType(activationRequestInfo.getSubType());
		activationInfo.setZipCode(activationRequestInfo.getServiceZipCode());
	}

	public String getCarrierIpPool(String corpId, Boolean isVerizonBI) {

		String businessType = hierarchyManager.getBusinessTypeByCorpId(corpId);
		
		if(isVerizonBI && Constants.TYPE_EDUCATION.equalsIgnoreCase(businessType)) {
			return carrierIpPoolVerizonBIEDU;
		}
		else if(isVerizonBI && !Constants.TYPE_EDUCATION.equalsIgnoreCase(businessType)) {
			return carrierIpPoolVerizonBIENT;
		}

		switch(StringUtils.lowerCase(businessType)) {
			case Constants.TYPE_EDUCATION:
				return carrierIpPoolEdu;
			case Constants.TYPE_ENTERPRISE:
				return carrierIpPoolEnterprise;
			case Constants.TYPE_WBU:
				return carrierIpPoolWbu;
			case Constants.TYPE_LOC:
				return carrierIpPoolLoc;
			default:
				return carrierIpPoolPlte;
		}
	}

	public boolean validateIccidImei(String imei, String iccid, Map<String, String> iccidImeiMap)
									throws SystemException {

		validateImei(imei);
		validateGenericIccid(iccid);

		if (iccidImeiMap.containsKey(iccid)) {
			String imeiStored = iccidImeiMap.get(iccid);
			if (imeiStored.equals(imei)) {
				return false;// same sim, ignoring.
			} else {
				throw new BadRequest("Duplicate ICCID: " + iccid);// not valid
			}
		}

		if (StringUtils.isNotBlank(imei) && iccidImeiMap.containsValue(imei)) {
			throw new SystemException("Duplicate IMEI: " + imei);// currently not supporting that case
		}

		iccidImeiMap.put(iccid, imei);

		return true;
	}

	public void validateNumberOfActivationLines(List<ActivationLine> activationLines) {
		if(activationLines.size() > maxActivationRowsCount) {
			throw new BadRequest("Max limit of rows " + maxActivationRowsCount + " exceeded.");
		}
	}

	public void validateFilterGroups(SentinelPrincipal principal, String filterGroup) {
		
		if (StringUtils.isBlank(filterGroup) || !webFilteringManagerProxy
										.getAllWebFilteringGroupsForUser(principal.getName()).contains(filterGroup)) {
			throw new BadRequest("Invalid Filter group");
		}
	}

	private void validateUSZipCode(String zipCode) {
		String zipCodePattern = "\\d{5}(-\\d{4})?";
		if (StringUtils.isBlank(zipCode)) {
			throw new BadRequest("Zipcode is Mandatory");
		}
		if (!StringUtils.isBlank(zipCode) && !zipCode.matches(zipCodePattern)) {
			throw new BadRequest("Invalid Zipcode");
		}
	}
	
	

	private List<String> getNonBearerCarriers(String businessType){
		List<CarrierBearerPath> carrierBearerPaths = activationDao.getCarrierBearerPaths(businessType);
        return carrierBearerPaths.stream().filter(e-> "Non-Bearer".equalsIgnoreCase(e.getBearerPath())).map(CarrierBearerPath::getCarrierName).collect(Collectors.toList());
	}

	public void validateAttFirstNetFields(ActivationRequestInfo requestInfo) {
		if (StringUtils.isBlank(requestInfo.getAgencyEndUserName()) || requestInfo.getAgencyEndUserName().length() > 50) {
			throw new BadRequest("Invalid Agency End User Name");
		}

		if (StringUtils.isBlank(requestInfo.getBillingAddress()) || requestInfo.getBillingAddress().length() > 50) {
			throw new BadRequest("Invalid End Customer Billing Address");
		}

		if (StringUtils.isBlank(requestInfo.getBillingCity()) || requestInfo.getBillingCity().length() > 50) {
			throw new BadRequest("Invalid City");
		}

		if (StringUtils.isBlank(requestInfo.getBillingState()) || requestInfo.getBillingState().length() > 50) {
			throw new BadRequest("Invalid State");
		}

		if (StringUtils.isBlank(requestInfo.getSubType()) || requestInfo.getSubType().length() > 50) {
			throw new BadRequest("Invalid Sub-Type");
		}
	}

	@Override
	public List<String> getKNECarrierListForActivation() {
		return activationDao.getKNECarrierListForActivation();
	}

	@Override
	public List<String> getCarrierListForActivation(String firstResponder, Boolean isVerizonBI, String deviceGroupId, Boolean isEsimEnabled) {

		String businessType = hierarchyManager.getBusinessTypeByCorpId(deviceGroupId);
		if("N".equalsIgnoreCase(firstResponder)) {
			return activationDao.getCarrierListForActivation(isVerizonBI, businessType, isEsimEnabled);
		}else {
			return activationDao.getCarrierListForActivationFirstResponder(isVerizonBI);
		}
		
	}

	@Override
	public ActivationInventoryInfo getActivationInventoryInfoByCarrier3rdParty(String carrier) {
		return activationDao.getActivationInventoryInfoByCarrier3rdParty(carrier);
	}
	
	@Deprecated
	@Override
	public List<ActivationTransactionDTO> getActivationTransactionHistory(String corpId, String sortDir,
			String userTimezone) {
		sortDir = StringUtils.defaultIfEmpty(sortDir, "DESC");
		return activationDao.getActivationTransactionHistory(corpId, sortDir, userTimezone);
	}
	
	@Override
	public List<ActivationTransactionDTO> getRecentActivationTransactions(String corpId, Integer offset, Integer limit, String sortDir,
			String userTimezone) {
		sortDir = StringUtils.defaultIfEmpty(sortDir, "DESC");
		return activationDao.getRecentActivationTransactions(corpId, offset, limit, sortDir, userTimezone);
	}
	
	@Override
	public List<ActivationTransactionDTO> getAllActivationHistoryDetails(String corpId, String sortDir,
			String userTimezone) {
		sortDir = StringUtils.defaultIfEmpty(sortDir, "DESC");
		return activationDao.getAllActivationHistoryDetails(corpId, sortDir, userTimezone);
	}

	@Override
	public List<ActivationTransactionDTO> getActivationTransactionDetails(String transactionId, String corpId,
			String userTimezone) {
		return activationDao.getActivationTransactionDetails(transactionId, corpId, userTimezone);
	}

	@Override
	public String getCarrierAccountId(String corpId, String carrier) {
		
		return activationDao.getCarrierAccountId(corpId, carrier);
	}

	@Override
	public List<CarrierBearerPath> getCarrierBearerPaths(String corpId) {
		Organization organization = hierarchyManager.getCorpInfo(corpId);
		return activationDao.getCarrierBearerPaths(organization.getCorpBusinessType());
	}
	
	@Override
	public String getUserTimeZoneID(SentinelPrincipal principal) {
        return this.userAccessService.getTimeZone(principal).getID();
    }

	@Override
	public Integer getRecentActivationHistoryTotalCount(String corpId) {
		return activationDao.getRecentActivationHistoryTotalCount(corpId);
	}

	@Override
	public KempEsimInventoryCount getKempEsimInventoryCount(String carrier, String corpId) {
		return kempManager.getKempEsimInventoryCount(carrier, getMasterCorp(corpId));
	}

	@Override
	public List<String> getCarriersForESim(SentinelPrincipal principal) {
		Settings settings = userAccessService.getSettings(principal);

		boolean isVerizonBI = settings.getAccess().contains(AccessObjectEnum.VERIZON_BUSINESS_INTERNET_PLAN.getDescription());
		boolean hasEsimActivation = settings.getAccess().contains(AccessObjectEnum.ESIM_ACTIVATION.getDescription());

		String corpId = principal.getCorpId();
		CorpSettings corpSettings = corpManager.getCorpSetting(corpId);
		String businessType = hierarchyManager.getBusinessTypeByCorpId(corpId);
		boolean isKPW = Constants.TYPE_KPW.equalsIgnoreCase(businessType);

		String firstResponder = resolveFirstResponder(corpSettings.getFirstResponder(), corpId);
		List<String> carrierList;

		if (isKPW) {
			carrierList = new ArrayList<>(this.getKNECarrierListForActivation());
			appendDemoCarriersIfApplicable(carrierList, corpId);
		} else {
			carrierList = this.getCarrierListForActivation(firstResponder, isVerizonBI, corpId, hasEsimActivation);
		}

		reorderCarrier(carrierList);
		return carrierList;
	}

	@Override
	public void reorderCarrier(List<String> carrierList) {
		String verizonBI = "Verizon BI";
		if(carrierList.contains(verizonBI)) {
            carrierList.remove(verizonBI);
			carrierList.add(0, verizonBI);

		}
	}

	@Override
	public String resolveFirstResponder(String firstResponder, String corpId) {
		return "I".equals(firstResponder)
				? corpManager.getFirstResponderByHierarchy(corpId)
				: firstResponder;
	}

	@Override
	public void appendDemoCarriersIfApplicable(List<String> carrierList, String corpId) {
		List<String> ciscoDemoList = Arrays.asList(StringUtils.split(ciscoDemoCorps, ','));
		List<String> penteDemoList = Arrays.asList(StringUtils.split(penteDemoCorps, ','));

		if (ciscoDemoList.contains(corpId)) {
			carrierList.add("Kajeet Cisco Network");
		}
		if (penteDemoList.contains(corpId)) {
			carrierList.add("Kajeet Private Wireless(KPN)");
		}
	}

	@Override
	public List<ActivationVerizonBusinessPlan> getBusinessInternetPlans() {
		return activationDao.getBusinessInternetPlans();
	}

}
