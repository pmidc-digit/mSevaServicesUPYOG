package org.egov.egovsurveyservices.service;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.egovsurveyservices.config.ApplicationProperties;
import org.egov.egovsurveyservices.producer.Producer;
import org.egov.egovsurveyservices.repository.QuestionRepository;
import org.egov.egovsurveyservices.utils.ResponseInfoFactory;
import org.egov.egovsurveyservices.validators.QuestionValidator;
import org.egov.egovsurveyservices.web.models.*;
import org.egov.egovsurveyservices.web.models.enums.Status;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.*;

@Slf4j
@Service
public class QuestionService {

    @Autowired
    QuestionValidator questionValidator;

    @Autowired
    QuestionRepository questionRepository;

    @Autowired
    private Producer producer;

    @Autowired
    private ApplicationProperties applicationProperties;

    public QuestionResponse createQuestion(QuestionRequest questionRequest) {
        RequestInfo requestInfo = questionRequest.getRequestInfo();
        if (questionRequest.getQuestions().size() > applicationProperties.getMaxCreateLimit()) {
            throw new IllegalArgumentException("Maximum " + applicationProperties.getMaxCreateLimit() + " questions allowed per request.");
        }
        questionRequest.getQuestions().forEach(question -> enrichCreateRequest(question,requestInfo));
        producer.push(applicationProperties.getSaveQuestionTopic(), questionRequest);
        return generateResponse(questionRequest);
    }

    public QuestionResponse updateQuestion(QuestionRequest questionRequest) {
        questionValidator.validateForUpdate(questionRequest);
        Question question = questionRequest.getQuestions().get(0);
        List<Question> existingQuesList = questionRepository.getQuestionById(question.getUuid());
        if (CollectionUtils.isEmpty(existingQuesList)) {
            throw new CustomException("EG_SS_QUESTION_NOT_FOUND", "question not found");
        }
        Question existingQuesFromDb = existingQuesList.get(0);
        Gson gson = new Gson();
        Question deepCopy = gson.fromJson(gson.toJson(existingQuesFromDb), Question.class);

        if (question.getStatus() != null) {
            existingQuesFromDb.setStatus(question.getStatus());
        }

        if (existingQuesFromDb.equals(deepCopy)) {
            throw new CustomException("EG_SS_NOTHING_TO_UPDATE", "no content returned, nothing to update");
        }

        // Update audit details
        String uuid = questionRequest.getRequestInfo().getUserInfo().getUuid();
        existingQuesFromDb.getAuditDetails().setLastModifiedBy(uuid);
        existingQuesFromDb.getAuditDetails().setLastModifiedTime(System.currentTimeMillis());

        // Save the updated question
        questionRequest.setQuestions(Collections.singletonList(existingQuesFromDb));
        producer.push(applicationProperties.getUpdateQuestionTopic(), questionRequest);
        return generateResponse(questionRequest);

    }

    private void enrichCreateRequest(Question question, RequestInfo requestInfo) {
        AuditDetails auditDetails = AuditDetails.builder()
                .createdBy(requestInfo.getUserInfo().getUuid())
                .lastModifiedBy(requestInfo.getUserInfo().getUuid())
                .createdTime(new Date().getTime())
                .lastModifiedTime(new Date().getTime())
                .build();
        question.setUuid(UUID.randomUUID().toString());
        question.setAuditDetails(auditDetails);
        question.setStatus(Optional.ofNullable(question.getStatus()).orElse(Status.ACTIVE));
        List<String> options = question.getOptions();
        question.setOptions((options == null || options.isEmpty()) ? Collections.singletonList("NA") : options);
    }

    private QuestionResponse generateResponse(QuestionRequest questionRequest) {
        return QuestionResponse.builder().responseInfo(ResponseInfoFactory.createResponseInfoFromRequestInfo(questionRequest.getRequestInfo(), true)).questions(questionRequest.getQuestions()).build();
    }

    public QuestionResponse searchQuestion(QuestionSearchCriteria criteria) {

        if (StringUtils.isBlank(criteria.getUuid()) && (StringUtils.isBlank(criteria.getTenantId())||(StringUtils.isBlank(criteria.getCategoryId())))) {
            throw new CustomException("EG_SS_TENANT_ID_REQUIRED_QUESTION_SEARCH", "either a (uuid) or a (tenant id and category id) is required.");
        }
        if (criteria.getPageNumber() < 1) {
            throw new IllegalArgumentException("Page number must be greater than or equal to 1");
        }

        QuestionRequest questionRequest = new QuestionRequest();
        questionRequest.setQuestions(questionRepository.fetchQuestions(criteria));
        return generateResponse(questionRequest);
    }

}
