package org.egov.collection.service;

import static java.util.Objects.isNull;

import java.util.*;

import org.apache.commons.lang3.StringUtils;
import org.egov.collection.config.ApplicationProperties;
import org.egov.collection.model.Payment;
import org.egov.collection.model.PaymentRequest;
import org.egov.collection.model.PaymentSearchCriteria;
import org.egov.collection.producer.CollectionProducer;
import org.egov.collection.repository.PaymentRepository;
import org.egov.collection.util.PaymentEnricher;
import org.egov.collection.util.PaymentValidator;
import org.egov.collection.web.contract.Bill;
import org.egov.collection.web.contract.PropertyDetail;
import org.egov.collection.web.contract.RoadCuttingInfo;
import org.egov.collection.web.contract.UsageCategoryInfo;
import org.egov.common.contract.request.RequestInfo;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PaymentService {

	private ApportionerService apportionerService;

	private PaymentEnricher paymentEnricher;

	private ApplicationProperties applicationProperties;

	private UserService userService;

	private PaymentValidator paymentValidator;

	private PaymentRepository paymentRepository;

	private CollectionProducer producer;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	public PaymentService(ApportionerService apportionerService, PaymentEnricher paymentEnricher,
			ApplicationProperties applicationProperties, UserService userService, PaymentValidator paymentValidator,
			PaymentRepository paymentRepository, CollectionProducer producer) {
		this.apportionerService = apportionerService;
		this.paymentEnricher = paymentEnricher;
		this.applicationProperties = applicationProperties;
		this.userService = userService;
		this.paymentValidator = paymentValidator;
		this.paymentRepository = paymentRepository;
		this.producer = producer;
	}

	/**
	 * Fetch all receipts matching the given criteria, enrich receipts with
	 * instruments
	 *
	 * @param requestInfo           Request info of the search
	 * @param paymentSearchCriteria Criteria against which search has to be
	 *                              performed
	 * @return List of matching receipts
	 */
	public List<Payment> getPayments(RequestInfo requestInfo, PaymentSearchCriteria paymentSearchCriteria,
			String moduleName) {

		paymentValidator.validateAndUpdateSearchRequestFromConfig(paymentSearchCriteria, requestInfo, moduleName);
		if (applicationProperties.isPaymentsSearchPaginationEnabled()) {
			paymentSearchCriteria
					.setOffset(isNull(paymentSearchCriteria.getOffset()) ? 0 : paymentSearchCriteria.getOffset());
			paymentSearchCriteria.setLimit(
					isNull(paymentSearchCriteria.getLimit()) ? applicationProperties.getReceiptsSearchDefaultLimit()
							: paymentSearchCriteria.getLimit());
		} else {
			paymentSearchCriteria.setOffset(0);
			paymentSearchCriteria.setLimit(applicationProperties.getReceiptsSearchDefaultLimit());
		}
		/*
		 * if (requestInfo.getUserInfo().getType().equals("CITIZEN")) { List<String>
		 * payerIds = new ArrayList<>();
		 * payerIds.add(requestInfo.getUserInfo().getUuid());
		 * paymentSearchCriteria.setPayerIds(payerIds); }
		 */

		List<Payment> payments = paymentRepository.fetchPayments(paymentSearchCriteria);
		
		
		if (payments != null && !payments.isEmpty()) {
			Collections.sort(payments.get(0).getPaymentDetails().get(0).getBill().getBillDetails(),
					(b1, b2) -> b2.getFromPeriod().compareTo(b1.getFromPeriod()));
		}
		
		
		if ((null != paymentSearchCriteria.getBusinessService() ||null != paymentSearchCriteria.getReceiptNumbers())  && payments != null && !payments.isEmpty())
		{
			String businessservice =null;
			
			
			if (null != paymentSearchCriteria.getReceiptNumbers()) 
				
			{
				String receiptnumber = null;
				Iterator<String> iterate = paymentSearchCriteria.getReceiptNumbers().iterator();
				while (iterate.hasNext()) {
					receiptnumber = iterate.next();
				}
				String receipts[] = receiptnumber.split("/");

				String businessservices[] = receipts[0].split("_");
				businessservice=businessservices[0];
				if (businessservices[0].equals("WS") || businessservices[0].equals("SW")) 
				{
					List<String> consumercode = paymentRepository.fetchConsumerCodeByReceiptNumber(receiptnumber);

					// Create a Set to hold the application number
					Set<String> applicationNumbers = new HashSet<>();
					/* w/s receipt bug fixing for ledger id and other things-- Abhishek (ticket number-- Defect #24)  */
					if (consumercode.get(0).contains("WS_AP") || consumercode.get(0).contains("SW_AP"))
					{
						
					    applicationNumbers.add(consumercode.get(0)); // Add the string to the Set
					    log.info("final consumercode "+applicationNumbers);
					    paymentSearchCriteria.setApplicationNo(applicationNumbers); // Pass the Set to the method
					}
					else
					{
					    applicationNumbers.add(consumercode.get(0)); // Add the string to the Set

						paymentSearchCriteria.setConsumerCodes(applicationNumbers);
					}
				}
				
			}
				
			
			else
			businessservice = paymentSearchCriteria.getBusinessService();
			
			if (businessservice.equals("WS")|| businessservice.equals("SW") || businessservice.equals("WS.ONE_TIME_FEE") || businessservice.equals("SW.ONE_TIME_FEE")) 
			{
				
				if ((businessservice.equals("WS.ONE_TIME_FEE")||businessservice.equals("SW.ONE_TIME_FEE")) && paymentSearchCriteria.getConsumerCodes()!=null  && paymentSearchCriteria.getConsumerCodes()!=null )
				{
					paymentSearchCriteria.setConsumerCodes(null);
				}
				
				List<UsageCategoryInfo> usageCategory = paymentRepository.fetchUsageCategoryByApplicationnos(
					    paymentSearchCriteria.getConsumerCodes(), 
					    paymentSearchCriteria.getApplicationNo() , 
					    businessservice
					);
				
			
				List<String> address = paymentRepository.fetchAddressByApplicationnos(
					    paymentSearchCriteria.getConsumerCodes(), 
					    paymentSearchCriteria.getApplicationNo(), 
					    businessservice
					);

				List<RoadCuttingInfo> fetchRoadCuttingInfo = paymentRepository.fetchRoadCuttingInfo(
					    paymentSearchCriteria.getConsumerCodes(), 
					    paymentSearchCriteria.getApplicationNo(), 
					    businessservice
					);

				
				List<String> additional = paymentRepository.adddetails(
					    paymentSearchCriteria.getConsumerCodes(), 
					    paymentSearchCriteria.getApplicationNo(), 
					    businessservice
					);
				List<String> meterdetails  = paymentRepository.meterInstallmentDate(
					    paymentSearchCriteria.getConsumerCodes(), 
					    paymentSearchCriteria.getApplicationNo(), 
					    businessservice
					);

					List<String> meterid = paymentRepository.meterId(
					    paymentSearchCriteria.getConsumerCodes(), 
					    paymentSearchCriteria.getApplicationNo(), 
					    businessservice
					);
				String meterMake = null;
				String avarageMeterReading = null;
				String initialMeterReading = null;
				if (additional != null && !additional.isEmpty()) {
					ObjectMapper objectMapper = new ObjectMapper();

					for (String jsonString : additional) {
						try {
							Map<String, String> map = objectMapper.readValue(jsonString,
									new TypeReference<Map<String, String>>() {
									});
							meterMake = (String) map.get("meterMake");
							payments.get(0).setMeterMake(meterMake);
							avarageMeterReading = (String) map.get("avarageMeterReading");
							payments.get(0).setAvarageMeterReading(avarageMeterReading);
							initialMeterReading = (String) map.get("initialMeterReading");
							payments.get(0).setInitialMeterReading(initialMeterReading);
							
							String ledgerId = (String) map.get("ledgerId");
							payments.get(0).setLedgerId(ledgerId);
							
							String groups = (String) map.get("groups");
							payments.get(0).setGroupId(groups);

							String conncat = (String) map.get("connectionCategory");
							payments.get(0).setConnectionCategory(conncat);

						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}
				// setPropertyData(receiptnumber,payments,businessservice);
				if (usageCategory != null && !usageCategory.isEmpty())
				{
					payments.get(0).setUsageCategory(usageCategory.get(0).getUsageCategory());
					payments.get(0).setLandarea(usageCategory.get(0).getLandArea());
					payments.get(0).setPropertyId(usageCategory.get(0).getPropertyId());
					
				}
				if (fetchRoadCuttingInfo!=null && !fetchRoadCuttingInfo.isEmpty() )
					
				{
					payments.get(0).setRoadtype(fetchRoadCuttingInfo.get(0).getRoadType());
					payments.get(0).setRoadlength(fetchRoadCuttingInfo.get(0).getRoadCuttingArea());

				}
					
				
				if (address != null && !address.isEmpty())
					payments.get(0).setAddress(address.get(0));

				if (meterdetails != null && !meterdetails.isEmpty())
					payments.get(0).setMeterinstallationDate(meterdetails.get(0));
				if (meterid != null && !meterid.isEmpty())
					payments.get(0).setMeterId(meterid.get(0));
			}
			
			return payments;
		
			}
			
		else {
			return payments;
		}
	}

	public Long getpaymentcountForBusiness(String tenantId, String businessService) {

		return paymentRepository.getPaymentsCount(tenantId, businessService);
	}

	@Transactional
	public Payment updatePaymentForFilestore(Payment payment) {

		paymentRepository.updateFileStoreIdToNull(payment);
		return payment;
	}

	/**
	 * Handles creation of a receipt, including multi-service, involves the
	 * following steps, - Enrich receipt from billing service using bill id -
	 * Validate the receipt object - Enrich receipt with receipt numbers, coll type
	 * etc - Apportion paid amount - Persist the receipt object - Create instrument
	 *
	 * @param paymentRequest payment request for which receipt has to be created
	 * @return Created receipt
	 */
	@Transactional
	public Payment createPayment(PaymentRequest paymentRequest) {

		paymentEnricher.enrichPaymentPreValidate(paymentRequest, false);
		paymentValidator.validatePaymentForCreate(paymentRequest);
		paymentEnricher.enrichPaymentPostValidate(paymentRequest);

		Payment payment = paymentRequest.getPayment();
		Map<String, Bill> billIdToApportionedBill = apportionerService.apportionBill(paymentRequest);
		paymentEnricher.enrichAdvanceTaxHead(new LinkedList<>(billIdToApportionedBill.values()));
		setApportionedBillsToPayment(billIdToApportionedBill, payment);

		String payerId = createUser(paymentRequest);
		if (!StringUtils.isEmpty(payerId))
			payment.setPayerId(payerId);
		paymentRepository.savePayment(payment);

		producer.producer(applicationProperties.getCreatePaymentTopicName(), paymentRequest);

		return payment;
	}

	private void setPropertyData(String receiptnumber, List<Payment> payments, String businessservice) {
		List<String> consumercode = paymentRepository.fetchConsumerCodeByReceiptNumber(receiptnumber);
		String connectionno = consumercode.get(0);
		PropertyDetail status = paymentRepository.fetchPropertyDetail(connectionno, businessservice);

		if (status != null) {
			HashMap<String, String> additionalDetail = new HashMap<>();

			if (!StringUtils.isEmpty(status.getOldConnectionNo())) {
				additionalDetail.put("oldConnectionno", status.getOldConnectionNo());
			}

			if (!StringUtils.isEmpty(status.getPlotSize())) {
				additionalDetail.put("landArea", status.getPlotSize());
			}

			if (!StringUtils.isEmpty(status.getUsageCategory())) {
				additionalDetail.put("usageCategory", status.getUsageCategory());
				payments.get(0).setUsageCategory(status.getUsageCategory());
			}

			if (!StringUtils.isEmpty(status.getPropertyId())) {
				payments.get(0).setPropertyId(status.getPropertyId());
			}

			if (!StringUtils.isEmpty(status.getAddress())) {
				payments.get(0).setAddress(status.getAddress());
			}

			if (!StringUtils.isEmpty(status.getMeterDetails())) {
				payments.get(0).setMeterinstallationDate(status.getMeterDetails());
			}

			if (!StringUtils.isEmpty(status.getMeterId())) {
				payments.get(0).setMeterId(status.getMeterId());
			}

			if (!StringUtils.isEmpty(status.getAverageMeterReading())) {
				payments.get(0).setAvarageMeterReading(status.getAverageMeterReading());
			}

			if (!StringUtils.isEmpty(status.getInitialMeterReading())) {
				payments.get(0).setInitialMeterReading(status.getInitialMeterReading());
			}

			if (!StringUtils.isEmpty(status.getMeterMake())) {
				payments.get(0).setMeterMake(status.getMeterMake());
			}

			payments.get(0).setPropertyDetail(additionalDetail);
		}
	}

	/**
	 * If Citizen is paying, the id of the logged in user becomes payer id. If
	 * Employee is paying, 1. the id of the owner of the bill will be attached as
	 * payer id. 2. In case the bill is for a misc payment, payer id is empty.
	 * 
	 * @param paymentRequest
	 * @return
	 */
	public String createUser(PaymentRequest paymentRequest) {

		String id = null;
		if (paymentRequest.getRequestInfo().getUserInfo().getType().equals("CITIZEN")) {
			id = paymentRequest.getRequestInfo().getUserInfo().getUuid();
		} else {
			if (applicationProperties.getIsUserCreateEnabled()) {
				Payment payment = paymentRequest.getPayment();
				Map<String, String> res = userService.getUser(paymentRequest.getRequestInfo(),
						payment.getMobileNumber(), payment.getTenantId());
				if (CollectionUtils.isEmpty(res.keySet())) {
					id = userService.createUser(paymentRequest);
				} else {
					id = res.get("id");
				}
			}
		}
		return id;
	}

	private void setApportionedBillsToPayment(Map<String, Bill> billIdToApportionedBill, Payment payment) {
		Map<String, String> errorMap = new HashMap<>();
		payment.getPaymentDetails().forEach(paymentDetail -> {
			if (billIdToApportionedBill.get(paymentDetail.getBillId()) != null)
				paymentDetail.setBill(billIdToApportionedBill.get(paymentDetail.getBillId()));
			else
				errorMap.put("APPORTIONING_ERROR",
						"The bill id: " + paymentDetail.getBillId() + " not present in apportion response");
		});
		if (!errorMap.isEmpty())
			throw new CustomException(errorMap);
	}

	@Transactional
	public List<Payment> updatePayment(PaymentRequest paymentRequest) {

		List<Payment> validatedPayments = paymentValidator.validateAndEnrichPaymentsForUpdate(
				Collections.singletonList(paymentRequest.getPayment()), paymentRequest.getRequestInfo());

		paymentRepository.updatePayment(validatedPayments);
		producer.producer(applicationProperties.getUpdatePaymentTopicName(),
				new PaymentRequest(paymentRequest.getRequestInfo(), paymentRequest.getPayment()));

		return validatedPayments;
	}

	/**
	 * Used by payment gateway to validate provisional receipts of the payment
	 * 
	 * @param paymentRequest
	 * @return
	 */
	@Transactional
	public Payment vaidateProvisonalPayment(PaymentRequest paymentRequest) {
		paymentEnricher.enrichPaymentPreValidate(paymentRequest, false);
		paymentValidator.validatePaymentForCreate(paymentRequest);

		return paymentRequest.getPayment();
	}

//    @Transactional
//    public Payment updatePaymentForFilestore(Payment payment) {
//
//       paymentRepository.updateFileStoreIdToNull(payment);
//        return payment;
//    }

	public List<Payment> plainSearch(PaymentSearchCriteria paymentSearchCriteria) {
		PaymentSearchCriteria searchCriteria = new PaymentSearchCriteria();

		log.info("plainSearch Service BusinessServices" + paymentSearchCriteria.getBusinessServices()
				+ "plainSearch Service Date " + paymentSearchCriteria.getFromDate() + " to "
				+ paymentSearchCriteria.getToDate() + "Teant IT " + paymentSearchCriteria.getTenantId()
				+ " \"plainSearch Service BusinessServices\"+paymentSearchCriteria.getBusinessService():"
				+ paymentSearchCriteria.getBusinessService());

		if (applicationProperties.isPaymentsSearchPaginationEnabled()) {
			searchCriteria.setOffset(isNull(paymentSearchCriteria.getOffset()) ? 0 : paymentSearchCriteria.getOffset());
			searchCriteria.setLimit(
					isNull(paymentSearchCriteria.getLimit()) ? applicationProperties.getReceiptsSearchDefaultLimit()
							: paymentSearchCriteria.getLimit());
		} else {
			searchCriteria.setOffset(0);
			searchCriteria.setLimit(applicationProperties.getReceiptsSearchDefaultLimit());
		}

		if (paymentSearchCriteria.getTenantId() != null) {
			searchCriteria.setTenantId(paymentSearchCriteria.getTenantId());
		}

		if (paymentSearchCriteria.getBusinessServices() != null) {
			log.info("in PaymentService.java paymentSearchCriteria.getBusinessServices(): "
					+ paymentSearchCriteria.getBusinessServices());
			searchCriteria.setBusinessServices(paymentSearchCriteria.getBusinessServices());
		}

		if (paymentSearchCriteria.getBusinessService() != null) {
			log.info("in PaymentService.java paymentSearchCriteria.getBusinessService(): "
					+ paymentSearchCriteria.getBusinessService());
			searchCriteria.setBusinessService(paymentSearchCriteria.getBusinessService());
		}

		if ((paymentSearchCriteria.getFromDate() != null && paymentSearchCriteria.getFromDate() > 0)
				&& (paymentSearchCriteria.getToDate() != null && paymentSearchCriteria.getToDate() > 0)) {
			searchCriteria.setToDate(paymentSearchCriteria.getToDate());
			searchCriteria.setFromDate(paymentSearchCriteria.getFromDate());

		}

		List<String> ids = paymentRepository.fetchPaymentIds(searchCriteria);
		if (ids.isEmpty())
			return Collections.emptyList();

		PaymentSearchCriteria criteria = PaymentSearchCriteria.builder().ids(new HashSet<String>(ids)).build();
		return paymentRepository.fetchPaymentsForPlainSearch(criteria);
	}

	@Transactional
	public Payment createPaymentForWSMigration(PaymentRequest paymentRequest) {

		paymentEnricher.enrichPaymentPreValidate(paymentRequest, true);
		paymentValidator.validatePaymentForCreateWSMigration(paymentRequest);
		paymentEnricher.enrichPaymentPostValidate(paymentRequest);

		Payment payment = paymentRequest.getPayment();
		Map<String, Bill> billIdToApportionedBill = apportionerService.apportionBill(paymentRequest);
		paymentEnricher.enrichAdvanceTaxHead(new LinkedList<>(billIdToApportionedBill.values()));
		setApportionedBillsToPayment(billIdToApportionedBill, payment);

		String payerId = createUser(paymentRequest);
		if (!StringUtils.isEmpty(payerId))
			payment.setPayerId(payerId);
		paymentRepository.savePayment(payment);

		// producer.producer(applicationProperties.getCreatePaymentTopicName(),
		// paymentRequest);

		return payment;
	}

}
